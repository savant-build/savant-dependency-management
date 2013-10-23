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
package org.savantbuild.dep.version;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is a singleton that stores the {@link VersionComparator} instances.
 *
 * @author Brian Pontarelli
 */
public class VersionComparatorRegistry {
  private static final Map<String, VersionComparator> checkers = new HashMap<>();

  private static final String DEFAULT_TYPE = "DEFAULT_VERSION_COMPARATOR";

  static {
    checkers.put("minor", new MinorVersionComparator());
    checkers.put("major", new MajorVersionComparator());
    checkers.put("patch", new PatchVersionComparator());
    checkers.put("identical", new IdenticalVersionComparator());
    checkers.put(DEFAULT_TYPE, new MinorVersionComparator());
  }

  /**
   * Adds the given version comparator that will be used to verify compatibility for artifacts who specify the given
   * compatibility type string.
   *
   * @param type    The type of compatibility that this checkers will be used for.
   * @param checker The checkers.
   */
  public static void add(String type, VersionComparator checker) {
    checkers.put(type, checker);
  }

  /**
   * Retrieves a version comparator for the given compatibility type.
   *
   * @param type The type of the compatibility to get the comparator for.
   * @return The comparator and never null. If no checkers was registered the default checkers is used and the default
   *         checkers is always not null because initially it is the {@link IdenticalVersionComparator}.
   */
  public static VersionComparator lookup(String type) {
    VersionComparator checker;
    if (type == null) {
      checker = checkers.get(DEFAULT_TYPE);
    } else {
      checker = checkers.get(type);
      if (checker == null) {
        throw new IllegalArgumentException("Invalid compatibility type [" + type + "]");
      }
    }

    return checker;
  }

  /**
   * Resets the default comparator to the original value (currently minor).
   */
  public static void resetDefault() {
    checkers.put(DEFAULT_TYPE, new MinorVersionComparator());
  }

  /**
   * Sets the default comparator used when a comparator is not found for a specific type string.
   *
   * @param comparator The default comparator.
   * @throws IllegalArgumentException If the comparator given is null.
   */
  public static void setDefault(VersionComparator comparator) throws IllegalArgumentException {
    if (comparator == null) {
      throw new IllegalArgumentException("Default checkers cannot be null");
    }

    checkers.put(DEFAULT_TYPE, comparator);
  }
}
