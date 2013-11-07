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
package org.savantbuild.dep.util;

/**
 * Stores the min and max of a comparable.
 *
 * @author Brian Pontarelli
 */
public class MinMax<T extends Comparable<T>> {
  public T min;
  public T max;

  public void add(T t) {
    if (min == null || min.compareTo(t) > 0) {
      min = t;
    }

    if (max == null || max.compareTo(t) < 0) {
      max = t;
    }
  }

  @Override
  public String toString() {
    return "MinMax{" +
        "min=" + min +
        ", max=" + max +
        "} " + super.toString();
  }
}
