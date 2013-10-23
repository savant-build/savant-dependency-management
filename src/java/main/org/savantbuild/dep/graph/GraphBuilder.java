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
package org.savantbuild.dep.graph;

import org.savantbuild.dep.DependencyException;
import org.savantbuild.dep.ResolutionContext;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.version.ArtifactVersionTools;
import org.savantbuild.dep.workflow.Workflow;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * This class is used to build out an {@link Graph} that contains a map of all the dependencies between artifacts. This
 * is done by traversing the transitive dependencies for each artifact in a {@link Dependencies} object.
 *
 * @author Brian Pontarelli
 */
public class GraphBuilder {
  private final static Logger logger = Logger.getLogger(GraphBuilder.class.getName());

  private final Dependencies dependencies;

  private final boolean transitive;

  private final Workflow workflow;

  /**
   * Constructs a new graph builder.
   *
   * @param dependencies    The dependencies that will be used to build the dependency graph. This will be used to
   *                        resolve all transitive dependencies.
   * @param workflow The workflow used to fetch and publish the dependencies of artifacts during
   *                        transitive graph building.
   * @param transitive      Determines if when building the graph, this class should include transitive dependencies.
   * @throws DependencyException If the graph population encountered any errors.
   */
  public GraphBuilder(Dependencies dependencies, Workflow workflow, boolean transitive) {
    if (dependencies == null || workflow == null) {
      throw new DependencyException("A Dependencies and Workflow are required for " +
          "constructing a GraphBuilder");
    }

    this.dependencies = dependencies;
    this.workflow = workflow;
    this.transitive = transitive;
  }

  /**
   * Constructs the graph using all of the configuration given in the constructor.
   *
   * @param resolutionContext The resolution context.
   * @return The graph.
   */
  public DependencyGraph buildGraph(ResolutionContext resolutionContext) {
    Artifact projectArtifact = new Artifact("__PROJECT__GROUP__", "__PROJECT__NAME__", "__PROJECT__ARTIFACT__",
        "__PROJECT__VERSION__", "__ARTIFACT__TYPE__");
    DependencyGraph graph = new DependencyGraph(projectArtifact);

    // There must be a project artifact so that the version of the project's direct dependencies
    // is stored in the graph
    populateGraph(graph, projectArtifact, dependencies, new HashSet<>(), resolutionContext);
    dependencies.graph = graph;

    return graph;
  }

  /**
   * Adds the artifact dependencies to the {@link Graph} if the origin artifact is given. Otherwise, this simply adds
   * the artifacts to the graph without any links (edges).
   *
   * @param graph             The Graph to populate.
   * @param originArtifact    The origin artifact that is dependent on the Dependencies given.
   * @param dependencies      The list of dependencies to extract the artifacts from.
   * @param artifactsRecursed The set of artifacts already resolved and recursed for.
   * @param resolutionContext The resolution context.
   */
  protected void populateGraph(DependencyGraph graph, Artifact originArtifact, Dependencies dependencies,
                               Set<Artifact> artifactsRecursed, ResolutionContext resolutionContext) {

    logger.fine("Running integration build resolver");
    ArtifactVersionTools.resolve(dependencies, workflow);

    Map<String, DependencyGroup> groups = dependencies.groups;
    for (String type : groups.keySet()) {
      DependencyGroup ag = groups.get(type);
      List<Artifact> artifacts = ag.dependencies;
      for (Artifact artifact : artifacts) {

        GraphNode<ArtifactID, DependencyLinkValue> destination = graph.getNode(artifact.id);
        if (destination == null) {
          destination = graph.addNode(artifact.id);
        }

        // Create a link using nodes so that we can be explicit
        ArtifactMetaData amd = workflow.getFetchWorkflow()
                                              .fetchMetaData(artifact, workflow.getPublishWorkflow(),
                                                  resolutionContext);

        String compatibility = (amd != null) ? amd.compatibility : null;
        GraphNode<ArtifactID, DependencyLinkValue> origin = graph.addNode(originArtifact.id);
        DependencyLinkValue link = new DependencyLinkValue(originArtifact.version, artifact.version, type, compatibility);
        graph.addLink(origin, destination, link);

        // If we have already recursed this artifact, skip it.
        if (artifactsRecursed.contains(artifact)) {
          continue;
        }

        // Recurse
        if (amd != null && amd.dependencies != null && transitive) {
          populateGraph(graph, artifact, amd.dependencies, artifactsRecursed, resolutionContext);
        }

        // Add the artifact to the list
        artifactsRecursed.add(artifact);
      }
    }
  }
}
