/*
 * Copyright 2007 (c) by Texture Media, Inc.
 *
 * This software is confidential and proprietary to
 * Texture Media, Inc. It may not be reproduced,
 * published or disclosed to others without company
 * authorization.
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
public class MajorVersionComparator implements VersionComparator {
  /**
   * Determines if the two artifacts are identical or not.
   *
   * @param previousVersion The previous version number to compare.
   * @param currentVersion  The current version being checked.
   * @return Null is returned if either of the versions is null. Otherwise, this returns the version that has the
   *         highest value.
   */
  public String determineBestVersion(String previousVersion, String currentVersion) {
    if (previousVersion.equals(currentVersion)) {
      return previousVersion; // perfect match
    } else if (currentVersion != null) {
      // Try using the smart versions
      Version currentVer = new Version(currentVersion);
      Version previousVer = new Version(previousVersion);

      // Smart versions are cool, compare using minor
      int result = currentVer.compareTo(previousVer);
      return (result > 0) ? currentVersion : previousVersion;
    }

    return null;
  }

}
