/*
 * Copyright (c) 2001-2010, Inversoft, All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.util;

import java.util.Arrays;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * <p>
 * This
 * </p>
 *
 * @author Brian Pontarelli
 */
public class StringToolsTest {
  @Test
  public void safe() {
    assertNull(StringTools.safe(null));
    assertEquals(StringTools.safe(""), "");
    assertNotNull(StringTools.safe(new Object()));
  }

  @Test
  public void isTrimmedEmpty() {
    assertTrue(StringTools.isTrimmedEmpty(null));
    assertTrue(StringTools.isTrimmedEmpty(""));
    assertTrue(StringTools.isTrimmedEmpty("      "));
  }

  @Test
  public void join() {
    assertEquals(StringTools.join("/", "foo", "bar", "baz"), "foo/bar/baz");
  }

  @Test
  public void toHex() {
    assertEquals(StringTools.toHex((byte) 0), "00");
    assertEquals(StringTools.toHex((byte) 1), "01");
    assertEquals(StringTools.toHex((byte) 2), "02");
    assertEquals(StringTools.toHex((byte) 3), "03");
    assertEquals(StringTools.toHex((byte) 4), "04");
    assertEquals(StringTools.toHex((byte) 5), "05");
    assertEquals(StringTools.toHex((byte) 6), "06");
    assertEquals(StringTools.toHex((byte) 7), "07");
    assertEquals(StringTools.toHex((byte) 8), "08");
    assertEquals(StringTools.toHex((byte) 9), "09");
    assertEquals(StringTools.toHex((byte) 10), "0a");
    assertEquals(StringTools.toHex((byte) 11), "0b");
    assertEquals(StringTools.toHex((byte) 12), "0c");
    assertEquals(StringTools.toHex((byte) 13), "0d");
    assertEquals(StringTools.toHex((byte) 14), "0e");
    assertEquals(StringTools.toHex((byte) 15), "0f");
    assertEquals(StringTools.toHex((byte) 0), "00");
    assertEquals(StringTools.toHex((byte) 200), "c8");
    assertEquals(StringTools.toHex((byte) 234, (byte) 93), "ea5d");
  }

  @Test
  public void fromHex() {
    assertTrue(Arrays.equals(StringTools.fromHex("00"), new byte[]{(byte) 0}));
    assertTrue(Arrays.equals(StringTools.fromHex("C8"), new byte[]{(byte) 200}));
    assertTrue(Arrays.equals(StringTools.fromHex("EA5D"), new byte[]{(byte) 234, (byte) 93}));
  }

  @Test
  public void toInt() {
    assertEquals(StringTools.toInt("0", 1), 0);
    assertEquals(StringTools.toInt(null, 1), 1);
    assertEquals(StringTools.toInt("", 1), 1);
    assertEquals(StringTools.toInt("  ", 1), 1);
  }

  @Test
  public void toBoolean() {
    assertTrue(StringTools.toBoolean("true", false));
    assertTrue(StringTools.toBoolean(null, true));
    assertFalse(StringTools.toBoolean("", false));
    assertFalse(StringTools.toBoolean("  ", false));
  }
}
