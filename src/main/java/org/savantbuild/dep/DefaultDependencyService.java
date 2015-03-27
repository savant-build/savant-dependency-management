/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.savantbuild.dep.DependencyService.TraversalRules.GroupTraversalRule;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.CompatibilityException;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Publication;
import org.savantbuild.dep.domain.ReifiedArtifact;
import org.savantbuild.dep.domain.ResolvedArtifact;
import org.savantbuild.dep.domain.Version;
import org.savantbuild.dep.graph.ArtifactGraph;
import org.savantbuild.dep.graph.DependencyEdgeValue;
import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.DependencyGraph.Dependency;
import org.savantbuild.dep.graph.ResolvedArtifactGraph;
import org.savantbuild.dep.workflow.ArtifactMetaDataMissingException;
import org.savantbuild.dep.workflow.ArtifactMissingException;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.workflow.Workflow;
import org.savantbuild.dep.workflow.process.ProcessFailureException;
import org.savantbuild.dep.xml.ArtifactTools;
import org.savantbuild.output.Output;
import org.savantbuild.security.MD5;
import org.savantbuild.security.MD5Exception;
import org.savantbuild.util.CyclicException;
import org.savantbuild.util.Graph.Edge;

import static java.util.Arrays.asList;

/**
 * Default implementation of the dependency service.
 *
 * @author Brian Pontarelli
 */
public class DefaultDependencyService implements DependencyService {
  private final Output output;

  public DefaultDependencyService(Output output) {
    this.output = output;
  }

  /**
   * Creates a temporary file.
   *
   * @param prefix       The prefix for the temporary file.
   * @param suffix       The suffix for the temporary file.
   * @param deleteOnExit If the file should be deleted when the JVM exits.
   * @return The Path of the temporary file.
   * @throws IOException If the create fails.
   */
  public static Path createTempPath(String prefix, String suffix, boolean deleteOnExit) throws IOException {
    File file = File.createTempFile(prefix, suffix);
    if (deleteOnExit) {
      file.deleteOnExit();
    }

    return file.toPath();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DependencyGraph buildGraph(ReifiedArtifact project, Dependencies dependencies, Workflow workflow)
      throws ArtifactMetaDataMissingException, ProcessFailureException, MD5Exception {
    output.debugln("Building DependencyGraph with a root of [%s]", project);
    DependencyGraph graph = new DependencyGraph(project);
    populateGraph(graph, project, dependencies, workflow, new HashSet<>());
    return graph;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void publish(Publication publication, PublishWorkflow workflow) throws PublishException {
    if (!Files.isReadable(publication.file)) {
      throw new PublishException("The publication file [" + publication.file + "] for the publication [" + publication.artifact + "] doesn't exist.");
    }

    if (publication.sourceFile != null && !Files.isReadable(publication.sourceFile)) {
      throw new PublishException("The publication source file [" + publication.sourceFile + "] for the publication [" + publication.artifact + "] doesn't exist.");
    }

    output.infoln("Publishing [%s]", publication);

    try {
      Path amdFile = ArtifactTools.generateXML(publication.metaData);
      publishItem(publication.artifact, publication.artifact.getArtifactMetaDataFile(), amdFile, workflow);
      publishItem(publication.artifact, publication.artifact.getArtifactFile(), publication.file, workflow);

      if (publication.sourceFile != null) {
        publishItem(publication.artifact, publication.artifact.getArtifactSourceFile(), publication.sourceFile, workflow);
      } else {
        workflow.publishNegative(publication.artifact, publication.artifact.getArtifactSourceFile());
      }
    } catch (IOException e) {
      throw new PublishException(publication, e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ArtifactGraph reduce(DependencyGraph graph) throws CompatibilityException, CyclicException {
    output.debugln("Reducing DependencyGraph with a root of [%s]", graph.root);

    // Traverse graph. At each node, if the node's parents haven't all been checked. Skip it.
    // If the node's parents have all been checked, for each parent, get the version of the node for the version of the
    // parent that was kept. Ensure all these versions are compatible. Select the highest one. Add that to the
    // ArtifactGraph. Store the kept version. Continue.

    ArtifactGraph artifactGraph = new ArtifactGraph(graph.root);
    Map<ArtifactID, ReifiedArtifact> artifacts = new HashMap<>();
    artifacts.put(graph.root.id, graph.root);

    graph.traverse(new Dependency(graph.root.id), false, null, (origin, destination, edgeValue, depth, isLast) -> {
      List<Edge<Dependency, DependencyEdgeValue>> inboundEdges = graph.getInboundEdges(destination);
      boolean alreadyCheckedAllParents = inboundEdges.size() > 0 && inboundEdges.stream().allMatch((edge) -> artifacts.containsKey(edge.getOrigin().id));
      if (alreadyCheckedAllParents) {
        output.debugln("Already checked all parents so we know the versions of them at this point. Working on node [%s]", destination);

        // Determine all of the versions of this dependency
        List<Edge<Dependency, DependencyEdgeValue>> significantInbound =
            inboundEdges.stream()
                        .filter((edge) -> edge.getValue().dependentVersion.equals(artifacts.get(edge.getOrigin().id).version))
                        .collect(Collectors.toList());

        // This is the complex part, for each inbound edge, grab the one where the origin is the correct version (based
        // on the versions we have already kept). Then for each of those, map to the dependency version (the version of
        // the destination node). Then get the min and max.
        Version min = significantInbound.stream()
                                        .map((edge) -> edge.getValue().dependencyVersion)
                                        .min(Version::compareTo)
                                        .orElse(null);
        Version max = significantInbound.stream()
                                        .map((edge) -> edge.getValue().dependencyVersion)
                                        .max(Version::compareTo)
                                        .orElse(null);

        output.debugln("Min [%s] and max [%s]", min, max);

        // This dependency is no longer used
        if (min == null || max == null) {
          output.debugln("NO LONGER USED");
          return false;
        }

        // Ensure min and max are compatible
        if (!destination.skipCompatibilityCheck && !min.isCompatibleWith(max)) {
          output.debugln("INCOMPATIBLE");
          throw new CompatibilityException(graph, destination, min, max);
        }

        // Build the artifact for this node, save it in the Map and put it in the ArtifactGraph
        ReifiedArtifact destinationArtifact = new ReifiedArtifact(destination.id, max, edgeValue.licenses);
        artifacts.put(destination.id, destinationArtifact);

        significantInbound.stream()
                          .forEach((edge) -> {
                            ReifiedArtifact originArtifact = artifacts.get(edge.getOrigin().id);
                            artifactGraph.addEdge(originArtifact, destinationArtifact, edge.getValue().type);
                          });
      } else {
        output.debugln("Skipping dependency [%s] for now. Not all its parents have been checked", destination);
      }

      return true; // Always continue traversal
    });

    return artifactGraph;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ResolvedArtifactGraph resolve(ArtifactGraph graph, Workflow workflow, TraversalRules configuration,
                                       DependencyListener... listeners)
      throws CyclicException, ArtifactMissingException, ProcessFailureException, MD5Exception, LicenseException {
    output.debugln("Resolving ArtifactGraph with a root of [%s]", graph.root);

    ResolvedArtifact root = new ResolvedArtifact(graph.root.id, graph.root.version, graph.root.licenses, null, null);
    ResolvedArtifactGraph resolvedGraph = new ResolvedArtifactGraph(root);

    Map<ReifiedArtifact, ResolvedArtifact> map = new HashMap<>();
    map.put(graph.root, root);

    AtomicReference<GroupTraversalRule> rootTypeResolveConfiguration = new AtomicReference<>();

    graph.traverse(graph.root, false, null, (origin, destination, group, depth, isLast) -> {
      // If we are at the root, check if the group is to be resolved. If we are below the root, then we need to ensure
      // that the root was setup to fetch the group transitively
      GroupTraversalRule groupTraversalRule;
      if (origin.equals(graph.root)) {
        groupTraversalRule = configuration.rules.get(group);
        if (groupTraversalRule == null) {
          return false;
        }

        rootTypeResolveConfiguration.set(groupTraversalRule);
      } else {
        groupTraversalRule = rootTypeResolveConfiguration.get();
        if (groupTraversalRule.transitiveGroups.size() > 0 && !groupTraversalRule.transitiveGroups.contains(group)) {
          return false;
        }
      }

      if (groupTraversalRule.disallowedLicenses.stream().filter(destination.licenses.keySet()::contains).findFirst().isPresent()) {
        throw new LicenseException(destination);
      }

      Path file = workflow.fetchArtifact(destination).toAbsolutePath();

      // Optionally fetch the source
      Path sourceFile = null;
      if (groupTraversalRule.fetchSource) {
        sourceFile = workflow.fetchSource(destination);
      }

      // Add to the graph
      ResolvedArtifact resolvedArtifact = new ResolvedArtifact(destination.id, destination.version, destination.licenses, file, sourceFile);
      resolvedGraph.addEdge(map.get(origin), resolvedArtifact, group);
      map.put(destination, resolvedArtifact);

      // Call the listeners
      asList(listeners).forEach((listener) -> listener.artifactFetched(resolvedArtifact));

      // Recurse if the configuration is set to transitive (or not set)
      return groupTraversalRule.transitive;
    });

    return resolvedGraph;
  }

  /**
   * Recursively populates the DependencyGraph starting with the given origin and its dependencies. This fetches the
   * ArtifactMetaData for all of the dependencies and performs a breadth first traversal of the graph. If an dependency
   * has already been encountered and traversed, this does not traverse it again. The Set is used to track the
   * dependencies that have already been encountered.
   *
   * @param graph             The Graph to populate.
   * @param origin            The origin artifact that is dependent on the Dependencies given.
   * @param dependencies      The list of dependencies to extract the artifacts from.
   * @param workflow          The workflow used to fetch the AMD files.
   * @param artifactsRecursed The set of artifacts already resolved and recursed for.
   */
  private void populateGraph(DependencyGraph graph, ReifiedArtifact origin, Dependencies dependencies, Workflow workflow,
                             Set<Artifact> artifactsRecursed)
      throws ArtifactMetaDataMissingException, ProcessFailureException, MD5Exception {
    dependencies.groups.forEach((type, group) -> {
      output.debugln("Loading dependency group [%s]", type);

      for (Artifact dependency : group.dependencies) {
        output.debugln("Loading dependency [%s] skipCompatibilityCheck=[%b]", dependency, dependency.skipCompatibilityCheck);

        ArtifactMetaData amd = workflow.fetchMetaData(dependency);

        // Create an edge using nodes so that we can be explicit
        DependencyEdgeValue edge = new DependencyEdgeValue(origin.version, dependency.version, type, amd.licenses);
        graph.addEdge(new Dependency(origin.id), new Dependency(dependency.id), edge);
        if (dependency.skipCompatibilityCheck) {
          output.debugln("SKIPPING COMPATIBILITY CHECK for [%s]", dependency.id);
          graph.skipCompatibilityCheck(dependency.id);
        }

        // If we have already recursed this artifact, skip it.
        if (artifactsRecursed.contains(dependency)) {
          continue;
        }

        // Recurse
        if (amd.dependencies != null) {
          ReifiedArtifact artifact = amd.toLicensedArtifact(dependency);
          populateGraph(graph, artifact, amd.dependencies, workflow, artifactsRecursed);
        }

        // Add the artifact to the list
        artifactsRecursed.add(dependency);
      }
    });
  }

  /**
   * Publishes a single item for the given artifact.
   *
   * @param artifact The artifact.
   * @param item     The item to publish.
   * @param file     The file to publish.
   * @param workflow The publish workflow.
   * @throws IOException If the publication fails.
   */
  private void publishItem(Artifact artifact, String item, Path file, PublishWorkflow workflow) throws IOException {
    MD5 md5 = MD5.forPath(file);
    Path md5File = createTempPath("artifact-item", "md5", true);
    MD5.writeMD5(md5, md5File);
    workflow.publish(artifact, item + ".md5", md5File);
    workflow.publish(artifact, item, file);
  }
}
