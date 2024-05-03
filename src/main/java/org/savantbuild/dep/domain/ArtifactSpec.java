/*
 * Copyright (c) 2024, Inversoft Inc., All Rights Reserved
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

import org.savantbuild.domain.Version;

import static java.util.Arrays.stream;

public class ArtifactSpec {
  public final ArtifactID id;

  public final Version version;

  public ArtifactSpec(String spec) {
    this(spec, true);
  }

  public ArtifactSpec(String spec, boolean parseVersion) {
    String[] parts = spec.split(":");
    if (parts.length < 3) {
      throw new IllegalArgumentException("Invalid artifact specification [" + spec + "]. It must have 3, 4, or 5 parts");
    }

    if (stream(parts).anyMatch(String::isEmpty)) {
      throw new IllegalArgumentException("Invalid artifact specification [" + spec + "]. One of the parts is empty (i.e. foo::3.0");
    }

    String draftVersion;

    if (parts.length == 3) {
      id = new ArtifactID(parts[0], parts[1], parts[1], "jar");
      draftVersion = parts[2];
    } else if (parts.length == 4) {
      id = new ArtifactID(parts[0], parts[1], parts[1], parts[3]);
      draftVersion = parts[2];
    } else if (parts.length == 5) {
      id = new ArtifactID(parts[0], parts[1], parts[2], parts[4]);
      draftVersion = parts[3];
    } else {
      throw new IllegalArgumentException("Invalid artifact specification [" + spec + "]. It must have 3, 4, or 5 parts");
    }
    version = parseVersion ? new Version(draftVersion) : null;
  }
}
