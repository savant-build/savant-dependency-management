/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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

import java.util.Map;
import java.util.Objects;

import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.Version;

/**
 * This class stores the information for edges between artifacts in the graph.
 *
 * @author Brian Pontarelli
 */
public class DependencyEdgeValue {
  public final Version dependencyVersion;

  public final Version dependentVersion;

  public final Map<License, String> licenses;

  public final String type;

  public DependencyEdgeValue(Version dependentVersion, Version dependencyVersion, String type, Map<License, String> licenses) {
    Objects.requireNonNull(dependentVersion, "DependencyEdgeValue requires a dependentVersion");
    Objects.requireNonNull(dependencyVersion, "DependencyEdgeValue requires a dependencyVersion");
    Objects.requireNonNull(type, "DependencyEdgeValue requires a type");
    Objects.requireNonNull(licenses, "DependencyEdgeValue requires a license");
    this.dependentVersion = dependentVersion;
    this.dependencyVersion = dependencyVersion;
    this.type = type;
    this.licenses = licenses;
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
        licenses.equals(that.licenses);
  }

  @Override
  public int hashCode() {
    int result = dependencyVersion.hashCode();
    result = 31 * result + dependentVersion.hashCode();
    result = 31 * result + type.hashCode();
    return result;
  }

  public String toString() {
    return dependentVersion.toString() + " ---(type=" + type + ",licenses=" + licenses.keySet() + ")--> " + dependencyVersion.toString();
  }
}
