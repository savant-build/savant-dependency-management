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
package org.savantbuild.dep.compat;

import org.savantbuild.dep.version.MinorVersionComparator;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * <p>
 * This class is the test case for MinorArtifactCompatibilityChecker class.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class MinorArtifactCompatibilityCheckerTest {
  /**
   * Test major/minor/patch Comparable implementation
   */
  @Test
  public void majorMinorPatchComparable() {
    MinorVersionComparator c = new MinorVersionComparator();
    String v1 = "1.2.1";
    String v2 = "1.2.1";
    assertSame(v1, c.determineBestVersion(v1, v2));

    c = new MinorVersionComparator();
    v1 = "1.2.1";
    v2 = "2.1";
    assertSame(null, c.determineBestVersion(v1, v2));

    c = new MinorVersionComparator();
    v1 = "1.2.1";
    v2 = "1.8";
    assertSame(v2, c.determineBestVersion(v1, v2));

    c = new MinorVersionComparator();
    v1 = "1.8.1";
    v2 = "1.8";
    assertSame(v1, c.determineBestVersion(v1, v2));

    c = new MinorVersionComparator();
    v1 = "09272005";
    v2 = "09302005";
    assertSame(null, c.determineBestVersion(v1, v2));
  }

  /**
   * Test major/minor/patch works with RC
   */
  @Test
  public void rc() {
    MinorVersionComparator c = new MinorVersionComparator();
    String v1 = "1.2.1-RC";
    String v2 = "1.2.1-RC";
    assertSame(v1, c.determineBestVersion(v1, v2));

    c = new MinorVersionComparator();
    v1 = "1.2.1-RC";
    v2 = "2.1-RC";
    assertSame(null, c.determineBestVersion(v1, v2));

    c = new MinorVersionComparator();
    v1 = "1.2.1-RC";
    v2 = "1.8-RC";
    assertSame(v2, c.determineBestVersion(v1, v2));

    c = new MinorVersionComparator();
    v1 = "1.8.1-RC";
    v2 = "1.8-RC";
    assertSame(v1, c.determineBestVersion(v1, v2));

    c = new MinorVersionComparator();
    v1 = "1.8.1-RC";
    v2 = "1.8";
    assertSame(v1, c.determineBestVersion(v1, v2));

    c = new MinorVersionComparator();
    v1 = "1.8.1";
    v2 = "1.8.9-RC";
    assertSame(v2, c.determineBestVersion(v1, v2));

    c = new MinorVersionComparator();
    v1 = "1.8.9";
    v2 = "1.8.9-RC";
    assertSame(v1, c.determineBestVersion(v1, v2));
  }

  /**
   * Test major/minor/patch works with just strings
   */
  @Test
  public void strings() {
    MinorVersionComparator c = new MinorVersionComparator();
    String v1 = "foo";
    String v2 = "bar";
    assertSame(v1, c.determineBestVersion(v1, v2));

    c = new MinorVersionComparator();
    v1 = "foo";
    v2 = "foo";
    assertSame(v1, c.determineBestVersion(v1, v2));
  }

  /**
   * Test major/minor/patch works with mixed versions
   */
  @Test
  public void mixed() {
    MinorVersionComparator c = new MinorVersionComparator();
    String v1 = "1.0beta9";
    String v2 = "1.0";
    assertSame(v2, c.determineBestVersion(v1, v2));
  }
}
