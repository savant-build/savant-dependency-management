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

import org.savantbuild.dep.BaseUnitTest;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.ReifiedArtifact;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * This class is a test case for the artifact graph data structure.
 *
 * @author Brian Pontarelli
 */
public class DependencyGraphTest extends BaseUnitTest {
  @Test
  public void equals() {
    ReifiedArtifact one = new ReifiedArtifact("group:project:artifact1:1.0:jar", License.Apachev2);
    ReifiedArtifact two = new ReifiedArtifact("group:project:artifact2:1.0:jar", License.Commercial);
    ReifiedArtifact three = new ReifiedArtifact("group:project:artifact3:1.0:jar", License.Commercial);
    ReifiedArtifact four = new ReifiedArtifact("group:project:artifact4:1.0:jar", License.Commercial);

    DependencyGraph graph = new DependencyGraph(one);
    graph.addEdge(one.id, two.id, new DependencyEdgeValue(one.version, two.version, "compile", License.Commercial));
    graph.addEdge(one.id, three.id, new DependencyEdgeValue(one.version, three.version, "compile", License.Commercial));
    graph.addEdge(two.id, four.id, new DependencyEdgeValue(two.version, four.version, "compile", License.Commercial));
    graph.addEdge(three.id, four.id, new DependencyEdgeValue(three.version, four.version, "compile", License.Commercial));

    DependencyGraph graph2 = new DependencyGraph(one);
    graph2.addEdge(three.id, four.id, new DependencyEdgeValue(three.version, four.version, "compile", License.Commercial));
    graph2.addEdge(two.id, four.id, new DependencyEdgeValue(two.version, four.version, "compile", License.Commercial));
    graph2.addEdge(one.id, three.id, new DependencyEdgeValue(one.version, three.version, "compile", License.Commercial));
    graph2.addEdge(one.id, two.id, new DependencyEdgeValue(one.version, two.version, "compile", License.Commercial));

    assertEquals(graph, graph2);
  }
}
