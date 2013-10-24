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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is used to construct and manage graph structures. This is a simple class that makes the navigation and
 * usage of Graphs simple and accessible.
 * <p/>
 * <h3>Graphs</h3>
 * <p/>
 * Graphs are simple structures that model nodes with any number of connections between nodes. The connections are
 * bi-directional and are called Links. A two node graph with a link between the nodes looks like this:
 * <p/>
 * <pre>
 * node1 <---> node2
 * </pre>
 * <p/>
 * The important point about Graphs is that they don't enforce a top level node that controls the entire structure like
 * trees do. Instead, the graph has access to all nodes and the connections between them. This makes finding a Node easy
 * and then traversing the graph also easy.
 * <p/>
 * <h3>Generics</h3>
 * <p/>
 * There are two generics for a Graph. The first variable T is the content of the nodes themselves. Each node can stored
 * a single value. The second generic is the value that can be associated with the Link between nodes. This is carried
 * thoroughout the entire graph structure making it very strongly typed.
 * <p/>
 * <h3>Internals</h3>
 * <p/>
 * It is important to understand how the Graph works internally. Nodes are stored in a Map whose key is the value for
 * the node. If the graph is storing Strings then only a single node can exist with the value <em>foo</em>. This means
 * that the graph does not allow duplicates. Therefore it would be impossible to have two nodes whose values are
 * <em>foo</em> with different links. The key of the Map is a {@link org.savantbuild.dep.graph.GraphNode} object. The
 * node stores the value as well as all the links.
 * <p/>
 * <h3>Node values</h3>
 * <p/>
 * Due to the implementation of the graph, all values must have a good equal and hashcode implementation. Using the
 * object identity is allowed and will then manage the graph based on the heap location of the value objects (pointers
 * are used for the java.lang.Object version of equals and hashcode).
 * <p/>
 * <h3>Thread safety</h3>
 * <p/>
 * The Graph is not thread safe. Classes must synchronize on the graph instance in order to protect multi-threaded use.
 *
 * @author Brian Pontarelli
 */
public class HashGraph<T, U> implements Graph<T, U> {
  private final Map<T, GraphNode<T, U>> nodes = new HashMap<>();

  public GraphNode<T, U> addNode(T value) {
    GraphNode<T, U> node = nodes.get(value);
    if (node == null) {
      node = new GraphNode<>(value);
      nodes.put(value, node);
    } else {
      node.value = value;
    }

    return node;
  }

  public void addLink(T origin, T destination, U linkValue) {
    GraphNode<T, U> originNode = addNode(origin);
    GraphNode<T, U> destinationNode = addNode(destination);

    originNode.addOutboundLink(destinationNode, linkValue);
    destinationNode.addInboundLink(originNode, linkValue);
  }

  public void addLink(GraphNode<T, U> origin, GraphNode<T, U> destination, U linkValue) {
    nodes.put(origin.value, origin);
    nodes.put(destination.value, destination);

    origin.addOutboundLink(destination, linkValue);
    destination.addInboundLink(origin, linkValue);
  }

  public boolean contains(T value) {
    return nodes.containsKey(value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final HashGraph hashGraph = (HashGraph) o;
    return nodes.equals(hashGraph.nodes);
  }

  /**
   * @return Returns all the nodes in the graph.
   */
  public Set<GraphNode<T, U>> getNodes() {
    return new HashSet<>(nodes.values());
  }

  /**
   * Returns a Set that contains all of the unique artifacts contained in the graph.
   *
   * @return All the artifacts.
   */
  public Set<T> values() {
    return new HashSet<>(nodes.keySet());
  }

  public GraphNode<T, U> getNode(T value) {
    return nodes.get(value);
  }

  public List<GraphLink<T, U>> getInboundLinks(T value) {
    GraphNode<T, U> node = getNode(value);
    if (node == null) {
      return null;
    }

    return node.getInboundLinks();
  }

  public List<GraphLink<T, U>> getOutboundLinks(T value) {
    GraphNode<T, U> node = getNode(value);
    if (node == null) {
      return null;
    }

    return node.getOutboundLinks();
  }

  public List<GraphPath<T>> getPaths(T origin, T destination) {
    GraphNode<T, U> originNode = getNode(origin);
    if (originNode == null) {
      return null;
    }

    GraphNode<T, U> destNode = getNode(destination);
    if (destNode == null) {
      return null;
    }

    List<GraphLink<T, U>> originLinks = originNode.getOutboundLinks();
    List<GraphPath<T>> list = new ArrayList<>();
    for (GraphLink<T, U> link : originLinks) {
      if (link.destination.value.equals(destination)) {
        GraphPath<T> path = new GraphPath<>();
        path.addToPath(origin);
        path.addToPath(destination);
        list.add(path);
      } else {
        List<GraphPath<T>> paths = getPaths(link.destination.value, destination);
        for (GraphPath<T> path : paths) {
          path.addToPathHead(origin);
          list.add(path);
        }
      }
    }

    return list;
  }

  @Override
  public int hashCode() {
    return nodes.hashCode();
  }

  public void remove(T value) throws CyclicException {
    GraphNode<T, U> node = nodes.remove(value);
    if (node == null) {
      return;
    }

    // Create the sub graph and add the removal node to it
    Set<GraphNode<T, U>> subGraph = new HashSet<>();
    subGraph.add(node);

    // Grab the sub-graph
    Set<GraphNode<T, U>> visited = new HashSet<>();
    try {
      recurseAdd(node, subGraph, visited);
    } catch (CyclicException e) {
      throw new CyclicException("Cyclic graph [" + e.getMessage() + "]");
    }

    // Recursively remove sub-graphs (depth first) that have no outbounds and all inbounds
    // are marked.
    nodeLoop:
    for (GraphNode<T, U> graphNode : subGraph) {
      List<GraphLink<T, U>> links = graphNode.getInboundLinks();
      for (GraphLink<T, U> link : links) {

        // If the node has a connection from the outside world, don't clear it out
        if (!subGraph.contains(link.origin) || !subGraph.contains(link.destination)) {
          continue nodeLoop;
        }
      }

      // If all the links are clear, KILL IT! HAHAHAHAHA
      nodes.remove(graphNode.value);
      clearLinks(graphNode);
    }

    // Just in case the removal node is reachable, we need to clear out its links
    clearLinks(node);
  }

  public void removeLink(T origin, T destination, U linkValue) {
    GraphNode<T, U> originNode = addNode(origin);
    GraphNode<T, U> destinationNode = addNode(destination);
    removeLink(originNode, destinationNode, linkValue);
  }

  public void removeLink(GraphNode<T, U> origin, GraphNode<T, U> destination, U linkValue) {
    // Add the nodes to the graph, even if they aren't there already so that we don't blow up
    nodes.put(origin.value, origin);
    nodes.put(destination.value, destination);

    // Remove the links from the two nodes.
    origin.removeOutboundLink(destination, linkValue);
    destination.removeInboundLink(origin, linkValue);
  }

  private void clearLinks(GraphNode<T, U> node) {
    List<GraphLink<T, U>> links = node.getOutboundLinks();
    for (GraphLink<T, U> link : links) {
      node.removeLink(link);
      GraphLink<T, U> inbound = link.destination.getInboundLink(link.origin);
      link.destination.removeLink(inbound);
    }

    links = node.getInboundLinks();
    for (GraphLink<T, U> link : links) {
      node.removeLink(link);
      GraphLink<T, U> outbound = link.origin.getOutboundLink(link.destination);
      link.origin.removeLink(outbound);
    }
  }

  private void recurseAdd(GraphNode<T, U> node, Set<GraphNode<T, U>> result, Set<GraphNode<T, U>> visited)
  throws CyclicException {
    if (visited.contains(node)) {
      // Eeck, cyclic
      throw new CyclicException(node.value.toString());
    }

    // Depth first
    List<GraphLink<T, U>> links = node.getOutboundLinks();
    for (GraphLink<T, U> link : links) {
      result.add(link.destination);
      try {
        recurseAdd(link.destination, result, visited);
      } catch (CyclicException e) {
        throw new CyclicException(node.value.toString() + "->" + e.getMessage());
      }
    }
  }
}
