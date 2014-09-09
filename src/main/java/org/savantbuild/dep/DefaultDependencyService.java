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

import java.io.IOException;
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
import org.savantbuild.dep.graph.ResolvedArtifactGraph;
import org.savantbuild.dep.workflow.ArtifactMetaDataMissingException;
import org.savantbuild.dep.workflow.ArtifactMissingException;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.workflow.Workflow;
import org.savantbuild.dep.workflow.process.ProcessFailureException;
import org.savantbuild.dep.xml.ArtifactTools;
import org.savantbuild.io.FileTools;
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
   * {@inheritDoc}
   */
  @Override
  public DependencyGraph buildGraph(ReifiedArtifact project, Dependencies dependencies, Workflow workflow)
      throws ArtifactMetaDataMissingException, ProcessFailureException, MD5Exception {
    output.debug("Building DependencyGraph with a root of [%s]", project);
    DependencyGraph graph = new DependencyGraph(project);
    populateGraph(graph, project, dependencies, workflow, new HashSet<>());
    return graph;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void publish(Publication publication, PublishWorkflow workflow) throws PublishException {
    output.info("Publishing [%s]", publication);

    try {
      Path amdFile = ArtifactTools.generateXML(publication.metaData);
      publishItem(publication.artifact, publication.artifact.getArtifactMetaDataFile(), amdFile, workflow);
      publishItem(publication.artifact, publication.artifact.getArtifactFile(), publication.file, workflow);

      if (publication.sourceFile != null) {
        publishItem(publication.artifact, publication.artifact.getArtifactSourceFile(), publication.sourceFile, workflow);
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
    output.debug("Reducing DependencyGraph with a root of [%s]", graph.root);

    // Traverse graph. At each node, if the node's parents haven't all been checked. Skip it.
    // If the node's parents have all been checked, for each parent, get the version of the node for the version of the
    // parent that was kept. Ensure all these versions are compatible. Select the highest one. Add that to the
    // ArtifactGraph. Store the kept version. Continue.

    ArtifactGraph artifactGraph = new ArtifactGraph(graph.root);
    Map<ArtifactID, ReifiedArtifact> artifacts = new HashMap<>();
    artifacts.put(graph.root.id, graph.root);

    graph.traverse(graph.root.id, false, (origin, destination, edgeValue, depth) -> {
      List<Edge<ArtifactID, DependencyEdgeValue>> inboundEdges = graph.getInboundEdges(destination);
      boolean alreadyCheckedAllParents = inboundEdges.size() > 0 && inboundEdges.stream().allMatch((edge) -> artifacts.containsKey(edge.getOrigin()));
      if (alreadyCheckedAllParents) {
        List<Edge<ArtifactID, DependencyEdgeValue>> significantInbound =
            inboundEdges.stream()
                        .filter((edge) -> edge.getValue().dependentVersion.equals(artifacts.get(edge.getOrigin()).version))
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

        // This dependency is no longer used
        if (min == null || max == null) {
          return false;
        }

        // Ensure min and max are compatible
        if (!min.isCompatibleWith(max)) {
          throw new CompatibilityException(destination, min, max);
        }

        // Build the artifact for this node, save it in the Map and put it in the ArtifactGraph
        ReifiedArtifact destinationArtifact = new ReifiedArtifact(destination, max, edgeValue.license);
        artifacts.put(destination, destinationArtifact);

        significantInbound.stream()
                          .forEach((edge) -> {
                            ReifiedArtifact originArtifact = artifacts.get(edge.getOrigin());
                            artifactGraph.addEdge(originArtifact, destinationArtifact, edge.getValue().type);
                          });
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
    output.debug("Resolving ArtifactGraph with a root of [%s]", graph.root);

    ResolvedArtifact root = new ResolvedArtifact(graph.root.id, graph.root.version, graph.root.license, null, null);
    ResolvedArtifactGraph resolvedGraph = new ResolvedArtifactGraph(root);

    Map<ReifiedArtifact, ResolvedArtifact> map = new HashMap<>();
    map.put(graph.root, root);

    AtomicReference<GroupTraversalRule> rootTypeResolveConfiguration = new AtomicReference<>();

    graph.traverse(graph.root, false, (origin, destination, group, depth) -> {
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

      if (groupTraversalRule.disallowedLicenses.contains(destination.license)) {
        throw new LicenseException(destination);
      }

      Path file = workflow.fetchArtifact(destination).toAbsolutePath();

      // Optionally fetch the source
      Path sourceFile = null;
      if (groupTraversalRule.fetchSource) {
        sourceFile = workflow.fetchSource(destination);
      }

      // Add to the graph
      ResolvedArtifact resolvedArtifact = new ResolvedArtifact(destination.id, destination.version, destination.license, file, sourceFile);
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
      for (Artifact dependency : group.dependencies) {
        ArtifactMetaData amd = workflow.fetchMetaData(dependency);

        // Create an edge using nodes so that we can be explicit
        DependencyEdgeValue edge = new DependencyEdgeValue(origin.version, dependency.version, type, amd.license);
        graph.addEdge(origin.id, dependency.id, edge);

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
    Path md5File = FileTools.createTempPath("artifact-item", "md5", true);
    MD5.writeMD5(md5, md5File);
    workflow.publish(artifact, item + ".md5", md5File);
    workflow.publish(artifact, item, file);
  }
}
