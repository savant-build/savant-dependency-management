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
package org.savantbuild.dep.graph;

import java.util.Formatter;

import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ReifiedArtifact;
import org.savantbuild.util.HashGraph;

/**
 * This class is a artifact and dependency version of the Graph.
 *
 * @author Brian Pontarelli
 */
public class DependencyGraph extends HashGraph<ArtifactID, DependencyEdgeValue> {
  public final ReifiedArtifact root;

  public DependencyGraph(ReifiedArtifact root) {
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

    final DependencyGraph that = (DependencyGraph) o;
    return root.equals(that.root);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + root.hashCode();
    return result;
  }

  /**
   * Outputs this DependencyGraph as a GraphViz DOT file.
   *
   * @return The DOT file String.
   */
  public String toDOT() {
    StringBuilder build = new StringBuilder();
    build.append("digraph Dependencies {\n");

    Formatter formatter = new Formatter(build);
    traverse(root.id, false, (origin, destination, edge, depth) -> {
      formatter.format("  \"%s\" -> \"%s\" [label=\"%s\", headlabel=\"%s\", taillabel=\"%s\"];\n", origin, destination, edge.type, edge.dependentVersion, edge.dependencyVersion);
      return true;
    });

    build.append("}\n");
    return build.toString();
  }
}
