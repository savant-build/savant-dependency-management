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

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * <p>
 * This tests the graph.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class HashGraphTest {
  @Test
  public void addGraphNode() throws Exception {
    HashGraph<String, String> graph = new HashGraph<String, String>();
    graph.addNode("foo");
    assertNotNull(graph.getNode("foo"));
    assertNotNull(graph.contains("foo"));
    assertEquals(1, graph.getNodes().size());
    assertEquals(1, graph.values().size());
  }

  @Test
  public void getGraphNode() throws Exception {
    HashGraph<String, String> graph = new HashGraph<String, String>();
    graph.addNode("foo");
    GraphNode<String, String> node = graph.getNode("foo");
    assertNotNull(node);
    assertEquals("foo", node.getValue());
  }

  @Test
  public void addLink() throws Exception {
    HashGraph<String, String> graph = new HashGraph<String, String>();
    GraphNode<String, String> foo = graph.addNode("foo");
    GraphNode<String, String> bar = graph.addNode("bar");
    graph.addLink("foo", "bar", "link");
    GraphLink<String, String> link = foo.getOutboundLink(bar);
    assertNotNull(link);
    assertEquals("link", link.value);

    link = bar.getInboundLink(foo);
    assertNotNull(link);
    assertEquals("link", link.value);
  }

  @Test
  public void getPaths() throws Exception {
    HashGraph<String, String> graph = new HashGraph<String, String>();
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

  @Test
  public void remove() throws Exception {
    HashGraph<String, String> graph = new HashGraph<String, String>();
    graph.addNode("one");
    graph.addNode("one-two");
    graph.addNode("one-three");
    graph.addNode("two");
    graph.addNode("three");
    graph.addNode("four");
    graph.addNode("five");

    graph.addLink("one", "one-two", "link");
    graph.addLink("one-two", "one-three", "link");
    graph.addLink("one", "two", "link");
    graph.addLink("one", "three", "link");
    graph.addLink("two", "three", "link");
    graph.addLink("two", "four", "link");
    graph.addLink("two", "five", "link");
    graph.addLink("four", "five", "link");
    graph.addLink("four", "one-three", "link");

    graph.remove("two");
    assertEquals(4, graph.getNodes().size());
    assertEquals(4, graph.values().size());
    assertNotNull(graph.getNode("one"));
    assertNotNull(graph.getNode("three"));
    assertNotNull(graph.getNode("one-two"));
    assertNotNull(graph.getNode("one-three"));

    assertEquals(2, graph.getNode("one").getOutboundLinks().size());
    assertEquals(0, graph.getNode("one").getInboundLinks().size());
    assertEquals(1, graph.getNode("one-two").getInboundLinks().size());
    assertEquals(1, graph.getNode("one-two").getOutboundLinks().size());
    assertEquals(1, graph.getNode("one-three").getInboundLinks().size());
    assertEquals(0, graph.getNode("one-three").getOutboundLinks().size());
    assertEquals(1, graph.getNode("three").getInboundLinks().size());
    assertEquals(0, graph.getNode("three").getOutboundLinks().size());

    assertEquals("one", graph.getNode("one").getOutboundLink(graph.getNode("one-two")).origin.getValue());
    assertEquals("one-two", graph.getNode("one").getOutboundLink(graph.getNode("one-two")).destination.getValue());
    assertEquals("one", graph.getNode("one-two").getInboundLink(graph.getNode("one")).origin.getValue());
    assertEquals("one-two", graph.getNode("one-two").getInboundLink(graph.getNode("one")).destination.getValue());

    assertEquals("one", graph.getNode("one").getOutboundLink(graph.getNode("three")).origin.getValue());
    assertEquals("three", graph.getNode("one").getOutboundLink(graph.getNode("three")).destination.getValue());
    assertEquals("one", graph.getNode("three").getInboundLink(graph.getNode("one")).origin.getValue());
    assertEquals("three", graph.getNode("three").getInboundLink(graph.getNode("one")).destination.getValue());

    assertEquals("one-two", graph.getNode("one-two").getOutboundLink(graph.getNode("one-three")).origin.getValue());
    assertEquals("one-three", graph.getNode("one-two").getOutboundLink(graph.getNode("one-three")).destination.getValue());
    assertEquals("one-two", graph.getNode("one-three").getInboundLink(graph.getNode("one-two")).origin.getValue());
    assertEquals("one-three", graph.getNode("one-three").getInboundLink(graph.getNode("one-two")).destination.getValue());
  }
}
