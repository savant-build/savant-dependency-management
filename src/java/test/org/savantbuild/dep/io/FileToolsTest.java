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
package org.savantbuild.io;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * <p>
 * This class tests the FileTools.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class FileToolsTest {
  /**
   * Tests that the copy file works correctly by copying a file and comparing the copy, byte for byte, to the original.
   */
  @Test
  public void copy() {
    File from = new File("src/java/test/unit/org/savantbuild/io/FileToolsTest.java");
    File to = new File("target/test/FileToolsTest.java.copy");
    to.delete();

    try {
      FileTools.copy(from, to);
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.toString());
    }

    // Make sure it exists and they are the same size
    assertTrue(to.exists());
    assertTrue(from.length() == to.length());

    compare(from, to);
  }

  /**
   * Tests that the copy strings.
   */
  @Test
  public void copyToDir() {
    new File("target/test/src/java/foo/FileTools.java").delete();
    new File("target/test/src/java/foo").mkdirs();

    try {
      FileTools.copy(new File("src/java/test/unit/org/savantbuild/io/FileToolsTest.java"), new File("target/test/src/java/foo"));
    } catch (Exception e) {
      fail(e.toString());
    }

    // Make sure it exists and they are the same size
    String path = "src/java/test/unit/org/savantbuild/io/FileToolsTest.java";
    File from = new File(path);
    File to = new File("target/test/src/java/foo/FileToolsTest.java");
    assertTrue(to.exists());
    assertTrue(from.length() == to.length());

    compare(from, to);
  }

  @Test
  public void md5() throws IOException {
    File f = new File("src/java/test/unit/org/savantbuild/io/copy-test.txt");
    MD5 md5 = FileTools.md5(f);
    assertNotNull(md5);
    assertEquals(md5.fileName, "copy-test.txt");
    assertEquals(md5.sum, "c0bfbec19e8e5578e458ce5bfee20751");
  }

  private void compare(File from, File to) {
    if (from.isDirectory()) {
      assertTrue(to.isDirectory());
      File[] files = from.listFiles();
      for (File file : files) {
        compare(file, new File(to, file.getName()));
      }
    } else {
      try {
        // Compare them
        FileReader fromReader = new FileReader(from);
        FileReader toReader = new FileReader(to);
        int fromChar;
        int toChar;

        do {
          fromChar = fromReader.read();
          toChar = toReader.read();
          assertTrue(fromChar == toChar);
        } while (fromChar != -1);
      } catch (Exception e) {
        fail(e.toString());
      }
    }
  }
}
