/*
 * Copyright (c) 2013-2017, Inversoft Inc., All Rights Reserved
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
package org.savantbuild.dep.maven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The necessary information from the POM.
 *
 * @author Brian Pontarelli
 */
public class POM {
  public List<MavenDependency> dependencies = new ArrayList<>();

  public List<MavenDependency> dependenciesDefinitions = new ArrayList<>();

  public String group;

  public String id;

  public List<MavenLicense> licenses = new ArrayList<>();

  public String name;

  public String packaging;

  public POM parent;

  public String parentGroup;

  public String parentId;

  public String parentVersion;

  public Map<String, String> properties = new HashMap<>();

  public String version;

  public POM() {
  }

  public POM(String group, String id, String version) {
    this.group = group;
    this.id = id;
    this.version = version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof POM)) return false;
    final POM pom = (POM) o;
    return Objects.equals(id, pom.id) && Objects.equals(group, pom.group) && Objects.equals(version, pom.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, group, version);
  }

  public List<MavenDependency> resolveAllDependencies() {
    Map<String, String> allProperties = new HashMap<>(properties);
    List<MavenDependency> allDeps = new ArrayList<>(dependencies);
    POM current = parent;
    while (current != null) {
      allDeps.addAll(current.dependencies);
      current.properties.forEach(allProperties::putIfAbsent);
      current.properties.forEach((key, value) -> allProperties.putIfAbsent("parent." + key, value));
      current.properties.forEach((key, value) -> allProperties.putIfAbsent("project.parent." + key, value));
      current = current.parent;
    }

    // Now resolve everything
    for (MavenDependency dep : allDeps) {
      if (dep.optional == null) {
        dep.optional = resolveDependencyOptional(dep);
      }
      dep.optional = MavenTools.replaceProperties(dep.optional, allProperties);

      if (dep.scope == null) {
        dep.scope = resolveDependencyScope(dep);
      }
      dep.scope = MavenTools.replaceProperties(dep.scope, allProperties);

      if (dep.version == null) {
        dep.version = resolveDependencyVersion(dep);
      }
      dep.version = MavenTools.replaceProperties(dep.version, allProperties);
    }

    return allDeps;
  }

  public String resolveDependencyOptional(MavenDependency dependency) {
    Optional<MavenDependency> optional = dependenciesDefinitions.stream().filter((def) -> def.group.equals(dependency.group) && def.id.equals(dependency.id)).findFirst();
    if (!optional.isPresent() && parent != null) {
      return parent.resolveDependencyOptional(dependency);
    }

    return optional.map(mavenArtifact -> mavenArtifact.optional).orElse(null);
  }

  public String resolveDependencyScope(MavenDependency dependency) {
    Optional<MavenDependency> optional = dependenciesDefinitions.stream().filter((def) -> def.group.equals(dependency.group) && def.id.equals(dependency.id)).findFirst();
    if (!optional.isPresent() && parent != null) {
      return parent.resolveDependencyScope(dependency);
    }

    return optional.map(mavenArtifact -> mavenArtifact.scope).orElse(null);
  }

  public String resolveDependencyVersion(MavenDependency dependency) {
    Optional<MavenDependency> optional = dependenciesDefinitions.stream().filter((def) -> def.group.equals(dependency.group) && def.id.equals(dependency.id)).findFirst();
    if (!optional.isPresent() && parent != null) {
      return parent.resolveDependencyVersion(dependency);
    }

    return optional.map(mavenArtifact -> mavenArtifact.version).orElse(null);
  }

  public String toString() {
    return group + ":" + id + ":" + version;
  }
}
