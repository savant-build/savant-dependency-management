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
import org.savantbuild.dep.domain.CompatibilityException;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.ResolvedArtifactGraph;
import org.savantbuild.dep.workflow.ArtifactMetaDataMissingException;
import org.savantbuild.dep.workflow.Workflow;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides all of the dependency management services. The main workflow for managing dependencies is:
 * <p/>
 * <pre>
 *   1. Download and parse all of the AMD (Artifact Meta Data) files to build a dependency graph.
 *   2. Traverse the graph and verify that it is a valid graph (doesn't contain conflicting versions of specific
 * artifacts)
 *   3. Traverse the graph and download the dependencies
 * </pre>
 *
 * @author Brian Pontarelli
 */
public interface DependencyService {
  /**
   * Builds a dependency graph for the given dependencies of the given project.
   *
   * @param project      The artifact that represents the project.
   * @param dependencies The declared dependencies of the project.
   * @param workflow     The workflow to use for downloading and caching the AMD files.
   * @return The dependency graph.
   * @throws ArtifactMetaDataMissingException
   *          If any artifacts AMD files could not be downloaded or found locally.
   */
  DependencyGraph buildGraph(Artifact project, Dependencies dependencies, Workflow workflow)
      throws ArtifactMetaDataMissingException;

  /**
   * Resolves the graph by downloading the artifacts. This will use the Workflow to download the artifacts in the graph
   * using the latest version for each artifact. This does not check version compatibility.
   *
   * @param graph         The DependencyGraph to resolve.
   * @param workflow      THe workflow used to resolve the artifacts.
   * @param configuration The resolution configuration that controls which artifact groups to resolve and how they are
   *                      resolved.
   * @param listeners     Any listeners that want to receive callbacks when artifacts are resolved.
   * @return The resolved graph.
   */
  ResolvedArtifactGraph resolve(DependencyGraph graph, Workflow workflow, ResolveConfiguration configuration,
                                DependencyListener... listeners);

  /**
   * Verifies that the given graph contains compatible versions of each artifact. This does not modify the graph in any
   * way.
   *
   * @param graph The graph.
   * @throws CompatibilityException If an dependency has incompatible versions.
   */
  void verifyCompatibility(DependencyGraph graph);

  public static class ResolveConfiguration {
    Map<String, TypeResolveConfiguration> configurations = new HashMap<>();

    public static class TypeResolveConfiguration {
      public boolean transitive;
    }
  }
}
