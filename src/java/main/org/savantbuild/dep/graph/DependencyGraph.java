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
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Dependency;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.Version;

import java.util.List;

/**
 * This class is a artifact and dependency version of the Graph.
 *
 * @author Brian Pontarelli
 */
public class DependencyGraph extends HashGraph<ArtifactID, DependencyLinkValue> {
  public final Artifact root;

  public DependencyGraph(Artifact root) {
    this.root = root;
    addNode(root.id);
  }

  public Dependencies getDependencies(Artifact artifact) {
    List<GraphLink<ArtifactID, DependencyLinkValue>> links = getOutboundLinks(artifact.id);

    Dependencies deps = new Dependencies();
    if (links == null || links.isEmpty()) {
      return deps;
    }

    for (GraphLink<ArtifactID, DependencyLinkValue> link : links) {
      // If this link is for a different version of the artifact, skip it
      if (!link.value.dependentVersion.equals(artifact.version)) {
        continue;
      }

      // Create the artifact for this link
      Dependency dep = link.value.toDependency(link.destination.value);

      // Add the artifact to the group
      DependencyGroup group = deps.groups.get(link.value.type);
      if (group == null) {
        group = new DependencyGroup(link.value.type);
        deps.groups.put(group.type, group);
      }

      group.dependencies.add(dep);
    }

    return deps;
  }

  /**
   * Finds the latest version of the given ArtifactID in the graph.
   *
   * @param id The artifact id.
   * @return THe latest version.
   */
  public Version getLatestVersion(ArtifactID id) {
    GraphNode<ArtifactID, DependencyLinkValue> node = getNode(id);
    return node.getInboundLinks()
               .stream()
               .map((link) -> link.value.dependencyVersion)
               .max(Version::compareTo)
               .get();
  }
}
