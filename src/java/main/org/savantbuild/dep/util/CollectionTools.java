/*
 * Copyright (c) 2001-2011, Inversoft, All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.dep.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class provides collection tools.
 *
 * @author Brian Pontarelli
 */
public class CollectionTools {
  /**
   * Puts the given key value pairs into a Map.
   *
   * @param t   The values. Must be even number of values.
   * @param <T> The type.
   * @return The Map.
   */
  public static <T> Map<T, T> map(T... t) {
    if (t.length % 2 != 0) {
      throw new IllegalArgumentException("Must pass in even number of parameters to the map method");
    }

    Map<T, T> map = new HashMap<T, T>();
    for (int i = 0; i < t.length; i = i + 2) {
      map.put(t[i], t[i + 1]);
    }

    return map;
  }

  /**
   * Puts the given values into a Set.
   *
   * @param t   The values. Must be even number of values.
   * @param <T> The type.
   * @return The Map.
   */
  public static <T> Set<T> set(T... t) {
    Set<T> set = new HashSet<T>();
    set.addAll(Arrays.asList(t));
    return set;
  }
}
