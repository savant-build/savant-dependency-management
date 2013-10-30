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
import org.savantbuild.dep.io.FileTools;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * This class is the test for the CacheProcess.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class CacheProcessTest {
  @Test
  public void deleteIntegration() throws Exception {
    Path cache = Paths.get("build/test/deps");
    FileTools.prune(cache);

    CacheProcess process = new CacheProcess("build/test/deps");
    Artifact artifact = new Artifact("org.savantbuild.test:integration-build:integration-build:2.1.1-{integration}:jar");

    Path artFile = Paths.get("test-deps/savant/org/savantbuild/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar");
    Path file = process.publish(artifact, artifact.getArtifactFile(), artFile);
    assertTrue(Files.isRegularFile(file));

    artifact = new Artifact("org.savantbuild.test:integration-build:integration-build:2.1.1:jar");
    process.deleteIntegrationBuilds(artifact);
    assertFalse(Files.isRegularFile(file));
  }

  @Test
  public void fetch() {
    CacheProcess process = new CacheProcess("test-deps/savant");
    Artifact artifact = new Artifact("org.savantbuild.test:dependencies:dependencies:1.0.0:jar");

    Path file = process.fetch(artifact, artifact.getArtifactFile(), null);
    assertNotNull(file);
    assertTrue(file.toAbsolutePath().toString().replace('\\', '/').endsWith("test-deps/savant/org/savantbuild/test/dependencies/1.0.0/dependencies-1.0.0.jar"));
    assertTrue(Files.isRegularFile(file));
  }

  @Test
  public void store() throws Exception {
    Path cache = Paths.get("build/test/deps");
    FileTools.prune(cache);

    CacheProcess process = new CacheProcess("build/test/deps");
    Artifact artifact = new Artifact("org.savantbuild.test:dependencies:dependencies:1.0.0:jar");

    Path artFile = Paths.get("test-deps/savant/org/savantbuild/test/dependencies/1.0.0/dependencies-1.0.0.jar");
    Path file = process.publish(artifact, artifact.getArtifactFile(), artFile);
    assertNotNull(file);
    assertTrue(file.toAbsolutePath().toString().replace('\\', '/').endsWith("build/test/deps/org/savantbuild/test/dependencies/1.0.0/dependencies-1.0.0.jar"));
    assertTrue(Files.isRegularFile(file));
  }
}
