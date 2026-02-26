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
import java.util.List;

import org.savantbuild.dep.BaseUnitTest;
import org.savantbuild.dep.PathTools;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.ReifiedArtifact;
import org.savantbuild.dep.domain.ResolvableItem;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * This class is the test for the CacheProcess.
 *
 * @author Brian Pontarelli
 */
public class CacheProcessTest extends BaseUnitTest {
  @Test
  public void fetch() {
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/savant").toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    FetchResult result = process.fetch(item, null);
    assertNotNull(result);
    assertTrue(result.file().toAbsolutePath().toString().replace('\\', '/').endsWith("test-deps/savant/org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar"));
    assertTrue(Files.isRegularFile(result.file()));
  }

  @Test
  public void fetchIntegration() {
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/integration").toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:integration-build:integration-build:2.1.1-{integration}:jar", License.Licenses.get("ApacheV2_0"));

    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    FetchResult result = process.fetch(item, null);
    assertNotNull(result);
    assertTrue(result.file().toAbsolutePath().toString().replace('\\', '/').endsWith("test-deps/integration/org/savantbuild/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar"));
    assertTrue(Files.isRegularFile(result.file()));
  }

  @Test
  public void store() throws Exception {
    Path cache = projectDir.resolve("build/test/deps");
    PathTools.prune(cache);

    CacheProcess process = new CacheProcess(output, cache.toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    Path artFile = projectDir.resolve("test-deps/savant/org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar");
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path file = process.publish(new FetchResult(artFile, ItemSource.SAVANT, item));
    assertNotNull(file);
    assertTrue(file.toAbsolutePath().toString().replace('\\', '/').endsWith("build/test/deps/org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar"));
    assertTrue(Files.isRegularFile(file));
  }

  @Test
  public void store_rejectsMaven() throws Exception {
    Path cache = projectDir.resolve("build/test/deps");
    PathTools.prune(cache);

    CacheProcess process = new CacheProcess(output, cache.toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    Path artFile = projectDir.resolve("test-deps/savant/org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar");
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path result = process.publish(new FetchResult(artFile, ItemSource.MAVEN, item));
    assertNull(result);
  }

  @Test
  public void storeIntegration() throws Exception {
    Path cache = projectDir.resolve("build/test/deps");
    PathTools.prune(cache);

    CacheProcess process = new CacheProcess(output, cache.toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:integration-build:integration-build:2.1.1-{integration}:jar", License.Licenses.get("ApacheV2_0"));

    Path artFile = projectDir.resolve("test-deps/integration/org/savantbuild/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar");
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path file = process.publish(new FetchResult(artFile, ItemSource.SAVANT, item));
    assertNotNull(file);
    assertTrue(file.toAbsolutePath().toString().replace('\\', '/').endsWith("build/test/deps/org/savantbuild/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar"));
    assertTrue(Files.isRegularFile(file));
  }

  @Test
  public void fetch_withAlternative_primaryHit() {
    // When the primary item exists, alternatives should not be checked and the primary item name is returned
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/savant").toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
        artifact.version.toString(), artifact.getArtifactFile(),
        List.of(artifact.getArtifactAlternativeSourceFile()));
    FetchResult result = process.fetch(item, null);
    assertNotNull(result);
    assertEquals(result.item().item, artifact.getArtifactFile());
    assertTrue(result.file().toAbsolutePath().toString().replace('\\', '/').endsWith("multiple-versions-1.0.0.jar"));
    assertTrue(Files.isRegularFile(result.file()));
  }

  @Test
  public void fetch_withAlternative_primaryMissAlternativeHit() {
    // When primary (-src.jar) doesn't exist but alternative (-sources.jar) does, the alternative is returned
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/savant").toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
        artifact.version.toString(), artifact.getArtifactSourceFile(),
        List.of(artifact.getArtifactAlternativeSourceFile()));
    FetchResult result = process.fetch(item, null);
    assertNotNull(result);
    assertEquals(result.item().item, artifact.getArtifactAlternativeSourceFile());
    assertTrue(result.file().toAbsolutePath().toString().replace('\\', '/').endsWith("multiple-versions-1.0.0-sources.jar"));
    assertTrue(Files.isRegularFile(result.file()));
  }

  @Test(expectedExceptions = NegativeCacheException.class)
  public void fetch_withAlternative_negativeCache() {
    // When negative cache marker exists for primary, NegativeCacheException is thrown before checking alternatives
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/savant").toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.1.0:jar", License.Licenses.get("ApacheV2_0"));

    // multiple-versions-1.1.0-src.jar.neg exists in test fixtures
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
        artifact.version.toString(), artifact.getArtifactSourceFile(),
        List.of(artifact.getArtifactAlternativeSourceFile()));
    process.fetch(item, null);
  }

  @Test
  public void fetch_withAlternative_noneFound() {
    // When neither primary nor alternative exists, null is returned
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/savant").toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    // Use item names that don't exist in test fixtures
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
        artifact.version.toString(), "nonexistent-1.0.0-src.jar",
        List.of("nonexistent-1.0.0-sources.jar"));
    FetchResult result = process.fetch(item, null);
    assertNull(result);
  }
}
