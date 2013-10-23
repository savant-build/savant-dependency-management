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

/**
 * This class is an artifact compatibility checker that verifies that only exact matches for artifacts are acceptable.
 *
 * @author Brian Pontarelli
 */
public class IdenticalVersionComparator implements VersionComparator {
  /**
   * Determines if the two artifacts are identical or not.
   *
   * @param previousVersion The previous version number to compare.
   * @param currentVersion  The current version being checked.
   * @return Null is returned if the artifacts are not equal. Otherwise the previous version is returned.
   */
  public String determineBestVersion(String previousVersion, String currentVersion) {
    boolean identical = previousVersion.equals(currentVersion);
    return (identical) ? previousVersion : null;
  }
}
