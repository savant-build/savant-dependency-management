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
package org.savantbuild.dep.workflow.process;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.net.SSHOptions;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * SCPProcess Tester.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "functional")
public class SCPProcessTest {
  @BeforeMethod
  public void deleteFile() throws Exception {
    if (Files.isDirectory(Paths.get("/tmp/savant-test"))) {
      Connection connection = new Connection("localhost");
      connection.connect();
      connection.authenticateWithPassword("savanttest", "savantpassword");

      Session session = connection.openSession();
      session.execCommand("rm -rf /tmp/savant-test");
      session.close();
      connection.close();
    }

    assertFalse(Files.exists(Paths.get("/tmp/savant-text")));
  }

  @DataProvider(name = "options")
  public Object[][] options() {
    SSHOptions trust = new SSHOptions();
    trust.username = "savanttest";
    trust.identity = new File("src/java/test/org/savantbuild/dep/net/test_id_dsa");
    trust.knownHosts = null;
    trust.trustUnknownHosts = true;

    SSHOptions identity = new SSHOptions();
    identity.identity = null;
    identity.username = "savanttest";
    identity.identity = new File("src/java/test/org/savantbuild/dep/net/test_id_dsa");

    SSHOptions username = new SSHOptions();
    username.identity = null;
    username.username = "savanttest";
    username.password = "savantpassword";

    return new Object[][]{
        {trust}, {identity}, {username}
    };
  }

  @Test(dataProvider = "options")
  public void run(SSHOptions options) throws IOException {
    SCPProcess process = new SCPProcess("localhost", "/tmp/savant-test", options);
    Path path = Paths.get("src/java/test/org/savantbuild/dep/net/test_id_dsa");
    Artifact artifact = new Artifact("org.savantbuild.test:scp-test:1.0");
    process.publish(artifact, artifact.getArtifactFile(), path);

    Path result = Paths.get("/tmp/savant-test/org/savantbuild/test/scp-test/1.0.0/scp-test-1.0.0.jar");
    assertTrue(Files.isRegularFile(result));
  }
}
