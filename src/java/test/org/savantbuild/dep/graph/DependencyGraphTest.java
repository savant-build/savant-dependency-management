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

import java.util.List;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.Dependencies;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * <p>
 * This class is a test case for the artifact graph data
 * structure.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class DependencyGraphTest {
  @Test
  public void graphDeps() {
    Artifact a1 = new Artifact("group", "project", "artifact1", "1.0", "jar");
    Artifact a2 = new Artifact("group", "project", "artifact2", "1.0", "jar");
    Artifact a3 = new Artifact("group", "project", "artifact3", "1.0", "jar");
    Artifact a4 = new Artifact("group", "project", "artifact4", "1.0", "jar");

    DependencyGraph ag = new DependencyGraph(a1);
    ag.addLink(a1.getId(), a2.getId(), new DependencyLinkValue(a1.getVersion(), a2.getVersion(), "compile", null));
    ag.addLink(a1.getId(), a3.getId(), new DependencyLinkValue(a1.getVersion(), a3.getVersion(), "compile", null));
    ag.addLink(a3.getId(), a4.getId(), new DependencyLinkValue(a3.getVersion(), a4.getVersion(), "compile", null));

    Dependencies deps = ag.getDependencies(a1);
    DependencyGroup group = deps.getArtifactGroups().get("compile");
    assertTrue(group.getArtifacts().contains(a2));
    assertTrue(group.getArtifacts().contains(a3));
  }

  @Test
  public void artifactGraphPaths() {
    Artifact a1 = new Artifact("group", "project", "artifact1", "1.0", "jar");
    Artifact a2 = new Artifact("group", "project", "artifact2", "1.0", "jar");
    Artifact a3 = new Artifact("group", "project", "artifact3", "1.0", "jar");
    Artifact a4 = new Artifact("group", "project", "artifact4", "1.0", "jar");

    DependencyGraph ag = new DependencyGraph(a1);
    ag.addLink(a1.getId(), a2.getId(), new DependencyLinkValue(a1.getVersion(), a2.getVersion(), "compile", null));
    ag.addLink(a1.getId(), a3.getId(), new DependencyLinkValue(a1.getVersion(), a3.getVersion(), "compile", null));
    ag.addLink(a2.getId(), a4.getId(), new DependencyLinkValue(a2.getVersion(), a4.getVersion(), "compile", null));
    ag.addLink(a3.getId(), a4.getId(), new DependencyLinkValue(a3.getVersion(), a4.getVersion(), "compile", null));

    List<GraphPath<ArtifactID>> paths = ag.paths(a1.getId(), a4.getId());
    assertEquals(2, paths.size());
    assertEquals(3, paths.get(0).getPath().size());
    assertEquals(3, paths.get(1).getPath().size());
    assertSame(a1.getId(), paths.get(0).getPath().get(0));
    assertSame(a2.getId(), paths.get(0).getPath().get(1));
    assertSame(a4.getId(), paths.get(0).getPath().get(2));
    assertSame(a1.getId(), paths.get(1).getPath().get(0));
    assertSame(a3.getId(), paths.get(1).getPath().get(1));
    assertSame(a4.getId(), paths.get(1).getPath().get(2));
  }
}
