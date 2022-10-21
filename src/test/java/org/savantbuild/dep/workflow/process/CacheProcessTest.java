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

import java.nio.file.Files;
import java.nio.file.Path;

import org.savantbuild.dep.BaseUnitTest;
import org.savantbuild.dep.PathTools;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.ReifiedArtifact;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * This class is the test for the CacheProcess.
 *
 * @author Brian Pontarelli
 */
public class CacheProcessTest extends BaseUnitTest {
  @Test
  public void deleteIntegration() throws Exception {
    Path cache = projectDir.resolve("build/test/deps");
    PathTools.prune(cache);

    CacheProcess process = new CacheProcess(output, cache.toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:integration-build:integration-build:2.1.1-{integration}:jar", License.Licenses.get("ApacheV2_0"));

    Path artFile = projectDir.resolve("test-deps/savant/org/savantbuild/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar");
    Path file = process.publish(artifact, artifact.getArtifactFile(), artFile);
    assertTrue(Files.isRegularFile(file));

    artifact = new ReifiedArtifact("org.savantbuild.test:integration-build:integration-build:2.1.1:jar", License.Licenses.get("ApacheV2_0"));
    process.deleteIntegrationBuilds(artifact);
    assertFalse(Files.isRegularFile(file));
  }

  @Test
  public void fetch() {
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/savant").toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    Path file = process.fetch(artifact, artifact.getArtifactFile(), null);
    assertNotNull(file);
    assertTrue(file.toAbsolutePath().toString().replace('\\', '/').endsWith("test-deps/savant/org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar"));
    assertTrue(Files.isRegularFile(file));
  }

  @Test
  public void store() throws Exception {
    Path cache = projectDir.resolve("build/test/deps");
    PathTools.prune(cache);

    CacheProcess process = new CacheProcess(output, cache.toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    Path artFile = projectDir.resolve("test-deps/savant/org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar");
    Path file = process.publish(artifact, artifact.getArtifactFile(), artFile);
    assertNotNull(file);
    assertTrue(file.toAbsolutePath().toString().replace('\\', '/').endsWith("build/test/deps/org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar"));
    assertTrue(Files.isRegularFile(file));
  }
}
