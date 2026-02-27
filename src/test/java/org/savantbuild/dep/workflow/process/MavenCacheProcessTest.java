/*
 * Copyright (c) 2014-2025, Inversoft Inc., All Rights Reserved
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
import org.savantbuild.dep.domain.ResolvableItem;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * This class is the test for the MavenCacheProcess.
 *
 * @author Brian Pontarelli
 */
public class MavenCacheProcessTest extends BaseUnitTest {
  @Test
  public void store_rejectsSavant() throws Exception {
    Path mavenCache = projectDir.resolve("build/test/maven-deps");
    PathTools.prune(mavenCache);

    MavenCacheProcess process = new MavenCacheProcess(output, mavenCache.toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    Path artFile = projectDir.resolve("test-deps/savant/org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar");
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path result = process.publish(new FetchResult(artFile, ItemSource.SAVANT, item));
    assertNull(result);
  }

  @Test
  public void store_acceptsMaven() throws Exception {
    Path mavenCache = projectDir.resolve("build/test/maven-deps");
    PathTools.prune(mavenCache);

    MavenCacheProcess process = new MavenCacheProcess(output, mavenCache.toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    Path artFile = projectDir.resolve("test-deps/savant/org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar");
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path result = process.publish(new FetchResult(artFile, ItemSource.MAVEN, item));
    assertNotNull(result);
    assertTrue(Files.isRegularFile(result));
  }

  @Test
  public void fetch_negativeCache() throws Exception {
    // When a .neg marker exists for the item, NegativeCacheException is thrown
    Path mavenCache = projectDir.resolve("build/test/maven-deps");
    PathTools.prune(mavenCache);

    MavenCacheProcess process = new MavenCacheProcess(output, mavenCache.toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    // Create the directory and .neg marker
    Path negFile = mavenCache.resolve("org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0-src.jar.neg");
    Files.createDirectories(negFile.getParent());
    Files.createFile(negFile);
    try {
      ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
          artifact.version.toString(), artifact.getArtifactSourceFile());
      process.fetch(item, null);
      fail("Expected NegativeCacheException");
    } catch (NegativeCacheException e) {
      // Expected
    } finally {
      PathTools.prune(mavenCache);
    }
  }

  @Test
  public void fetch_noNegativeCache() throws Exception {
    // When no .neg marker exists and the item is missing, null is returned (not an exception)
    Path mavenCache = projectDir.resolve("build/test/maven-deps");
    PathTools.prune(mavenCache);

    MavenCacheProcess process = new MavenCacheProcess(output, mavenCache.toString());
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
        artifact.version.toString(), artifact.getArtifactSourceFile());
    FetchResult result = process.fetch(item, null);
    assertNull(result);
  }
}
