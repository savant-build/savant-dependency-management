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

import java.io.File;
import java.net.URI;

import org.savantbuild.dep.io.FileTools;
import org.savantbuild.dep.io.MD5;
import org.savantbuild.dep.io.MD5Exception;
import org.savantbuild.dep.io.PermanentIOException;
import org.savantbuild.dep.util.StringTools;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * The net tools test.
 */
public class NetToolsIntegrationTest {
  @Test
  public void downloadToString() throws Exception {
    String result = NetTools.downloadToString(new URI("http://savant.inversoft.org/org/junit/junit/4.4/junit-4.4.jar.md5"), null, null);
    assertEquals(result.trim(), "f852bbb2bbe0471cef8e5b833cb36078");
  }

  @Test
  public void downloadToFile() throws Exception {
    File f = NetTools.downloadToPath(new URI("http://savant.inversoft.org/org/junit/junit/4.4/junit-4.4.jar.md5"), null, null, null);
    String result = FileTools.read(f);
    assertEquals(result.trim(), "f852bbb2bbe0471cef8e5b833cb36078");
  }

  @Test
  public void downloadToFileWithMD5() throws Exception {
    String result = NetTools.downloadToString(new URI("http://savant.inversoft.org/org/junit/junit/4.4/junit-4.4.jar.md5"), null, null);
    String hash = result.substring(0, 32);
    byte[] bytes = StringTools.fromHex(hash);
    MD5 md5 = new MD5(hash, bytes, result.substring(33));

    File f = NetTools.downloadToPath(new URI("http://savant.inversoft.org/org/junit/junit/4.4/junit-4.4.jar"), null, null, md5);
    assertTrue(f.isFile());
  }

  @Test
  public void downloadToFileWithMD5Failure() throws Exception {
    MD5 md5 = new MD5("0000000000000000000000000000000", new byte[32], "");
    try {
      NetTools.downloadToPath(new URI("http://savant.inversoft.org/org/junit/junit/4.4/junit-4.4.jar"), null, null, md5);
      fail("Should have failed");
    } catch (PermanentIOException e) {
      assertTrue(e.getCause() instanceof MD5Exception);
    }
  }
}
