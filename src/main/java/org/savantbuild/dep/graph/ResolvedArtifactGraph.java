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

import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ResolvedArtifact;
import org.savantbuild.lang.Classpath;
import org.savantbuild.util.HashGraph;

import java.util.HashSet;
import java.util.Set;

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
  }

  /**
   * Brute force traverses the graph and locates the Path for the given artifact. This only needs the ArtifactID because
   * this graph will never contain two versions of the same artifact.
   *
   * @param id The id.
   * @return The Path or null if the graph doesn't contain the given Artifact.
   */
  public java.nio.file.Path getPath(ArtifactID id) {
    ResolvedArtifact match = find(root, (artifact) -> artifact.id.equals(id));
    if (match != null) {
      return match.file;
    }

    return null;
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

  public Classpath toClasspath() {
    if (size() == 0) {
      return new Classpath();
    }

    Classpath classpath = new Classpath();
    Set<ResolvedArtifact> visited = new HashSet<>();
    traverse(root, (origin, destination, value, depth) -> {
      if (visited.contains(destination)) {
        return false;
      }

      classpath.path(destination.file);
      visited.add(destination);
      return true;
    });

    return classpath;
  }
}
