/*
 * Copyright (c) 2001-2010, Inversoft, All Rights Reserved
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
package org.savantbuild.dep.domain;

import java.util.Objects;

/**
 * This class is contains the properties that define an artifacts identity. Any two artifacts whose identity match are
 * considered the same artifact. All other properties associated with the artifact usually determine the artifacts
 * variant (such as version).
 *
 * @author Brian Pontarelli
 */
public class ArtifactID {
  public final String group;

  public final String name;

  public final String project;

  public final String type;

  /**
   * Constructs an artifact id, which is composed of a group, project, name, and type
   *
   * @param group   the artifact group
   * @param project the artifact project
   * @param name    the artifact name
   * @param type    the artifact type
   * @throws NullPointerException If any of the arguments are null.
   */
  public ArtifactID(String group, String project, String name, String type) throws NullPointerException {
    Objects.requireNonNull(group, "Artifacts must have a group");
    Objects.requireNonNull(project, "Artifacts must have a project");
    Objects.requireNonNull(name, "Artifacts must have a name");
    Objects.requireNonNull(type, "Artifacts must have a type");
    this.group = group;
    this.project = project == null ? name : project;
    this.name = name == null ? project : name;
    this.type = type == null ? "jar" : type;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ArtifactID that = (ArtifactID) o;
    return group.equals(that.group) && name.equals(that.name) && project.equals(that.project) && type.equals(that.type);
  }

  public int hashCode() {
    int result;
    result = group.hashCode();
    result = 31 * result + project.hashCode();
    result = 31 * result + name.hashCode();
    result = 31 * result + type.hashCode();
    return result;
  }

  public String toString() {
    return group + ":" + project + ":" + name + ":" + type;
  }
}
