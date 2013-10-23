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
 * This class is an artifact compatibility checker for MAJOR.MINOR.PATCH versioning schemes. MAJOR and MINOR versions
 * are by definition incompatible upgrades of the API; PATCH versions retain source and binary compatibility with older
 * minor versions;
 *
 * @author Brian Pontarelli
 */
public class PatchVersionComparator implements VersionComparator {
  /**
   * Determines if the two artifacts are compatible on the patch line.
   *
   * @param previousVersion The previous version number to compare.
   * @param currentVersion  The current version being checked.
   * @return Null is returned if either of the versions is null or if they are not on the same major and minor line.
   *         Otherwise, this returns the version that has the highest value.
   */
  public String determineBestVersion(String previousVersion, String currentVersion) {
    if (previousVersion.equals(currentVersion)) {
      return previousVersion; // perfect match
    } else if (currentVersion != null) {
      Version currentVer = new Version(currentVersion);
      Version previousVer = new Version(previousVersion);

      if (currentVer.major != previousVer.major) {
        return null;
      }

      if (currentVer.minor != previousVer.minor) {
        return null;
      }

      int result = currentVer.compareTo(previousVer);
      return (result > 0) ? currentVersion : previousVersion;
    }

    return null;
  }

}
