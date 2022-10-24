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
package org.savantbuild.dep.domain;

import java.util.Objects;

import org.savantbuild.domain.Version;

import static java.util.Arrays.stream;

/**
 * <p>
 * This class defines an artifact as it exists across all projects, dependencies, etc. This class is the representation
 * of the artifact that is defined by its group, project, name, type and version. This object is also the representation
 * of a dependency between two project's.
 * </p>
 * <p>
 * See the {@link #Artifact(String, boolean)} constructor for String formats of artifacts.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class Artifact {
  public final ArtifactID id;

  public final boolean skipCompatibilityCheck;

  public final Version version;

  /**
   * Constructs an Artifact with the given ID and version.
   *
   * @param id                     The artifact ID (group, project, name, type).
   * @param version                The version of the artifact.
   * @param skipCompatibilityCheck Determines if the compatibility check is skipped for this artifact or not.
   */
  public Artifact(ArtifactID id, Version version, boolean skipCompatibilityCheck) {
    Objects.requireNonNull(id, "Artifacts must have an ArtifactID");
    Objects.requireNonNull(version, "Artifacts must have a Version");

    this.id = id;
    this.skipCompatibilityCheck = skipCompatibilityCheck;
    this.version = version;
  }

  /**
   * <p>
   * Parses the given specification to build an artifact. The currently supported spec formats are:
   * </p>
   * <pre>
   *   group:project:version
   *   group:project:version:type
   *   group:project:name:version:type
   * </pre>
   * <p>
   * Examples:
   * </p>
   * <pre>
   *   org.savantbuild.dep:savant-dependency-management:0.1
   *   org.savantbuild.dep:savant-dependency-management:0.1:jar
   *   org.savantbuild.dep:savant-dependency-management:some-other-artifact:0.1:jar
   * </pre>
   *
   * @param spec                   The spec.
   * @param skipCompatibilityCheck Determines if the compatibility check is skipped for this artifact or not.
   */
  public Artifact(String spec, boolean skipCompatibilityCheck) {
    this.skipCompatibilityCheck = skipCompatibilityCheck;
    String[] parts = spec.split(":");
    if (parts.length < 3) {
      throw new IllegalArgumentException("Invalid artifact specification [" + spec + "]. It must have 3, 4, or 5 parts");
    }

    if (stream(parts).filter(String::isEmpty).count() > 0) {
      throw new IllegalArgumentException("Invalid artifact specification [" + spec + "]. One of the parts is empty (i.e. foo::3.0");
    }

    if (parts.length == 3) {
      id = new ArtifactID(parts[0], parts[1], parts[1], "jar");
      version = new Version(parts[2]);
    } else if (parts.length == 4) {
      id = new ArtifactID(parts[0], parts[1], parts[1], parts[3]);
      version = new Version(parts[2]);
    } else if (parts.length == 5) {
      id = new ArtifactID(parts[0], parts[1], parts[2], parts[4]);
      version = new Version(parts[3]);
    } else {
      throw new IllegalArgumentException("Invalid artifact specification [" + spec + "]. It must have 3, 4, or 5 parts");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !(o instanceof Artifact)) {
      return false;
    }

    Artifact artifact = (Artifact) o;
    return id.equals(artifact.id) && version.equals(artifact.version);
  }

  /**
   * <p>
   * Returns the artifact file name. This does not include any path information at all and would look something like
   * this:
   * </p>
   * <pre>
   * common-collections-2.1.jar
   * </pre>
   *
   * @return The file name.
   */
  public String getArtifactFile() {
    return prefix() + "." + id.type;
  }

  /**
   * <p>
   * Returns the artifact POM file name. This does not include any path information at all and would look something like
   * this:
   * </p>
   * <pre>
   * common-collections-2.1.pom
   * </pre>
   *
   * @return The file name.
   */
  public String getArtifactFilePOM() {
    return prefix() + ".pom";
  }

  /**
   * <p>
   * Returns the artifact MetaData file name. This does not include any path information at all and would look something
   * like this:
   * </p>
   * <pre>
   * common-collections-2.1.jar.amd
   * </pre>
   *
   * @return The MetaData file name.
   */
  public String getArtifactMetaDataFile() {
    return prefix() + "." + id.type + ".amd";
  }

  /**
   * <p>
   * Returns the artifact source file name. This does not include any path information at all and would look something
   * like this:
   * </p>
   * <pre>
   * common-collections-2.1-src.jar
   * </pre>
   *
   * @return The source file name.
   */
  public String getArtifactSourceFile() {
    return prefix() + "-src." + id.type;
  }

  /**
   * <p>
   * Returns the artifact test file name. This does not include any path information at all and would look something
   * like this:
   * </p>
   * <pre>
   * common-collections-test-2.1.jar
   * </pre>
   *
   * @return The file name.
   */
  public String getArtifactTestFile() {
    return id.name + "-test-" + version + "." + id.type;
  }

  /**
   * <p>
   * Returns the artifact test source file name. This does not include any path information at all and would look
   * something like this:
   * </p>
   * <pre>
   * common-collections-test-2.1.jar
   * </pre>
   *
   * @return The file name.
   */
  public String getArtifactTestSourceFile() {
    return id.name + "-test-" + version + "-src." + id.type;
  }

  @Override
  public int hashCode() {
    int result = id.hashCode();
    result = 31 * result + version.hashCode();
    return result;
  }

  /**
   * @return Whether or not the version of this artifact is an integration build version.
   */
  public boolean isIntegrationBuild() {
    return version.isIntegration();
  }

  @Override
  public String toString() {
    return id.group + ":" + id.project + ":" + id.name + ":" + version + ":" + id.type;
  }

  private String prefix() {
    return id.name + "-" + version;
  }
}
