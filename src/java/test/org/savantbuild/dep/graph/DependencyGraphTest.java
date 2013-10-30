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
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Dependency;
import org.savantbuild.dep.domain.DependencyGroup;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

/**
 * This class is a test case for the artifact graph data structure.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class DependencyGraphTest {
  @Test
  public void artifactGraphPaths() {
    Dependency d1 = new Dependency("group:project:artifact1:1.0:jar", false);
    Dependency d2 = new Dependency("group:project:artifact2:1.0:jar", false);
    Dependency d3 = new Dependency("group:project:artifact3:1.0:jar", false);
    Dependency d4 = new Dependency("group:project:artifact4:1.0:jar", false);

    DependencyGraph ag = new DependencyGraph(d1);
    ag.addLink(d1.id, d2.id, new DependencyLinkValue(d1.version, d2.version, "compile", false));
    ag.addLink(d1.id, d3.id, new DependencyLinkValue(d1.version, d3.version, "compile", false));
    ag.addLink(d2.id, d4.id, new DependencyLinkValue(d2.version, d4.version, "compile", false));
    ag.addLink(d3.id, d4.id, new DependencyLinkValue(d3.version, d4.version, "compile", false));

    List<GraphPath<ArtifactID>> paths = ag.getPaths(d1.id, d4.id);
    assertEquals(2, paths.size());
    assertEquals(3, paths.get(0).getPath().size());
    assertEquals(3, paths.get(1).getPath().size());
    assertSame(d1.id, paths.get(0).getPath().get(0));
    assertSame(d2.id, paths.get(0).getPath().get(1));
    assertSame(d4.id, paths.get(0).getPath().get(2));
    assertSame(d1.id, paths.get(1).getPath().get(0));
    assertSame(d3.id, paths.get(1).getPath().get(1));
    assertSame(d4.id, paths.get(1).getPath().get(2));
  }

  @Test
  public void graphToDependenciesObject() {
    Dependency d1 = new Dependency("group:project:artifact1:1.0:jar", false);
    Dependency d2 = new Dependency("group:project:artifact2:1.0:jar", false);
    Dependency d3 = new Dependency("group:project:artifact3:1.0:jar", true);
    Dependency d4 = new Dependency("group:project:artifact4:1.0:jar", false);

    DependencyGraph ag = new DependencyGraph(d1);
    ag.addLink(d1.id, d2.id, new DependencyLinkValue(d1.version, d2.version, "compile", false));
    ag.addLink(d1.id, d3.id, new DependencyLinkValue(d1.version, d3.version, "compile", true));
    ag.addLink(d3.id, d4.id, new DependencyLinkValue(d3.version, d4.version, "compile", false));

    Dependencies actual = ag.getDependencies(d1);
    Dependencies expected = new Dependencies(new DependencyGroup("compile", d2, d3));
    assertEquals(actual, expected);
  }
}
