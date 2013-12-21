/*
 * Copyright (c) 2001-2013, Inversoft, All Rights Reserved
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

/**
 * An exception that is thrown when a Version string cannot be parsed.
 *
 * @author Brian Pontarelli
 */
public class CompatibilityException extends RuntimeException {
  public final ArtifactID artifactID;
  public final Version min;
  public final Version max;

  public CompatibilityException(ArtifactID artifactID, Version min, Version max) {
    this.artifactID = artifactID;
    this.min = min;
    this.max = max;
  }

  @Override
  public String toString() {
    return "The artifact [" + artifactID + "] has incompatible versions in your dependencies. The versions are [" + min + ", " + max + "]";
  }
}
