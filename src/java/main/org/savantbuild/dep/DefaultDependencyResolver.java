/*
 * Copyright (c) 2008, Inversoft, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.dep;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.DependencyLinkValue;
import org.savantbuild.dep.graph.GraphBuilder;
import org.savantbuild.dep.graph.GraphLink;
import org.savantbuild.dep.graph.GraphNode;
import org.savantbuild.dep.graph.ResolvedArtifactGraph;
import org.savantbuild.dep.util.ErrorList;
import org.savantbuild.dep.version.CompatibilityVerifier;
import org.savantbuild.dep.workflow.FetchWorkflow;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.workflow.Workflow;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.savantbuild.dep.util.CollectionTools.set;

/**
 * This is used to iterate over dependency lists (artifacts and artifact groups) and call out to interested listeners.
 *
 * @author Brian Pontarelli
 */
public class DefaultDependencyResolver implements DependencyResolver {
  private final static Logger logger = Logger.getLogger(DefaultDependencyResolver.class.getName());

  /**
   * {@inheritDoc}
   */
  @Override
  public Dependencies dependencies(Artifact artifact, Workflow workflow) {
    Dependencies deps = new Dependencies();
    deps.groups.put("run", new DependencyGroup("run"));
    deps.groups.get("run").dependencies.add(artifact);
    resolve(deps, workflow, set("run"), true);
    return deps.graph.getDependencies(artifact);
  }

  @Override
  public ResolvedArtifactGraph resolve(Dependencies dependencies, Workflow workflow,
                                       Set<String> artifactGroupTypes,
                                       boolean transitive, DependencyListener... listeners) {
    // If there are no types, just assume they want everything
    if (artifactGroupTypes == null || artifactGroupTypes.size() == 0) {
      artifactGroupTypes = new HashSet<>();
    }

    logger.fine("Running dependency mediator");
    ResolutionContext resolutionContext = new ResolutionContext();
    DependencyGraph graph = dependencies.graph;
    if (graph == null) {
      GraphBuilder builder = new GraphBuilder(dependencies, workflow, transitive);
      graph = builder.buildGraph(resolutionContext);
    }

    CompatibilityVerifier verifier = new CompatibilityVerifier();
    ErrorList errors = verifier.verifyCompatibility(dependencies, graph, artifactGroupTypes);
    if (errors != null && !errors.isEmpty()) {
      throw new DependencyException("Artifact compatibility error", errors);
    }

    // Perform depth first traversal and download
    logger.fine("Fetching artifacts");
    errors = new ErrorList();
    Map<Artifact, Path> results = new HashMap<>();
    Set<GraphNode<ArtifactID, DependencyLinkValue>> nodes = graph.nodes();
    for (GraphNode<ArtifactID, DependencyLinkValue> node : nodes) {
      ArtifactID id = node.value;

      // Skip the root node because it is the project and therefore not resolvable
      if (id.equals(graph.root.id)) {
        continue;
      }

      // Determine the version of the artifact
      DependencyLinkValue bestLink = null;
      List<GraphLink<ArtifactID, DependencyLinkValue>> links = node.getInboundLinksList();
      for (GraphLink<ArtifactID, DependencyLinkValue> link : links) {
        // If this version is in a group that we shouldn't use, skip it
        if (artifactGroupTypes.size() > 0 && !artifactGroupTypes.contains(link.value.type)) {
          continue;
        }

        if (bestLink == null) {
          bestLink = link.value;
        } else if (!bestLink.dependencyVersion.equals(link.value.dependencyVersion)) {
          throw new DependencyException("Savant was unable to determine the single version of the artifact [" + id +
              "] and therefore could not resolve that artifact. This is generally an internal Savant bug and " +
              "should be reported and fixed.");
        }
      }

      // If we found a suitable version, resolve it
      if (bestLink != null) {
        Artifact artifact = bestLink.toArtifact(id);
        Path file = resolveSingleArtifact(workflow, artifact, errors, resolutionContext, listeners);
        if (file != null) {
          results.put(artifact, file);
        }
      }
    }

    if (!errors.isEmpty()) {
      throw new DependencyException("Savant encountered an error(s) while attempting to resolve the dependencies.", errors);
    }

    // Handle all the negatives
    Map<Artifact, Set<String>> missingItems = resolutionContext.getMissingItems();
    PublishWorkflow pw = workflow.getPublishWorkflow();
    for (Artifact artifact : missingItems.keySet()) {
      Set<String> items = missingItems.get(artifact);
      for (String item : items) {
        if (item.equals("AMD_FILE")) {
          pw.publishNegativeMetaData(artifact);
        } else {
          pw.publishNegative(artifact, item);
        }
      }
    }

    return results;
  }

  @Override
  public ResolvedArtifactGraph resolve(Artifact artifact, Workflow workflow, Set<String> artifactGroupTypes,
                                     boolean transitive, DependencyListener... listeners) {
    Dependencies deps = new Dependencies();
    deps.groups.put("run", new DependencyGroup("run"));
    deps.groups.get("run").dependencies.add(artifact);
    return resolve(deps, workflow, artifactGroupTypes, transitive, listeners);
  }

  /**
   * Handles the fetching of a single artifact.
   *
   * @param handler           The workflow handler.
   * @param artifact          The artifact to fetch and store
   * @param errors            The ErrorList to add any errors to.
   * @param resolutionContext The resolution context.
   * @param listeners         The listeners.
   * @return The file for the artifact in the local cache (if found and cached).
   */
  protected Path resolveSingleArtifact(Workflow handler, Artifact artifact, ErrorList errors,
                                       ResolutionContext resolutionContext, DependencyListener... listeners) {
    FetchWorkflow fw = handler.getFetchWorkflow();
    PublishWorkflow pw = handler.getPublishWorkflow();

    // Let the fetchWorkflow handle the resolution from both the local cache and
    // the process objects.
    Path file = fw.fetchItem(artifact, artifact.getArtifactFile(), pw, resolutionContext);
    if (file == null) {
      errors.addError("Unable to locate dependency [" + artifact.toString() + "]");
      return null;
    }

    // Fetch the source JAR for the artifact, if it exists. If it doesn't that's okay.
    fw.fetchItem(artifact, artifact.getArtifactSourceFile(), pw, resolutionContext);

    logger.fine("Done resolving artifact [" + artifact + "]");

    for (DependencyListener listener : listeners) {
      listener.artifactFound(file, artifact);
    }

    return file;
  }
}
