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
package org.savantbuild.dep.version;

import org.savantbuild.dep.domain.Version;

/**
 * This class is an artifact compatibility checker for MAJOR.MINOR.PATCH versioning schemes. MAJOR versions are by
 * definition incompatible upgrades of the API; MINOR versions retain source and binary compatibility with older minor
 * versions; PATCH level changes are perfectly compatible, forwards and backwards.
 *
 * @author Brian Pontarelli
 */
public class MinorVersionComparator implements VersionComparator {
  /**
   * Determines if the two artifacts are compatible based on minor lines.
   *
   * @param previousVersion The previous version number to compare.
   * @param currentVersion  The current version being checked.
   * @return Null is returned if either of the versions is null or if they are not on the same major line. Otherwise,
   *         this returns the version that has the highest value.
   */
  public String determineBestVersion(String previousVersion, String currentVersion) {
    if (previousVersion.equals(currentVersion)) {
      return previousVersion; // perfect match
    } else if (currentVersion != null) {
      // Try using the smart versions
      Version currentVer = new Version(currentVersion);
      Version previousVer = new Version(previousVersion);

      // Smart versions are cool, compare using minor
      if (currentVer.major != previousVer.major) {
        return null;
      }

      int result = currentVer.compareTo(previousVer);
      return (result > 0) ? currentVersion : previousVersion;
    }

    return null;
  }
}
