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

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Dependency;
import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.DependencyLinkValue;
import org.savantbuild.dep.graph.Graph;
import org.savantbuild.dep.graph.ResolvedArtifactGraph;
import org.savantbuild.dep.workflow.Workflow;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

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
  public DependencyGraph buildGraph(Artifact project, Dependencies dependencies, Workflow workflow) throws ArtifactMetaDataMissingException {
    logger.fine("Building DependencyGraph");
    DependencyGraph graph = new DependencyGraph(project);
    populateGraph(graph, new Dependency(project.id, project.version, false), dependencies, workflow, new HashSet<>());
    return graph;
  }

  @Override
  public ResolvedArtifactGraph resolve(DependencyGraph graph, Workflow workflow, ResolveConfiguration configuration,
                                       DependencyListener... listeners) {
    return null;
  }

  @Override
  public void verifyCompatibility(DependencyGraph graph) {
  }

  /**
   * Adds the artifact dependencies to the {@link Graph} if the origin artifact is given. Otherwise, this simply adds
   * the artifacts to the graph without any links (edges).
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
}
