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

import org.savantbuild.dep.version.PatchVersionComparator;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * <p>
 * This class is the test case for PatchArtifactCompatibilityChecker class.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class PatchArtifactCompatibilityCheckerTest {
  /**
   * Test major/minor/patch Comparable implementation
   */
  @Test
  public void majorPatchPatchComparable() {
    PatchVersionComparator c = new PatchVersionComparator();
    assertSame("1.2.1", c.determineBestVersion("1.2.1", "1.2.1"));

    c = new PatchVersionComparator();
    assertSame(null, c.determineBestVersion("1.2.1", "2.1"));

    c = new PatchVersionComparator();
    assertSame(null, c.determineBestVersion("1.2.1", "1.8"));

    c = new PatchVersionComparator();
    assertSame("1.8.1", c.determineBestVersion("1.8", "1.8.1"));

    c = new PatchVersionComparator();
    assertSame(null, c.determineBestVersion("09272005", "09302005"));
  }

  /**
   * Test major/minor/patch works with RC
   */
  @Test
  public void rc() {
    PatchVersionComparator c = new PatchVersionComparator();
    assertSame("1.2.1-RC", c.determineBestVersion("1.2.1-RC", "1.2.1-RC"));

    c = new PatchVersionComparator();
    assertSame(null, c.determineBestVersion("1.2.1-RC", "2.1-RC"));

    c = new PatchVersionComparator();
    assertSame(null, c.determineBestVersion("1.2.1-RC", "1.8-RC"));

    c = new PatchVersionComparator();
    assertSame("1.8.1-RC", c.determineBestVersion("1.8.1-RC", "1.8-RC"));

    c = new PatchVersionComparator();
    assertSame("1.8.1-RC", c.determineBestVersion("1.8.1-RC", "1.8"));

    c = new PatchVersionComparator();
    assertSame("1.8.9-RC", c.determineBestVersion("1.8.1", "1.8.9-RC"));

    c = new PatchVersionComparator();
    assertSame("1.8.9", c.determineBestVersion("1.8.9", "1.8.9-RC"));
  }

  /**
   * Test major/minor/patch works with just strings
   */
  @Test
  public void strings() {
    PatchVersionComparator c = new PatchVersionComparator();
    assertSame("foo", c.determineBestVersion("foo", "bar"));

    c = new PatchVersionComparator();
    assertSame("foo", c.determineBestVersion("foo", "foo"));
  }

  /**
   * Test major/minor/patch works with mixed versions
   */
  @Test
  public void mixed() {
    PatchVersionComparator c = new PatchVersionComparator();
    assertSame("1.0", c.determineBestVersion("1.0beta9", "1.0"));
  }
}
