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

/**
 * @author Daniel DeGroff
 * @see <a
 *     href="https://github.com/apache/maven/blob/39be5ef43a5079fc7573b74ac487c213f0606ed2/compat/maven-artifact/src/main/java/org/apache/maven/artifact/versioning/Restriction.java">src/main/java/org/apache/maven/artifact/versioning/Restriction.java</a>
 */
public class VersionRestriction {
  public static final VersionRestriction EVERYTHING = new VersionRestriction(null, false, null, false);

  private final ArtifactVersion lowerBound;

  private final boolean lowerBoundInclusive;

  private final ArtifactVersion upperBound;

  private final boolean upperBoundInclusive;

  public VersionRestriction(ArtifactVersion lowerBound, boolean lowerBoundInclusive, ArtifactVersion upperBound, boolean upperBoundInclusive) {
    this.lowerBound = lowerBound;
    this.lowerBoundInclusive = lowerBoundInclusive;
    this.upperBound = upperBound;
    this.upperBoundInclusive = upperBoundInclusive;
  }

  public boolean containsVersion(ArtifactVersion version) {
    if (lowerBound != null) {
      int comparison = lowerBound.compareTo(version);

      if ((comparison == 0) && !lowerBoundInclusive) {
        return false;
      }
      if (comparison > 0) {
        return false;
      }
    }
    if (upperBound != null) {
      int comparison = upperBound.compareTo(version);

      if ((comparison == 0) && !upperBoundInclusive) {
        return false;
      }
      return comparison >= 0;
    }

    return true;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other instanceof VersionRestriction restriction) {
      if (lowerBound != null) {
        if (!lowerBound.equals(restriction.lowerBound)) {
          return false;
        }
      } else if (restriction.lowerBound != null) {
        return false;
      }

      if (lowerBoundInclusive != restriction.lowerBoundInclusive) {
        return false;
      }

      if (upperBound != null) {
        if (!upperBound.equals(restriction.upperBound)) {
          return false;
        }
      } else if (restriction.upperBound != null) {
        return false;
      }

      return upperBoundInclusive == restriction.upperBoundInclusive;
    } else {
      return false;
    }
  }

  public ArtifactVersion getLowerBound() {
    return lowerBound;
  }

  public ArtifactVersion getUpperBound() {
    return upperBound;
  }

  @Override
  public int hashCode() {
    int result = 13;

    if (lowerBound == null) {
      result += 1;
    } else {
      result += lowerBound.hashCode();
    }

    result *= lowerBoundInclusive ? 1 : 2;

    if (upperBound == null) {
      result -= 3;
    } else {
      result -= upperBound.hashCode();
    }

    result *= upperBoundInclusive ? 2 : 3;

    return result;
  }

  public boolean isLowerBoundInclusive() {
    return lowerBoundInclusive;
  }

  public boolean isUpperBoundInclusive() {
    return upperBoundInclusive;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();

    buf.append(isLowerBoundInclusive() ? '[' : '(');
    if (getLowerBound() != null) {
      buf.append(getLowerBound().toString());
    }
    buf.append(',');
    if (getUpperBound() != null) {
      buf.append(getUpperBound().toString());
    }
    buf.append(isUpperBoundInclusive() ? ']' : ')');

    return buf.toString();
  }
}
