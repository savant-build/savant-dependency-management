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

import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.Version;

import java.util.Objects;

/**
 * This class stores the information for edges between artifacts in the graph.
 *
 * @author Brian Pontarelli
 */
public class DependencyEdgeValue {
  public final Version dependencyVersion;

  public final Version dependentVersion;

  public final License license;

  public final boolean optional;

  public final String type;

  public DependencyEdgeValue(Version dependentVersion, Version dependencyVersion, String type, boolean optional,
                             License license) {
    Objects.requireNonNull(dependentVersion, "DependencyEdgeValue requires a dependentVersion");
    Objects.requireNonNull(dependencyVersion, "DependencyEdgeValue requires a dependencyVersion");
    Objects.requireNonNull(type, "DependencyEdgeValue requires a type");
    Objects.requireNonNull(license, "DependencyEdgeValue requires a license");
    this.dependentVersion = dependentVersion;
    this.dependencyVersion = dependencyVersion;
    this.type = type;
    this.optional = optional;
    this.license = license;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final DependencyEdgeValue that = (DependencyEdgeValue) o;
    return dependencyVersion.equals(that.dependencyVersion) &&
        dependentVersion.equals(that.dependentVersion) &&
        type.equals(that.type) &&
        optional == that.optional &&
        license == that.license;
  }

  @Override
  public int hashCode() {
    int result = dependencyVersion.hashCode();
    result = 31 * result + dependentVersion.hashCode();
    result = 31 * result + type.hashCode();
    return result;
  }

  public String toString() {
    return dependentVersion.toString() + " ---(optional=" + optional + ",type=" + type + ",license=" + license + ")--> " + dependencyVersion.toString();
  }
}
