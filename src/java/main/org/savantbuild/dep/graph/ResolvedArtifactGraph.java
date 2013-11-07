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

import org.savantbuild.dep.domain.ResolvedArtifact;

/**
 * This class is a resolved artifact and dependency version of the Graph. The link between graph nodes is the artifact
 * group type as a String. The nodes contain the resolved artifact's, which include the Path of the artifact on the
 * local file system.
 *
 * @author Brian Pontarelli
 */
public class ResolvedArtifactGraph extends HashGraph<ResolvedArtifact, String> {
  public final ResolvedArtifact root;

  public ResolvedArtifactGraph(ResolvedArtifact root) {
    this.root = root;
    addNode(root);
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

    final ResolvedArtifactGraph that = (ResolvedArtifactGraph) o;
    return root.equals(that.root);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + root.hashCode();
    return result;
  }
}
