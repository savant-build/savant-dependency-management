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
package org.savantbuild.dep.domain;

import java.nio.file.Path;

/**
 * This class is a publishable artifact for a project. This is similar to an artifact, but doesn't have the group,
 * project and version, since those are controlled by the project and also has a file reference and dependencies
 * reference.
 *
 * @author Brian Pontarelli
 */
public class Publication {
  public AbstractArtifact artifact;

  public Dependencies dependencies;

  public Path file;

  public Path sourceFile;

  public Publication() {
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final Publication that = (Publication) o;
    return artifact.equals(that.artifact) &&
        file.equals(that.file) &&
        (dependencies == null ? that.dependencies == null : dependencies.equals(that.dependencies));
  }

  @Override
  public int hashCode() {
    return artifact.hashCode();
  }
}
