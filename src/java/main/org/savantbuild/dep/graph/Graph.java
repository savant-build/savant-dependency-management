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
import java.util.Set;

/**
 * This interface defines the generic graph data structure.
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
 * The important point about Graphs is that implementations don't need to enforce a top level node that controls the
 * entire structure like trees do. Instead, implementations can choose to have the graph store all of the nodes and the
 * connections between them allowing for direct access to any node. These implementations should define how the direct
 * access is managed and whether or not duplicate nodes can be stored.
 * <p/>
 * <h3>Generics</h3>
 * <p/>
 * There are two generics for a Graph. The first variable T is the content of the nodes themselves. Each node can stored
 * a single value. The second generic is the value that can be associated with the Link between nodes. This is carried
 * throughout the entire graph structure making it very strongly typed.
 *
 * @author Brian Pontarelli
 */
public interface Graph<T, U> {
  /**
   * Adds a link between the node whose value is the origin value given and the node whose value is the destination
   * value given. This method works well for implementations that only allow values to exist once.
   * <p/>
   * If there are no nodes for the given value, this method should create nodes for each value and then create a link
   * between them. This reduces the work required to link values.
   *
   * @param origin      The origin value that may or may not exist in the graph.
   * @param destination The destination value that may or may not exist in the graph.
   * @param linkValue   The value to associate with the link.
   */
  void addLink(T origin, T destination, U linkValue);

  /**
   * Adds a link between the two given nodes. If the nodes have not yet been added to the graph then they must first be
   * added to the graph prior to creating the link.
   *
   * @param origin      The origin node.
   * @param destination The destination node.
   * @param linkValue   The value to associate with the link.
   */
  void addLink(GraphNode<T, U> origin, GraphNode<T, U> destination, U linkValue);

  /**
   * Adds the given value to the graph in a new node. If the implementation does allow duplicates this will create a new
   * node. If the implementation does not allow duplicates, this will replace any existing node value but will retain
   * the node itself (more like an updateNode).
   *
   * @param value The value to add to an existing or new node.
   * @return The Node created or updated.
   */
  GraphNode<T, U> addNode(T value);

  /**
   * Determines if the Graph contains the given value or not.
   *
   * @param value The value.
   * @return True if the value is in the graph, false otherwise.
   */
  boolean contains(T value);

  /**
   * Returns a list of all the inbound links for the node whose value is given. This locates the first node with the
   * value.
   *
   * @param value The value to find the links for.
   * @return The links or an empty list if the node exists and has no links or null if the node does not exist.
   */
  List<GraphLink<T, U>> getInboundLinks(T value);

  /**
   * Retrieves the graph node whose value is equal to the given value. If the implementation allows duplicates, this
   * will return the first node found. If the implementation does not allow duplicates, this will return the only node
   * with the value.
   *
   * @param value The value to find the node for.
   * @return The node or null if there isn't one.
   */
  GraphNode<T, U> getNode(T value);

  /**
   * Returns a list of all the outbound links for the node whose value is given. This locates the first node with the
   * value.
   *
   * @param value The value to find the links for.
   * @return The links or an empty list if the node exists and has no links or null if the node does not exist.
   */
  List<GraphLink<T, U>> getOutboundLinks(T value);

  /**
   * Determines the path from the given origin value to given destination value.
   *
   * @param origin      The origin value.
   * @param destination The destination value.
   * @return A list of all the paths between the two nodes or an empty list if there are none or null if either of the
   *         nodes don't exist.
   */
  List<GraphPath<T>> getPaths(T origin, T destination);

  /**
   * Returns a Set that contains all of the unique values contained in the graph.
   *
   * @return All the values.
   */
  Set<T> values();
}
