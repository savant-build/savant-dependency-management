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
package org.savantbuild.dep.net;

import com.jcraft.jsch.JSchException;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.testng.Assert.assertTrue;

/**
 * SCP Tester.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "functional")
public class SCPTest {
  @Test
  public void userPasswordWithKnownHosts() throws IOException, JSchException {
    SSHOptions options = new SSHOptions();
    options.identity = null;
    options.username = "savant-test";
    options.password = "savant-password";
    options.server = "localhost";

    Path path = Paths.get("src/java/test/org/savantbuild/dep/net/test_id_dsa");
    Path result = Paths.get("/tmp/test_id_dsa");
    Files.delete(result);

    SCP scp = new SCP(options);
    scp.upload(path, "/tmp");
    assertTrue(Files.isRegularFile(result));
  }

  @Test
  public void userIdentity() throws IOException, JSchException {
    SSHOptions options = new SSHOptions();
    options.identity = null;
    options.username = "savant-test";
    options.identity = "src/java/test/org/savantbuild/dep/net/test_id_dsa";
    options.server = "localhost";

    Path path = Paths.get("src/java/test/org/savantbuild/dep/net/test_id_dsa");
    Path result = Paths.get("/tmp/test_id_dsa");
    Files.delete(result);

    SCP scp = new SCP(options);
    scp.upload(path, "/tmp");
    assertTrue(Files.isRegularFile(result));
  }

  @Test
  public void trust() throws IOException, JSchException {
    SSHOptions options = new SSHOptions();
    options.username = "savant-test";
    options.identity = "src/java/test/org/savantbuild/dep/net/test_id_dsa";
    options.server = "localhost";
    options.knownHosts = null;
    options.trustUnknownHosts = true;

    Path path = Paths.get("src/java/test/org/savantbuild/dep/net/test_id_dsa");
    Path result = Paths.get("/tmp/test_id_dsa");
    Files.delete(result);

    SCP scp = new SCP(options);
    scp.upload(path, "/tmp");
    assertTrue(Files.isRegularFile(result));
  }
}
