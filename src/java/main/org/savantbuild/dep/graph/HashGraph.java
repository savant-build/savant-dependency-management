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

  public List<GraphLink<T, U>> getInboundLinks(T value) {
    GraphNode<T, U> node = getNode(value);
    if (node == null) {
      return null;
    }

    return node.getInboundLinks();
  }

  public GraphNode<T, U> getNode(T value) {
    return nodes.get(value);
  }

  /**
   * @return Returns all the nodes in the graph.
   */
  public Set<GraphNode<T, U>> getNodes() {
    return new HashSet<>(nodes.values());
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

    // Get the outbound links first and then remove this nodes outbound and inbound links
    List<GraphLink<T, U>> outboundLinks = node.getOutboundLinks();
    clearLinks(node);

    outboundLinks.stream()
                 .filter((link) -> link.destination.getInboundLinks().isEmpty())
                 .forEach((link) -> remove(link.destination.value));
  }

  /**
   * Returns a Set that contains all of the unique artifacts contained in the graph.
   *
   * @return All the artifacts.
   */
  public Set<T> values() {
    return new HashSet<>(nodes.keySet());
  }

  private void clearLinks(GraphNode<T, U> node) {
    List<GraphLink<T, U>> links = node.getOutboundLinks();
    for (GraphLink<T, U> link : links) {
      node.removeLink(link);
      link.destination.getInboundLinks(link.origin).forEach(link.destination::removeLink);
    }

    links = node.getInboundLinks();
    for (GraphLink<T, U> link : links) {
      node.removeLink(link);
      link.origin.getOutboundLinks(link.destination).forEach(link.origin::removeLink);
    }
  }
}
