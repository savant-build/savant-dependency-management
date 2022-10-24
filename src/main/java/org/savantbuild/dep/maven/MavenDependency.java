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
package org.savantbuild.dep.maven;

import java.util.Objects;

import org.savantbuild.dep.domain.ReifiedArtifact;

/**
 * Maven artifact.
 *
 * @author Brian Pontarelli
 */
public class MavenDependency extends POM {
  public String classifier;

  public String optional;

  public ReifiedArtifact savantArtifact;

  public String scope;

  public String type;

  public MavenDependency() {
  }

  public MavenDependency(String group, String id, String version) {
    super(group, id, version);
  }

  public MavenDependency(String group, String id, String version, String scope) {
    super(group, id, version);
    this.scope = scope;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MavenDependency)) return false;
    if (!super.equals(o)) return false;
    final MavenDependency that = (MavenDependency) o;
    return Objects.equals(classifier, that.classifier) && Objects.equals(optional, that.optional) &&
        Objects.equals(scope, that.scope) && Objects.equals(type, that.type);
  }

  public String getArtifactName() {
    return id + ((classifier != null && classifier.trim().length() > 0) ? "-" + classifier : "");
  }

  public String getMainFile() {
    return id + "-" + version + ((classifier != null && classifier.trim().length() > 0) ? "-" + classifier : "") + "." + (type == null ? "jar" : type);
  }

  public String getPOM() {
    return id + "-" + version + ".pom";
  }

  public String getSourceFile() {
    return id + "-" + version + ((classifier != null && classifier.trim().length() > 0) ? "-" + classifier : "") + "-sources." + (type == null ? "jar" : type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), classifier, optional, scope, type);
  }

  public String toString() {
    if ((classifier != null && classifier.trim().length() > 0)) {
      return group + ":" + id + ":" + id + "-" + classifier + ":" + version + ":" + (type == null ? "jar" : type) + "[" + scope + "]";
    }
    return super.toString() + ":" + (type == null ? "jar" : type) + "[" + scope + "]";
  }
}
