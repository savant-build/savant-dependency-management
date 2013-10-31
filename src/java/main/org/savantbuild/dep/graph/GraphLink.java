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

/**
 * This class is the graph link between nodes in the graph.
 *
 * @author Brian Pontarelli
 */
public class GraphLink<T, U> {
  public final GraphNode<T, U> destination;

  public final GraphNode<T, U> origin;

  public final U value;

  public GraphLink(GraphNode<T, U> origin, GraphNode<T, U> destination, U value) {
    this.origin = origin;
    this.destination = destination;
    this.value = value;
  }

  public GraphNode<T, U> getDestination() {
    return destination;
  }

  public GraphNode<T, U> getOrigin() {
    return origin;
  }

  public U getValue() {
    return value;
  }
}
