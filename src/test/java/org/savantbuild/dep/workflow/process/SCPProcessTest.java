/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.savantbuild.dep.BaseUnitTest;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.ReifiedArtifact;
import org.savantbuild.dep.net.SSHOptions;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * SCPProcess Tester. In order to get this working, you must setup SSH on your machine. Here are the steps on a Mac:
 * <p>
 * <pre>
 *   - Create a savanttest user with the password savantpassword (you can disable this account when you are done
 * testing
 * for security)
 *   - Enable password authentication (edit the /etc/sshd_config file and uncomment the "PasswordAuthentication yes"
 * line)
 *   - Restart SSH (sudo launchctl unload /System/Library/LaunchDaemons/ssh.plist followed by a load)
 * </pre>
 *
 * @author Brian Pontarelli
 */
public class SCPProcessTest extends BaseUnitTest {
  private Path path = Paths.get("/tmp/savant-test");

  @BeforeMethod
  public void deleteFile() throws Exception {
    if (Files.isDirectory(path)) {
      Connection connection = new Connection("localhost");
      connection.connect();
      connection.authenticateWithPassword("savanttest", "savantpassword");

      Session session = connection.openSession();
      session.execCommand("rm -rf " + path.toString());
      session.close();
      connection.close();

      // Wait for the ssh command to terminate
      Thread.sleep(100);
    }

    assertFalse(Files.isDirectory(path));
  }

  @DataProvider(name = "options")
  public Object[][] options() {
    SSHOptions trust = new SSHOptions();
    trust.username = "savanttest";
    trust.identity = projectDir.resolve("src/test/java/org/savantbuild/dep/net/test_id_dsa").toFile();
    trust.knownHosts = null;
    trust.trustUnknownHosts = true;

    SSHOptions identity = new SSHOptions();
    identity.identity = null;
    identity.username = "savanttest";
    identity.identity = projectDir.resolve("src/test/java/org/savantbuild/dep/net/test_id_dsa").toFile();

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
    SCPProcess process = new SCPProcess(output, "localhost", path.toString(), options);
    Path path = projectDir.resolve("src/test/java/org/savantbuild/dep/net/test_id_dsa");
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:scp-test:1.0", License.Apachev2);
    process.publish(artifact, artifact.getArtifactFile(), path);

    Path result = Paths.get("/tmp/savant-test/org/savantbuild/test/scp-test/1.0.0/scp-test-1.0.0.jar");
    assertTrue(Files.isRegularFile(result));
  }
}
