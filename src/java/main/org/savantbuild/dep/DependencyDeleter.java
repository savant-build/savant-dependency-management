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
import org.savantbuild.dep.workflow.Workflow;

/**
 * This interface defines the process for deleting artifacts.
 *
 * @author Brian Pontarelli
 */
public interface DependencyDeleter {
  /**
   * Deletes the given artifact (and possibly all of its dependencies) from any local caches or other locations that
   * support deleting.
   *
   * @param artifact   The artifact to delete.
   * @param workflow   The workflow to use for deleting. This dictates where the artifact is deleted.
   * @param transitive Whether or not to delete the artifacts dependencies.
   * @param listeners  Listeners.
   */
  void delete(Artifact artifact, Workflow workflow, boolean transitive, DependencyListener... listeners);

  /**
   * Deletes all of the artifacts (and possibly all of their dependencies) in the given dependencies set from any local
   * caches or other locations that support deleting.
   *
   * @param dependencies The dependencies to delete.
   * @param workflow     The workflow to use for deleting. This dictates where the artifact is deleted.
   * @param transitive   Whether or not to delete the artifacts dependencies.
   * @param listeners    Listeners.
   */
  void delete(Dependencies dependencies, Workflow workflow, boolean transitive, DependencyListener... listeners);
}
