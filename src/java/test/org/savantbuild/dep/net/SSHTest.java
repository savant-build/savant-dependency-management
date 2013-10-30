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

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * SSH Tester.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "functional")
public class SSHTest {
  @Test
  public void userPasswordWithKnownHosts() throws JSchException {
    SSHOptions options = new SSHOptions();
    options.identity = null;
    options.username = "savant-test";
    options.password = "savant-password";
    options.server = "localhost";

    SSH ssh = new SSH(options);
    String result = ssh.execute("ls /");
    assertNotNull(result);
    assertTrue(result.contains("tmp"));
  }

  @Test
  public void testUserIdentity() throws JSchException {
    SSHOptions options = new SSHOptions();
    options.identity = null;
    options.username = "savant-test";
    options.identity = "src/java/test/integration/org/savantbuild/net/test_id_dsa";
    options.server = "localhost";

    SSH ssh = new SSH(options);
    String result = ssh.execute("ls /");
    assertNotNull(result);
    assertTrue(result.contains("tmp"));
  }

  @Test
  public void testTrust() throws JSchException {
    SSHOptions options = new SSHOptions();
    options.username = "savant-test";
    options.identity = "src/java/test/integration/org/savantbuild/net/test_id_dsa";
    options.server = "localhost";
    options.knownHosts = null;
    options.trustUnknownHosts = true;

    SSH ssh = new SSH(options);
    String result = ssh.execute("ls /");
    assertNotNull(result);
    assertTrue(result.contains("tmp"));
  }
}
