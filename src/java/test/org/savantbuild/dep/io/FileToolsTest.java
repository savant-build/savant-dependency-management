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
package org.savantbuild.dep.io;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * This class tests the FileTools.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class FileToolsTest {
  @Test
  public void md5() throws IOException {
    Path f = Paths.get("src/java/test/org/savantbuild/dep/io/FileToolsTest.txt");
    MD5 md5 = FileTools.md5(f);
    assertNotNull(md5);
    assertEquals(md5.fileName, "FileToolsTest.txt");
    assertEquals(md5.sum, "c0bfbec19e8e5578e458ce5bfee20751");
  }
}