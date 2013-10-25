/*
 * Copyright (c) 2001-2013, Inversoft, All Rights Reserved
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

import org.savantbuild.dep.DependencyService.ResolveConfiguration.TypeResolveConfiguration;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.CompatibilityException;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Dependency;
import org.savantbuild.dep.domain.ResolvedArtifact;
import org.savantbuild.dep.domain.Version;
import org.savantbuild.dep.graph.CyclicException;
import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.DependencyLinkValue;
import org.savantbuild.dep.graph.GraphNode;
import org.savantbuild.dep.graph.ResolvedArtifactGraph;
import org.savantbuild.dep.workflow.ArtifactMetaDataMissingException;
import org.savantbuild.dep.workflow.Workflow;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.Arrays.asList;

/**
 * Default implementation of the dependency service.
 *
 * @author Brian Pontarelli
 */
public class DefaultDependencyService implements DependencyService {
  private final static Logger logger = Logger.getLogger(DefaultDependencyService.class.getName());

  /**
   * {@inheritDoc}
   */
  @Override
  public DependencyGraph buildGraph(Artifact project, Dependencies dependencies, Workflow workflow)
      throws ArtifactMetaDataMissingException {
    logger.fine("Building DependencyGraph");
    DependencyGraph graph = new DependencyGraph(project);
    populateGraph(graph, new Dependency(project.id, project.version, false), dependencies, workflow, new HashSet<>());
    return graph;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ResolvedArtifactGraph resolve(DependencyGraph graph, Workflow workflow, ResolveConfiguration configuration,
                                       DependencyListener... listeners) {
    ResolvedArtifact root = new ResolvedArtifact(graph.root.id, graph.root.version);
    ResolvedArtifactGraph resolvedGraph = new ResolvedArtifactGraph(root);
    Deque<ArtifactID> visited = new ArrayDeque<>();
    visited.push(root.id);
    resolve(graph, resolvedGraph, workflow, configuration, root, visited, listeners);
    return resolvedGraph;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void verifyCompatibility(DependencyGraph graph) throws CompatibilityException {
    for (GraphNode<ArtifactID, DependencyLinkValue> node : graph.getNodes()) {
      Version min = node.getInboundLinks()
                        .stream()
                        .map((link) -> link.value.dependencyVersion)
                        .min(Version::compareTo)
                        .get();
      Version max = node.getInboundLinks()
                        .stream()
                        .map((link) -> link.value.dependencyVersion)
                        .max(Version::compareTo)
                        .get();
      if (!min.isCompatibleWith(max)) {
        throw new CompatibilityException(node.value, min, max);
      }
    }
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
  private void populateGraph(DependencyGraph graph, Dependency origin, Dependencies dependencies, Workflow workflow,
                             Set<Artifact> artifactsRecursed) throws ArtifactMetaDataMissingException {
    dependencies.groups.forEach((type, group) -> {
      for (Dependency dependency : group.dependencies) {
        // Create a link using nodes so that we can be explicit
        DependencyLinkValue link = new DependencyLinkValue(origin.version, dependency.version, type, dependency.optional);
        graph.addLink(origin.id, dependency.id, link);

        // If we have already recursed this artifact, skip it.
        if (artifactsRecursed.contains(dependency)) {
          continue;
        }

        // Recurse
        ArtifactMetaData amd = workflow.fetchMetaData(dependency);
        populateGraph(graph, dependency, amd.dependencies, workflow, artifactsRecursed);

        // Add the artifact to the list
        artifactsRecursed.add(dependency);
      }
    });
  }

  private void resolve(DependencyGraph graph, ResolvedArtifactGraph resolvedGraph, Workflow workflow,
                       ResolveConfiguration configuration, ResolvedArtifact origin, Deque<ArtifactID> visited,
                       DependencyListener... listeners) {
    Dependencies dependencies = graph.getDependencies(graph.root);
    dependencies.groups.forEach((type, group) -> {
      if (!configuration.groupConfigurations.containsKey(type)) {
        return;
      }

      group.dependencies.forEach((dependency) -> {
        if (visited.contains(dependency.id)) {
          throw new CyclicException("The dependency [" + dependency + "] was encountered twice. This means you have a cyclic in your dependencies");
        }

        Version latest = graph.getLatestVersion(dependency.id);
        ResolvedArtifact resolvedArtifact = new ResolvedArtifact(dependency.id, latest);
        resolvedArtifact.file = workflow.fetchArtifact(resolvedArtifact);
        resolvedGraph.addLink(origin, resolvedArtifact, type);

        // Optionally fetch the source
        TypeResolveConfiguration typeResolveConfiguration = configuration.groupConfigurations.get(type);
        if (typeResolveConfiguration.fetchSource) {
          workflow.fetchSource(resolvedArtifact);
        }

        // Call the listeners
        asList(listeners).forEach((listener) -> {
          listener.artifactFetched(resolvedArtifact);
        });

        // Recurse if the configuration is set to transitive
        if (typeResolveConfiguration.transitive) {
          visited.push(dependency.id);
          resolve(graph, resolvedGraph, workflow, configuration, resolvedArtifact, visited, listeners);
          visited.pop();
        }
      });
    });
  }
}
