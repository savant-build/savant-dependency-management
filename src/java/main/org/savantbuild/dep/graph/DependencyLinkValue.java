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

import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.Dependency;
import org.savantbuild.dep.domain.Version;

/**
 * This class stores the information for links between artifacts in the graph.
 *
 * @author Brian Pontarelli
 */
public class DependencyLinkValue {
  public final Version dependencyVersion;

  public final Version dependentVersion;

  public final boolean optional;

  public final String type;

  public DependencyLinkValue(Version dependentVersion, Version dependencyVersion, String type, boolean optional) {
    this.dependentVersion = dependentVersion;
    this.dependencyVersion = dependencyVersion;
    this.type = type;
    this.optional = optional;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final DependencyLinkValue that = (DependencyLinkValue) o;
    return dependencyVersion.equals(that.dependencyVersion) && dependentVersion.equals(that.dependentVersion) &&
        type.equals(that.type) && optional == that.optional;
  }

  @Override
  public int hashCode() {
    int result = dependencyVersion.hashCode();
    result = 31 * result + dependentVersion.hashCode();
    result = 31 * result + type.hashCode();
    return result;
  }

  public Dependency toDependency(ArtifactID id) {
    return new Dependency(id, dependencyVersion, optional);
  }
}
