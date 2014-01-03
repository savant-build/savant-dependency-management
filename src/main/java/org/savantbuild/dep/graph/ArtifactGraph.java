/*
 * Copyright (c) 2001-2006, Inversoft, All Rights Reserved
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

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.util.HashGraph;

/**
 * This class is an artifact graph that stores the relationship between artifacts. After a DependencyGraph has been
 * built, the process of upgrading all dependencies to a single version and pruning unused dependencies results in an
 * ArtifactGraph.
 *
 * @author Brian Pontarelli
 */
public class ArtifactGraph extends HashGraph<Artifact, String> {
  public final Artifact root;

  public ArtifactGraph(Artifact root) {
    this.root = root;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    final ArtifactGraph that = (ArtifactGraph) o;
    return root.equals(that.root);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + root.hashCode();
    return result;
  }
}
