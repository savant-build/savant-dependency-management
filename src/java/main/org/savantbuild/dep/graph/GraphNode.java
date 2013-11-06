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
import java.util.List;

/**
 * This class is a single node in the artifact graph.
 *
 * @author Brian Pontarelli
 */
public class GraphNode<T, U> {
  private final List<GraphLink<T, U>> inbound = new ArrayList<>();

  private final List<GraphLink<T, U>> outbound = new ArrayList<>();

  public T value;

  public GraphNode(T value) {
    this.value = value;
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final GraphNode<T, U> that = (GraphNode<T, U>) o;
    if (!value.equals(that.value)) {
      return false;
    }

    // Compare the lists brute force (to avoid nasty Comparable inflection)
    if (inbound.size() != that.inbound.size()) {
      return false;
    }
    if (outbound.size() != that.outbound.size()) {
      return false;
    }
    for (GraphLink<T, U> myLink : outbound) {
      boolean matches = that.outbound.stream().anyMatch(
          (GraphLink<T, U> theirLink) -> myLink.value.equals(theirLink.value) && myLink.destination.value.equals(theirLink.destination.value)
      );
      if (!matches) {
        return false;
      }
    }

    return true;
  }

  public GraphLink<T, U> getInboundLink(GraphNode<T, U> origin) {
    for (GraphLink<T, U> link : inbound) {
      if (link.origin.equals(origin)) {
        return link;
      }
    }

    return null;
  }

  public List<GraphLink<T, U>> getInboundLinks() {
    return new ArrayList<>(inbound);
  }

  public GraphLink<T, U> getOutboundLink(GraphNode<T, U> destination) {
    for (GraphLink<T, U> link : outbound) {
      if (link.destination.equals(destination)) {
        return link;
      }
    }

    return null;
  }

  public List<GraphLink<T, U>> getOutboundLinks() {
    return new ArrayList<>(outbound);
  }

  @Override
  public int hashCode() {
    int result = inbound.hashCode();
    result = 31 * result + outbound.hashCode();
    result = 31 * result + value.hashCode();
    return result;
//    return value.hashCode();
  }

  /**
   * Removes all inbound links for this node which contain the given value.
   *
   * @param origin    The origin node of the inbound link to remove.
   * @param linkValue The link value to remove.
   * @return True if the link was removed, false if it doesn't exist.
   */
  public boolean removeInboundLink(GraphNode<T, U> origin, U linkValue) {
    return inbound.removeIf((link) -> link.origin.value.equals(origin.value) && link.value.equals(linkValue));
  }

  /**
   * Removes all outbound links for this node which contain the given value.
   *
   * @param destination The destination node of the outbound link to remove.
   * @param linkValue   The link value to remove.
   * @return True if the link was removed, false if it doesn't exist.
   */
  public boolean removeOutboundLink(GraphNode<T, U> destination, U linkValue) {
    return outbound.removeIf((
        link) -> link.destination.value.equals(destination.value) && link.value.equals(linkValue));
  }

  /**
   * @return The toString of the node's value.
   */
  public String toString() {
    return value.toString();
  }

  void addInboundLink(GraphNode<T, U> origin, U linkValue) {
    GraphLink<T, U> link = new GraphLink<>(origin, this, linkValue);
    inbound.add(link);
  }

  void addOutboundLink(GraphNode<T, U> destination, U linkValue) {
    GraphLink<T, U> link = new GraphLink<>(this, destination, linkValue);
    outbound.add(link);
  }

  void removeLink(GraphLink<T, U> link) {
    outbound.remove(link);
    inbound.remove(link);
  }
}
