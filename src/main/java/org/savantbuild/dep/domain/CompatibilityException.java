/*
 * Copyright (c) 2001-2013, Inversoft, All Rights Reserved
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
package org.savantbuild.dep.domain;

import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.DependencyGraph.Dependency;
import org.savantbuild.domain.Version;

/**
 * An exception that is thrown when a Version string cannot be parsed.
 *
 * @author Brian Pontarelli
 */
public class CompatibilityException extends RuntimeException {
  public final DependencyGraph graph;
  public final Dependency dependency;
  public final Version min;
  public final Version max;

  public CompatibilityException(DependencyGraph graph, Dependency dependency, Version min, Version max) {
    super("The artifact [" + dependency.id + "] has incompatible versions in your dependencies. The versions are [" + min + ", " + max + "]");
    this.graph = graph;
    this.dependency = dependency;
    this.min = min;
    this.max = max;
  }
}
