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
import org.savantbuild.dep.util.ErrorList;
import org.savantbuild.dep.workflow.Workflow;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is used to iterate over dependency lists (artifacts and artifact groups) and deletes the artifacts that have
 * been published locally.
 *
 * @author Brian Pontarelli
 */
public class DefaultDependencyDeleter implements DependencyDeleter {
  private final static Logger logger = Logger.getLogger(DefaultDependencyDeleter.class.getName());

  @Override
  public void delete(Artifact artifact, Workflow workflow, boolean transitive, DependencyListener... listeners) {
    Dependencies deps = new Dependencies();
    deps.groups.put("run", new DependencyGroup("run"));
    deps.groups.get("run").dependencies.add(artifact);
    delete(deps, workflow, transitive, listeners);
  }

  @Override
  public void delete(Dependencies dependencies, Workflow workflow, boolean transitive,
                     DependencyListener... listeners) {
    logger.fine("Running dependency deleter");
    DependencyGraph graph = dependencies.graph;
    if (graph == null) {
      GraphBuilder builder = new GraphBuilder(dependencies, workflow, transitive);
      graph = builder.buildGraph(new ResolutionContext());
    }

    // The graph contains everything right now. We should be able to dump the graph and remove
    // everything.
    ErrorList errors = new ErrorList();
    logger.fine("Deleting artifacts");
    deleteArtifacts(graph, workflow, errors, listeners);
    if (!errors.isEmpty()) {
      throw new DependencyException("Errors found while deleting", errors);
    }
  }

  /**
   * Handles the deleting of the artifacts.
   *
   * @param graph     The artifact graph for the dependencies.
   * @param handler   The workflow to use.
   * @param errors    The error list to store any errors that occur.
   * @param listeners The listeners.
   */
  protected void deleteArtifacts(DependencyGraph graph, Workflow handler, ErrorList errors,
                                 DependencyListener... listeners) {
    Set<ArtifactID> ids = graph.values();
    for (ArtifactID id : ids) {
      // Find all the versions of the artifact that this project uses and delete them all
      Set<String> versions = new HashSet<>();
      List<GraphLink<ArtifactID, DependencyLinkValue>> inboundLinks = graph.getNode(id).getInboundLinksList();
      for (GraphLink<ArtifactID, DependencyLinkValue> inboundLink : inboundLinks) {
        versions.add(inboundLink.value.dependencyVersion);
      }

      for (String version : versions) {
        Artifact artifact = new Artifact(id.group, id.project, id.name, version, id.type);

        // Let the fetchWorkflow handle the resolution from both the local cache and
        // the process objects.
        boolean deleted = false;
        try {
          // Do compat and not and just clean up everything!
          deleted = handler.getPublishWorkflow().delete(artifact, artifact.getArtifactMetaDataFile());
          deleted |= handler.getPublishWorkflow().delete(artifact, artifact.getArtifactMetaDataFile() + ".md5");
          deleted |= handler.getPublishWorkflow().delete(artifact, artifact.getArtifactNegativeMetaDataFile());
          deleted |= handler.getPublishWorkflow().delete(artifact, artifact.getArtifactNegativeMetaDataFile() + ".md5");
          deleted |= handler.getPublishWorkflow().delete(artifact, artifact.getArtifactFile());
          deleted |= handler.getPublishWorkflow().delete(artifact, artifact.getArtifactFile() + ".md5");
          deleted |= handler.getPublishWorkflow().delete(artifact, artifact.getArtifactSourceFile());
          deleted |= handler.getPublishWorkflow().delete(artifact, artifact.getArtifactSourceFile() + ".md5");
          deleted |= handler.getPublishWorkflow().delete(artifact, artifact.getArtifactSourceFile() + ".neg");
          deleted |= handler.getPublishWorkflow().delete(artifact, artifact.getArtifactSourceFile() + ".neg.md5");
        } catch (DependencyException sbe) {
          errors.addError("Error while cleaning artifact [" + artifact + "] - " + sbe.toString());
          logger.log(Level.FINE, "Error while cleaning artifact [" + artifact + "]", sbe);
        }

        if (deleted) {
          for (DependencyListener listener : listeners) {
            listener.artifactCleaned(artifact);
          }

          logger.info("Cleaned out artifact [" + artifact.toString() + "]");
        }
      }
    }
  }
}
