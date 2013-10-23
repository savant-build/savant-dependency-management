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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * This class represents a directional path from one node in a graph to another.
 *
 * @author Brian Pontarelli
 */
public class GraphPath<T> {
  private LinkedList<T> path = new LinkedList<>();

  public void addToPath(T t) {
    path.addLast(t);
  }

  public void addToPathHead(T t) {
    path.addFirst(t);
  }

  public List<T> getPath() {
    return Collections.unmodifiableList(path);
  }

  public void removeLast() {
    path.removeLast();
  }
}
