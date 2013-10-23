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

import java.io.File;

import org.savantbuild.dep.DependencyException;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * SCP Tester.
 *
 * @author Brian Pontarelli
 */
public class SCPTest {
  @Test
  public void userPasswordWithKnownHosts() {
    try {
      SSHOptions options = new SSHOptions();
      options.identity = null;
      options.username = "savant-test";
      options.password = "savant-password";
      options.server = "localhost";

      File f = new File("src/java/test/integration/org/savantbuild/net/test_id_dsa");
      File result = new File("/tmp/test_id_dsa");
      result.delete();

      SCP scp = new SCP(options);
      scp.upload(f, "/tmp");
      assertTrue(result.isFile());
    } catch (DependencyException e) {
      System.out.println("*****FIX THIS*****\n\tI couldn't figure out how to fix this unit test.  " +
        "Something to do with ${user.home}/known_hosts I think\n*****FIX THIS*****");
      e.printStackTrace();
    }
  }

  @Test
  public void userIdentity() {
    SSHOptions options = new SSHOptions();
    options.identity = null;
    options.username = "savant-test";
    options.identity = "src/java/test/integration/org/savantbuild/net/test_id_dsa";
    options.server = "localhost";

    File f = new File("src/java/test/integration/org/savantbuild/net/test_id_dsa");
    File result = new File("/tmp/test_id_dsa");
    result.delete();

    SCP scp = new SCP(options);
    scp.upload(f, "/tmp");
    assertTrue(result.isFile());
  }

  @Test
  public void trust() {
    SSHOptions options = new SSHOptions();
    options.username = "savant-test";
    options.identity = "src/java/test/integration/org/savantbuild/net/test_id_dsa";
    options.server = "localhost";
    options.knownHosts = null;
    options.trustUnknownHosts = true;

    File f = new File("src/java/test/integration/org/savantbuild/net/test_id_dsa");
    File result = new File("/tmp/test_id_dsa");
    result.delete();

    SCP scp = new SCP(options);
    scp.upload(f, "/tmp");
    assertTrue(result.isFile());
  }
}
