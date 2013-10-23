/*
 * Copyright (c) 2001-2011, Inversoft, All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.dep;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.graph.ResolvedArtifactGraph;
import org.savantbuild.dep.workflow.Workflow;

import java.util.Set;

/**
 * This interface defines the process for resolving dependencies of the project.
 *
 * @author Brian Pontarelli
 */
public interface DependencyResolver {
  /**
   * Resolves the single artifact using the given workflow. If this is a transitive resolution, the set of artifact
   * group types dictates which transitive dependencies are resolved.
   *
   * @param artifact           The artifact to resolve.
   * @param workflow           The workflow to use.
   * @param artifactGroupTypes The artifact group types to fetch if this is a transitive resolution.
   * @param transitive         Controls if the artifacts dependencies should be resolved or not.
   * @param listeners          Listeners.
   * @return The Files for each artifact that was resolve and cached locally.
   */
  ResolvedArtifactGraph resolve(Artifact artifact, Workflow workflow, Set<String> artifactGroupTypes, boolean transitive, DependencyListener... listeners);

  /**
   * Resolves all of the artifacts in the given dependencies using the given workflow. The set of artifact group types
   * controls which artifacts in the given dependencies are resolved and also the transitive dependencies if this is a
   * transitive resolution.
   *
   * @param dependencies       The dependencies to resolve.
   * @param workflow           The workflow to use.
   * @param artifactGroupTypes The artifact group types to fetch.
   * @param transitive         Controls if the artifacts dependencies should be resolved or not.
   * @param listeners          Listeners.
   * @return The Files for each artifact that was resolve and cached locally.
   */
  ResolvedArtifactGraph resolve(Dependencies dependencies, Workflow workflow, Set<String> artifactGroupTypes, boolean transitive, DependencyListener... listeners);

  /**
   * Generates a Dependencies object for the given artifact.
   *
   * @param artifact the artifact.
   * @param workflow The workflow used to fetch the AMD files.
   * @return The Dependencies and never null.
   */
  Dependencies dependencies(Artifact artifact, Workflow workflow);
}
