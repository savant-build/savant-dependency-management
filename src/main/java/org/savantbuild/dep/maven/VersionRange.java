/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.savantbuild.dep.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.savantbuild.domain.VersionException;

/**
 * Model a Maven version range.
 * <p>
 * @see <a
 * href="https://github.com/apache/maven/blob/39be5ef43a5079fc7573b74ac487c213f0606ed2/compat/maven-artifact/src/main/java/org/apache/maven/artifact/versioning/VersionRange.java">
 * src/main/java/org/apache/maven/artifact/versioning/VersionRange.java
 * </a>
 *
 * @author Daniel DeGroff
 */
public class VersionRange {
  private final ArtifactVersion recommendedVersion;

  private final List<VersionRestriction> restrictions;

  private VersionRange(ArtifactVersion recommendedVersion, List<VersionRestriction> restrictions) {
    this.recommendedVersion = recommendedVersion;
    this.restrictions = restrictions;
  }

  /**
   * <p>
   * Create a version range from a string representation
   * </p>
   * Some spec examples are:
   * <ul>
   * <li><code>1.0</code> Version 1.0 as a recommended version</li>
   * <li><code>[1.0]</code> Version 1.0 explicitly only</li>
   * <li><code>[1.0,2.0)</code> Versions 1.0 (included) to 2.0 (not included)</li>
   * <li><code>[1.0,2.0]</code> Versions 1.0 to 2.0 (both included)</li>
   * <li><code>[1.5,)</code> Versions 1.5 and higher</li>
   * <li><code>(,1.0],[1.2,)</code> Versions up to 1.0 (included) and 1.2 or higher</li>
   * </ul>
   *
   * @param spec string representation of a version or version range
   * @return a new {@link VersionRange} object that represents the spec
   * @throws VersionException if invalid version specification
   */
  public static VersionRange parse(String spec) {
    Objects.requireNonNull(spec);
    List<VersionRestriction> restrictions = new ArrayList<>();
    String process = spec;
    ArtifactVersion version = null;
    ArtifactVersion upperBound = null;
    ArtifactVersion lowerBound = null;

    while (process.startsWith("[") || process.startsWith("(")) {
      int index1 = process.indexOf(')');
      int index2 = process.indexOf(']');

      int index = index2;
      if (index2 < 0 || index1 < index2) {
        if (index1 >= 0) {
          index = index1;
        }
      }

      if (index < 0) {
        throw new VersionException("Unbounded range: " + spec);
      }

      VersionRestriction restriction = parseRestriction(process.substring(0, index + 1));
      if (lowerBound == null) {
        lowerBound = restriction.getLowerBound();
      }
      if (upperBound != null) {
        if (restriction.getLowerBound() == null
            || restriction.getLowerBound().compareTo(upperBound) < 0) {
          throw new VersionException("Ranges overlap: " + spec);
        }
      }
      restrictions.add(restriction);
      upperBound = restriction.getUpperBound();

      process = process.substring(index + 1).trim();

      if (process.startsWith(",")) {
        process = process.substring(1).trim();
      }
    }

    if (!process.isEmpty()) {
      if (!restrictions.isEmpty()) {
        throw new VersionException("Only fully-qualified sets allowed in multiple set scenario: " + spec);
      } else {
        version = new ArtifactVersion(process);
        restrictions.add(VersionRestriction.EVERYTHING);
      }
    }


    return new VersionRange(version, restrictions);
  }

  private static VersionRestriction parseRestriction(String spec) throws VersionException {
    boolean lowerBoundInclusive = spec.startsWith("[");
    boolean upperBoundInclusive = spec.endsWith("]");

    String process = spec.substring(1, spec.length() - 1).trim();

    VersionRestriction restriction;

    int index = process.indexOf(',');

    if (index < 0) {
      if (!lowerBoundInclusive || !upperBoundInclusive) {
        throw new VersionException("Single version must be surrounded by []: " + spec);
      }

      ArtifactVersion version = new ArtifactVersion(process);

      restriction = new VersionRestriction(version, lowerBoundInclusive, version, upperBoundInclusive);
    } else {
      String lowerBound = process.substring(0, index).trim();
      String upperBound = process.substring(index + 1).trim();

      ArtifactVersion lowerVersion = null;
      if (!lowerBound.isEmpty()) {
        lowerVersion = new ArtifactVersion(lowerBound);
      }
      ArtifactVersion upperVersion = null;
      if (!upperBound.isEmpty()) {
        upperVersion = new ArtifactVersion(upperBound);
      }

      if (upperVersion != null && lowerVersion != null) {
        int result = upperVersion.compareTo(lowerVersion);
        if (result < 0 || (result == 0 && (!lowerBoundInclusive || !upperBoundInclusive))) {
          throw new VersionException("Range defies version ordering: " + spec);
        }
      }

      restriction = new VersionRestriction(lowerVersion, lowerBoundInclusive, upperVersion, upperBoundInclusive);
    }

    return restriction;
  }

  public ArtifactVersion getRecommendedVersion() {
    return recommendedVersion;
  }

  public List<VersionRestriction> getRestrictions() {
    return restrictions;
  }
}
