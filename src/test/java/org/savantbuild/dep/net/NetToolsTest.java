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
package org.savantbuild.dep.net;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.savantbuild.dep.BaseUnitTest;
import org.savantbuild.net.NetTools;
import org.savantbuild.security.MD5;
import org.savantbuild.security.MD5Exception;
import org.testng.annotations.Test;

import com.sun.net.httpserver.HttpServer;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * The net tools test.
 */
public class NetToolsTest extends BaseUnitTest {
  @Test
  public void build() throws URISyntaxException {
    URI uri = NetTools.build("http://www.example.com", "org/apache/commons", "common-collections", "3.0", "commons-collections-3.0.jar");
    assertEquals(uri.toString(), "http://www.example.com/org/apache/commons/common-collections/3.0/commons-collections-3.0.jar");

    uri = NetTools.build("http://www.example.com/", "org/apache/commons", "common-collections", "3.0", "commons-collections-3.0.jar");
    assertEquals(uri.toString(), "http://www.example.com/org/apache/commons/common-collections/3.0/commons-collections-3.0.jar");

    uri = NetTools.build("http://www.example.com/", "/org/apache/commons/", "common-collections", "3.0", "commons-collections-3.0.jar");
    assertEquals(uri.toString(), "http://www.example.com/org/apache/commons/common-collections/3.0/commons-collections-3.0.jar");
  }

  @Test
  public void downloadToFile() throws Exception {
    HttpServer server = makeFileServer(null, null);

    try {
      Path path = NetTools.downloadToPath(new URI("http://localhost:7000/src/test/java/org/savantbuild/dep/TestFile.txt"), null, null, null);
      String result = new String(Files.readAllBytes(path), "UTF-8");
      assertEquals(result.trim(), "This file is a test file for copying and writing and such.");
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void downloadToFileWithUsernameAndPassword() throws Exception {
    HttpServer server = makeFileServer("User", "Pass");

    try {
      Path path = NetTools.downloadToPath(new URI("http://localhost:7000/src/test/java/org/savantbuild/dep/TestFile.txt"), "User", "Pass", null);
      String result = new String(Files.readAllBytes(path), "UTF-8");
      assertEquals(result.trim(), "This file is a test file for copying and writing and such.");
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void downloadToFileWithMD5() throws Exception {
    HttpServer server = makeFileServer(null, null);

    try {
      MD5 md5 = MD5.forBytes(Files.readAllBytes(projectDir.resolve("src/test/java/org/savantbuild/dep/TestFile.txt")), "TestFile.txt");
      Path path = NetTools.downloadToPath(new URI("http://localhost:7000/src/test/java/org/savantbuild/dep/TestFile.txt"), null, null, md5);
      String result = new String(Files.readAllBytes(path), "UTF-8");
      assertEquals(result.trim(), "This file is a test file for copying and writing and such.");
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void downloadToFileWithMD5Failure() throws Exception {
    HttpServer server = makeFileServer(null, null);
    MD5 md5 = new MD5("0000000000000000000000000000000", new byte[] {0,0,0,0,0}, null);
    try {
      NetTools.downloadToPath(new URI("http://localhost:7000/src/test/java/org/savantbuild/dep/TestFile.txt"), null, null, md5);
      fail("Should have failed");
    } catch (MD5Exception e) {
      // Expected
    } finally {
      server.stop(0);
    }
  }
}
