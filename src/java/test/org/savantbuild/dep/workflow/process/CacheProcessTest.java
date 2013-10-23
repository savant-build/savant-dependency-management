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

import java.io.File;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.io.FileTools;
import org.savantbuild.run.output.DefaultOutput;
import org.testng.annotations.Test;

import static org.savantbuild.TestTools.*;
import static org.testng.Assert.*;

/**
 * <p>
 * This class is the test for the CacheProcess.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class CacheProcessTest {
  @Test
  public void fetch() {
    CacheProcess process = new CacheProcess(new DefaultOutput(), map("dir", "test-deps/savant"));
    Artifact artifact = new Artifact("org.savantbuild.test", "major-compat", "major-compat", "2.0", "jar");

    File f = process.fetch(artifact, artifact.getArtifactFile(), null);
    assertNotNull(f);
    assertTrue(f.getAbsolutePath().replace('\\', '/').endsWith("test-deps/savant/org/savantbuild/test/major-compat/2.0/major-compat-2.0.jar"));
    assertTrue(f.isFile());
  }

  @Test
  public void store() {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    CacheProcess process = new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps"));
    Artifact artifact = new Artifact("org.savantbuild.test", "major-compat", "major-compat", "2.0", "jar");

    File artFile = new File("test-deps/savant/org/savantbuild/test/major-compat/2.0/major-compat-2.0.jar");
    File f = process.publish(artifact, artifact.getArtifactFile(), artFile);
    assertNotNull(f);
    assertTrue(f.getAbsolutePath().replace('\\', '/').endsWith("target/test/deps/org/savantbuild/test/major-compat/2.0/major-compat-2.0.jar"));
    assertTrue(f.isFile());
  }

  @Test
  public void delete() {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    CacheProcess process = new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps"));
    Artifact artifact = new Artifact("org.savantbuild.test", "major-compat", "major-compat", "2.0", "jar");

    File artFile = new File("test-deps/savant/org/savantbuild/test/major-compat/2.0/major-compat-2.0.jar");
    File f = process.publish(artifact, artifact.getArtifactFile(), artFile);
    process.delete(artifact, artifact.getArtifactFile());
    assertFalse(f.isFile());
  }

  @Test
  public void deleteIntegration() {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    CacheProcess process = new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps"));
    Artifact forPath = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "2.1.1-{integration}", "jar");
    Artifact forName = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "2.1.1-IB20071231144403111", "jar");

    File artFile = new File("test-deps/savant/org/savantbuild/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-IB20071231144403111.jar");
    File f = process.publish(forPath, forName.getArtifactFile(), artFile);
    assertTrue(f.isFile());

    forPath = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "2.1.1", "jar");
    process.deleteIntegrationBuilds(forPath);
    assertFalse(f.isFile());
  }

  /**
   * Tests that the integration version can be found.
   */
  @Test
  public void integrationVersion() {
    CacheProcess process = new CacheProcess(new DefaultOutput(), map("dir", "test-deps/savant"));
    Artifact artifact = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "2.1.1-{integration}", "jar");
    assertEquals(process.determineVersion(artifact), "2.1.1-IB20080103144403111");
  }

  /**
   * Tests that the latest version can be found.
   */
  @Test
  public void latestVersion() {
    CacheProcess process = new CacheProcess(new DefaultOutput(), map("dir", "test-deps/savant"));
    Artifact artifact = new Artifact("org.savantbuild.test", "major-compat", "major-compat", "{latest}", "jar");
    assertEquals("2.0", process.determineVersion(artifact));

    artifact = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "{latest}", "jar");
    assertEquals(process.determineVersion(artifact), "2.1.1-{integration}");
  }
}
