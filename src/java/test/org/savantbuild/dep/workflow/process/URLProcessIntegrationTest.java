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
package org.savantbuild.dep.workflow.process;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.run.output.DefaultOutput;
import org.testng.annotations.Test;

import static org.savantbuild.IntegrationTestTools.*;
import static org.testng.Assert.*;

/**
 * <p>
 * This tests the HTTP index listing parsing for integration and latest version handling.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class URLProcessIntegrationTest {
  @Test(enabled = true)
  public void integrationInternet() throws Exception {
    Artifact artifact = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "2.1.1-{integration}", "jar");
    URLProcess ufp = new URLProcess(new DefaultOutput(), map("url", "http://savant.inversoft.org"));

    String version = ufp.determineVersion(artifact);
    assertEquals("2.1.1-IB20080103144403111", version);
  }

  @Test(enabled = true)
  public void latestVersionInternet() throws Exception {
    Artifact artifact = new Artifact("org.savantbuild.test", "major-compat", "major-compt", "{latest}", "jar");
    URLProcess ufp = new URLProcess(new DefaultOutput(), map("url", "http://savant.inversoft.org"));

    String version = ufp.determineVersion(artifact);
    assertEquals("2.0", version);
  }

  @Test(enabled = true)
  public void latestAndIntegrationVersionInternet() throws Exception {
    Artifact artifact = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "{latest}", "jar");
    URLProcess ufp = new URLProcess(new DefaultOutput(), map("url", "http://savant.inversoft.org"));

    String version = ufp.determineVersion(artifact);
    assertEquals("2.1.1-{integration}", version);
  }
}
