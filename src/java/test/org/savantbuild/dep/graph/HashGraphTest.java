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

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * This tests the graph.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class HashGraphTest {
  @Test
  public void addGraphNode() throws Exception {
    HashGraph<String, String> graph = new HashGraph<>();
    graph.addNode("foo");
    assertNotNull(graph.getNode("foo"));
    assertNotNull(graph.contains("foo"));
    assertEquals(1, graph.getNodes().size());
    assertEquals(1, graph.values().size());
  }

  @Test
  public void addLink() throws Exception {
    HashGraph<String, String> graph = new HashGraph<>();
    GraphNode<String, String> foo = graph.addNode("foo");
    GraphNode<String, String> bar = graph.addNode("bar");
    graph.addLink("foo", "bar", "link");
    List<GraphLink<String, String>> links = foo.getOutboundLinks(bar);
    assertEquals(links.size(), 1);
    assertEquals("link", links.get(0).value);

    links = bar.getInboundLinks(foo);
    assertEquals(links.size(), 1);
    assertEquals("link", links.get(0).value);
  }

  @Test
  public void getGraphNode() throws Exception {
    HashGraph<String, String> graph = new HashGraph<>();
    graph.addNode("foo");
    GraphNode<String, String> node = graph.getNode("foo");
    assertNotNull(node);
    assertEquals("foo", node.value);
  }

  @Test
  public void getPaths() throws Exception {
    HashGraph<String, String> graph = new HashGraph<>();
    graph.addNode("one");
    graph.addNode("two");
    graph.addNode("three");
    graph.addNode("four");

    graph.addLink("one", "two", "link");
    graph.addLink("two", "three", "link");
    graph.addLink("three", "four", "link");
    graph.addLink("one", "four", "link");
    graph.addLink("two", "four", "link");

    List<GraphPath<String>> paths = graph.getPaths("one", "four");
    assertEquals(3, paths.size());
    assertEquals(4, paths.get(0).getPath().size());
    assertEquals(3, paths.get(1).getPath().size());
    assertEquals(2, paths.get(2).getPath().size());

    System.out.println(paths.get(0).getPath());
    System.out.println(paths.get(1).getPath());
    System.out.println(paths.get(2).getPath());
  }

  /**
   * Graph:
   * <p/>
   * <pre>
   *   one --> one-two --> one-three --|
   *       |                 /\        |
   *       |                 |         |
   *       |-> two ------> four        |
   *       |    |           |          |
   *       |    |          \/          |
   *       |    |-------> five         |
   *       |    |                      |
   *       |    |-------> six <--------|
   *       |    |
   *       |   \/
   *       |-> three
   * </pre>
   * <p/>
   * Potential sub-graph to prune includes (in traversal order with duplicates):
   * <p/>
   * <pre>
   *   two
   *     four
   *       one-three
   *         six
   *       five
   *     five
   *     six
   *     three
   * </pre>
   *
   * @throws Exception
   */
  @Test
  public void remove() throws Exception {
    HashGraph<String, String> graph = new HashGraph<>();
    graph.addNode("one");
    graph.addNode("one-two");
    graph.addNode("one-three");
    graph.addNode("two");
    graph.addNode("three");
    graph.addNode("four");
    graph.addNode("five");
    graph.addNode("six");

    graph.addLink("one", "one-two", "link");
    graph.addLink("one-two", "one-three", "link");
    graph.addLink("one-three", "six", "link");
    graph.addLink("one", "two", "link");
    graph.addLink("one", "three", "link");
    graph.addLink("two", "three", "link");
    graph.addLink("two", "four", "link");
    graph.addLink("two", "five", "link");
    graph.addLink("two", "six", "link");
    graph.addLink("four", "five", "link");
    graph.addLink("four", "one-three", "link");

    graph.remove("two");
    assertEquals(graph.getNodes().size(), 5);
    assertEquals(graph.values().size(), 5);
    assertNotNull(graph.getNode("one"));
    assertNotNull(graph.getNode("one-two"));
    assertNotNull(graph.getNode("one-three"));
    assertNotNull(graph.getNode("three"));
    assertNotNull(graph.getNode("six"));

    assertEquals(graph.getNode("one").getOutboundLinks().size(), 2);
    assertEquals(graph.getNode("one").getInboundLinks().size(), 0);
    assertEquals(graph.getNode("one-two").getInboundLinks().size(), 1);
    assertEquals(graph.getNode("one-two").getOutboundLinks().size(), 1);
    assertEquals(graph.getNode("one-three").getInboundLinks().size(), 1);
    assertEquals(graph.getNode("one-three").getOutboundLinks().size(), 1);
    assertEquals(graph.getNode("three").getInboundLinks().size(), 1);
    assertEquals(graph.getNode("three").getOutboundLinks().size(), 0);

    assertEquals(graph.getNode("one").getOutboundLinks(graph.getNode("one-two")).get(0).origin.value, "one");
    assertEquals(graph.getNode("one").getOutboundLinks(graph.getNode("one-two")).get(0).destination.value, "one-two");
    assertEquals(graph.getNode("one-two").getInboundLinks(graph.getNode("one")).get(0).origin.value, "one");
    assertEquals(graph.getNode("one-two").getInboundLinks(graph.getNode("one")).get(0).destination.value, "one-two");

    assertEquals(graph.getNode("one").getOutboundLinks(graph.getNode("three")).get(0).origin.value, "one");
    assertEquals(graph.getNode("one").getOutboundLinks(graph.getNode("three")).get(0).destination.value, "three");
    assertEquals(graph.getNode("three").getInboundLinks(graph.getNode("one")).get(0).origin.value, "one");
    assertEquals(graph.getNode("three").getInboundLinks(graph.getNode("one")).get(0).destination.value, "three");

    assertEquals(graph.getNode("one-two").getOutboundLinks(graph.getNode("one-three")).get(0).origin.value, "one-two");
    assertEquals(graph.getNode("one-two").getOutboundLinks(graph.getNode("one-three")).get(0).destination.value, "one-three");
    assertEquals(graph.getNode("one-three").getInboundLinks(graph.getNode("one-two")).get(0).origin.value, "one-two");
    assertEquals(graph.getNode("one-three").getInboundLinks(graph.getNode("one-two")).get(0).destination.value, "one-three");
    assertEquals(graph.getNode("one-three").getOutboundLinks(graph.getNode("six")).get(0).origin.value, "one-three");
    assertEquals(graph.getNode("one-three").getOutboundLinks(graph.getNode("six")).get(0).destination.value, "six");
  }
}
