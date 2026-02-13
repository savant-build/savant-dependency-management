/*
 * Copyright (c) 2022-2024, Inversoft Inc., All Rights Reserved
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
import java.util.Collections;
import java.util.List;

import org.savantbuild.dep.BaseUnitTest;
import org.savantbuild.dep.PathTools;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.ReifiedArtifact;
import org.savantbuild.dep.workflow.ArtifactMetaDataMissingException;
import org.savantbuild.dep.workflow.FetchWorkflow;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.workflow.Workflow;
import org.savantbuild.domain.Version;
import org.testng.annotations.Test;

import com.sun.net.httpserver.HttpServer;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the Workflow for fetching artifacts, specifically the Maven handling.
 *
 * @author Brian Pontarelli
 */
public class WorkflowTest extends BaseUnitTest {
  @Test
  public void fetchSource_publish_source_file_does_not_exist() throws Exception {
    // arrange
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), cache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
        ),
        output
    );

    Artifact artifact = new ReifiedArtifact("org.apache.groovy:groovy:4.0.5", License.Licenses.get("Apache-2.0"));

    // act
    var sourcePath = workflow.fetchSource(artifact);

    // assert
    assertNull(sourcePath);
  }

  @Test
  public void fetchSource_publish_source_file_exists() throws Exception {
    // arrange
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), cache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), cache.toString())
        ),
        output
    );

    Artifact artifact = new ReifiedArtifact("org.apache.groovy:groovy:4.0.5", License.Licenses.get("Apache-2.0"));

    // act
    var sourcePath = workflow.fetchSource(artifact);

    // assert
    // expect src, not sources, because src is what will get published to the cache
    // and that will result in less noisy output for things like the IDEA plugin
    assertEquals(sourcePath.toString(), "../savant-dependency-management/build/test/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5-src.jar");
  }

  @Test
  public void fetchSource_publish_source_file_semantic_mapping_exists() throws IOException {
    // arrange
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), cache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), cache.toString())
        ),
        output
    );

    Artifact artifact = new ReifiedArtifact(new ArtifactID("org.xerial.snappy:snappy-java:snappy-java:jar"),
        new Version("1.1.10+5"),
        "1.1.10.5",
        List.of(License.Licenses.get("Apache-2.0")));

    // act
    var sourcePath = workflow.fetchSource(artifact);

    // assert
    // expect src, not sources, because src is what will get published to the cache
    // and that will result in less noisy output for things like the IDEA plugin
    assertEquals(sourcePath.toString(), "../savant-dependency-management/build/test/cache/org/xerial/snappy/snappy-java/1.1.10+5/snappy-java-1.1.10+5-src.jar");
  }

  @Test
  public void mavenCentral() throws Exception {
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), cache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), cache.toString())
        ),
        output
    );

    Artifact artifact = new ReifiedArtifact("org.apache.groovy:groovy:4.0.5", License.Licenses.get("Apache-2.0"));
    ArtifactMetaData amd = workflow.fetchMetaData(artifact);
    assertNotNull(amd);

    // Everything is optional, so it should be an empty dependencies
    Dependencies expected = new Dependencies();
    assertEquals(amd.dependencies, expected);

    // POMs are still fetched and cached by the fetch/publish workflow
    assertTrue(Files.isRegularFile(Paths.get("build/test/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.pom")));
    assertTrue(Files.isRegularFile(Paths.get("build/test/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.pom.md5")));

    // AMDs are NOT written to disk for Maven artifacts -- they are generated in-memory only
    assertFalse(Files.exists(Paths.get("build/test/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar.amd")));
    assertFalse(Files.exists(Paths.get("build/test/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar.amd.md5")));
  }

  @Test
  public void mavenCentralMapping() throws Exception {
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), cache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), cache.toString())
        ),
        output
    );
    workflow.mappings.put("io.netty:netty-all:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-buffer:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-codec-http:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-codec-http2:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-common:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-handler:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-handler-proxy:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-resolver:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-resolver-dns:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-tcnative-boringssl-static:2.0.39.Final", new Version("2.0.39"));
    workflow.mappings.put("io.netty:netty-transport:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-transport-native-epoll:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-transport-native-kqueue:4.1.65.Final", new Version("4.1.65"));

    Artifact artifact = new ReifiedArtifact("io.vertx:vertx-core:3.9.8", License.Licenses.get("Apache-2.0"));
    ArtifactMetaData amd = workflow.fetchMetaData(artifact);
    assertNotNull(amd);

    Dependencies expected = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("io.netty:netty-common:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-buffer:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-transport:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-handler:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-handler-proxy:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-codec-http:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-codec-http2:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-resolver:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-resolver-dns:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("com.fasterxml.jackson.core:jackson-core:2.11.3"),
            new Artifact("com.fasterxml.jackson.core:jackson-databind:2.11.3")
        )
    );
    assertEquals(amd.dependencies, expected);

    // POMs are still fetched and cached by the fetch/publish workflow
    assertTrue(Files.isRegularFile(Paths.get("build/test/cache/io/vertx/vertx-core/3.9.8/vertx-core-3.9.8.pom")));
    assertTrue(Files.isRegularFile(Paths.get("build/test/cache/io/vertx/vertx-core/3.9.8/vertx-core-3.9.8.pom.md5")));

    // AMDs are NOT written to disk for Maven artifacts -- they are generated in-memory only
    assertFalse(Files.exists(Paths.get("build/test/cache/io/vertx/vertx-core/3.9.8/vertx-core-3.9.8.jar.amd")));
    assertFalse(Files.exists(Paths.get("build/test/cache/io/vertx/vertx-core/3.9.8/vertx-core-3.9.8.jar.amd.md5")));
  }

  @Test
  public void fetchMetaData_inMemoryCache() throws Exception {
    // arrange
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), cache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), cache.toString())
        ),
        output
    );

    Artifact artifact = new ReifiedArtifact("org.apache.groovy:groovy:4.0.5", License.Licenses.get("Apache-2.0"));

    // act - fetch twice
    ArtifactMetaData amd1 = workflow.fetchMetaData(artifact);
    ArtifactMetaData amd2 = workflow.fetchMetaData(artifact);

    // assert - both calls return the same cached instance
    assertNotNull(amd1);
    assertSame(amd1, amd2, "Second fetchMetaData call should return the same in-memory cached instance");
  }

  @Test
  public void fetchMetaData_mappingsAppliedFresh() throws Exception {
    // Verifies that the stale-cache problem is fixed: mappings from the build file are applied
    // fresh on every build (every new Workflow instance), not baked into a cached AMD file.
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    // First workflow with no mappings for netty
    Workflow workflow1 = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), cache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), cache.toString())
        ),
        output
    );
    workflow1.mappings.put("io.netty:netty-common:4.1.65.Final", new Version("4.1.65"));
    workflow1.mappings.put("io.netty:netty-buffer:4.1.65.Final", new Version("4.1.65"));
    workflow1.mappings.put("io.netty:netty-transport:4.1.65.Final", new Version("4.1.65"));
    workflow1.mappings.put("io.netty:netty-handler:4.1.65.Final", new Version("4.1.65"));
    workflow1.mappings.put("io.netty:netty-handler-proxy:4.1.65.Final", new Version("4.1.65"));
    workflow1.mappings.put("io.netty:netty-codec-http:4.1.65.Final", new Version("4.1.65"));
    workflow1.mappings.put("io.netty:netty-codec-http2:4.1.65.Final", new Version("4.1.65"));
    workflow1.mappings.put("io.netty:netty-resolver:4.1.65.Final", new Version("4.1.65"));
    workflow1.mappings.put("io.netty:netty-resolver-dns:4.1.65.Final", new Version("4.1.65"));

    Artifact artifact = new ReifiedArtifact("io.vertx:vertx-core:3.9.8", License.Licenses.get("Apache-2.0"));
    ArtifactMetaData amd1 = workflow1.fetchMetaData(artifact);
    assertNotNull(amd1);

    // Verify the mapping was applied (netty-common should be 4.1.65)
    Artifact nettyCommon = amd1.dependencies.groups.get("compile").dependencies.get(0);
    assertEquals(nettyCommon.version, new Version("4.1.65"));

    // Second workflow with DIFFERENT mappings -- should see the new version
    Workflow workflow2 = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), cache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), cache.toString())
        ),
        output
    );
    workflow2.mappings.put("io.netty:netty-common:4.1.65.Final", new Version("4.1.100"));
    workflow2.mappings.put("io.netty:netty-buffer:4.1.65.Final", new Version("4.1.100"));
    workflow2.mappings.put("io.netty:netty-transport:4.1.65.Final", new Version("4.1.100"));
    workflow2.mappings.put("io.netty:netty-handler:4.1.65.Final", new Version("4.1.100"));
    workflow2.mappings.put("io.netty:netty-handler-proxy:4.1.65.Final", new Version("4.1.100"));
    workflow2.mappings.put("io.netty:netty-codec-http:4.1.65.Final", new Version("4.1.100"));
    workflow2.mappings.put("io.netty:netty-codec-http2:4.1.65.Final", new Version("4.1.100"));
    workflow2.mappings.put("io.netty:netty-resolver:4.1.65.Final", new Version("4.1.100"));
    workflow2.mappings.put("io.netty:netty-resolver-dns:4.1.65.Final", new Version("4.1.100"));

    ArtifactMetaData amd2 = workflow2.fetchMetaData(artifact);
    assertNotNull(amd2);

    // With the old behavior (disk-cached AMDs), this would STILL return 4.1.65 (stale cache).
    // With in-memory AMDs, the fresh mappings are applied and netty-common should be 4.1.100.
    Artifact nettyCommon2 = amd2.dependencies.groups.get("compile").dependencies.get(0);
    assertEquals(nettyCommon2.version, new Version("4.1.100"));
  }

  /**
   * Performance test: verifies that in-memory AMD generation from POMs is fast enough.
   * <p>
   * Resolves io.vertx:vertx-core:3.9.8 which exercises:
   * - Parent POM resolution (vertx-parent)
   * - BOM imports (vertx-dependencies with 100+ entries)
   * - 11 dependency mappings (netty artifacts)
   * - Property variable replacement
   * <p>
   * This is a representative "complex artifact" case. The test asserts:
   * 1. First call (POM parse + translate) completes in < 2 seconds
   * 2. Second call (in-memory cache hit) completes in < 5 milliseconds
   */
  @Test
  public void fetchMetaData_performance() throws Exception {
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), cache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), cache.toString())
        ),
        output
    );
    workflow.mappings.put("io.netty:netty-all:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-buffer:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-codec-http:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-codec-http2:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-common:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-handler:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-handler-proxy:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-resolver:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-resolver-dns:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-tcnative-boringssl-static:2.0.39.Final", new Version("2.0.39"));
    workflow.mappings.put("io.netty:netty-transport:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-transport-native-epoll:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-transport-native-kqueue:4.1.65.Final", new Version("4.1.65"));

    Artifact artifact = new ReifiedArtifact("io.vertx:vertx-core:3.9.8", License.Licenses.get("Apache-2.0"));

    // First call: POM parse + parent resolution + BOM imports + translation
    long startFirst = System.nanoTime();
    ArtifactMetaData amd1 = workflow.fetchMetaData(artifact);
    long firstCallMs = (System.nanoTime() - startFirst) / 1_000_000;

    assertNotNull(amd1);
    // Complex artifact with parent chain + BOM should still resolve in < 2 seconds
    assertTrue(firstCallMs < 2000,
        "First fetchMetaData (POM parse + translate) took " + firstCallMs + "ms, expected < 2000ms");

    // Second call: in-memory cache hit -- should be near-instant
    long startSecond = System.nanoTime();
    ArtifactMetaData amd2 = workflow.fetchMetaData(artifact);
    long secondCallMs = (System.nanoTime() - startSecond) / 1_000_000;

    assertSame(amd1, amd2);
    assertTrue(secondCallMs < 5,
        "Second fetchMetaData (cache hit) took " + secondCallMs + "ms, expected < 5ms");

    System.out.println("[Performance] vertx-core fetchMetaData: first call = " + firstCallMs + "ms, cached call = " + secondCallMs + "ms");
  }

  @Test
  public void fetchMetaData_savantNativeAmd() throws Exception {
    // Savant-native AMD path: pre-built AMD file exists in the Savant repo,
    // fetchMetaData parses it directly (no POM loading fallback)
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    HttpServer server = makeFileServer(null, null);
    try {
      Workflow workflow = new Workflow(
          new FetchWorkflow(
              output,
              new CacheProcess(output, cache.toString(), cache.toString()),
              new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null)
          ),
          new PublishWorkflow(
              new CacheProcess(output, cache.toString(), cache.toString())
          ),
          output
      );

      // leaf1 is a Savant-native artifact with a pre-built AMD file
      Artifact artifact = new ReifiedArtifact(
          new ArtifactID("org.savantbuild.test", "leaf1", "leaf1", "jar"),
          new Version("1.0.0"),
          List.of(License.parse("Commercial", "Commercial license")));
      ArtifactMetaData amd = workflow.fetchMetaData(artifact);
      assertNotNull(amd);

      // Savant-native AMD should be cached on disk (via publish workflow)
      assertTrue(Files.isRegularFile(cache.resolve("org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar.amd")));
      assertTrue(Files.isRegularFile(cache.resolve("org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar.amd.md5")));

      // In-memory cache should return the same instance on second call
      ArtifactMetaData amd2 = workflow.fetchMetaData(artifact);
      assertSame(amd, amd2);
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void fetchMetaData_savantNativeAmd_withDependencies() throws Exception {
    // Savant-native AMD with compile and runtime dependency groups
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    HttpServer server = makeFileServer(null, null);
    try {
      Workflow workflow = new Workflow(
          new FetchWorkflow(
              output,
              new CacheProcess(output, cache.toString(), cache.toString()),
              new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null)
          ),
          new PublishWorkflow(
              new CacheProcess(output, cache.toString(), cache.toString())
          ),
          output
      );

      // intermediate AMD has compile dep (multiple-versions) and runtime dep (multiple-versions-different-dependencies)
      Artifact artifact = new ReifiedArtifact(
          new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar"),
          new Version("1.0.0"),
          List.of(License.Licenses.get("ApacheV2_0")));
      ArtifactMetaData amd = workflow.fetchMetaData(artifact);
      assertNotNull(amd);
      assertNotNull(amd.dependencies);

      assertNotNull(amd.dependencies.groups.get("compile"));
      assertEquals(amd.dependencies.groups.get("compile").dependencies.size(), 1);
      assertEquals(amd.dependencies.groups.get("compile").dependencies.get(0).id.project, "multiple-versions");

      assertNotNull(amd.dependencies.groups.get("runtime"));
      assertEquals(amd.dependencies.groups.get("runtime").dependencies.size(), 1);
      assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).id.project, "multiple-versions-different-dependencies");
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void fetchMetaData_missingAmdAndPom() throws Exception {
    // Neither a Savant AMD file nor a Maven POM can be found -- should throw
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    // Workflow with only CacheProcess pointing at an empty cache -- no remote processes
    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), cache.toString())
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), cache.toString())
        ),
        output
    );

    Artifact artifact = new ReifiedArtifact("com.nonexistent:artifact:1.0.0", License.Licenses.get("Apache-2.0"));
    try {
      workflow.fetchMetaData(artifact);
      fail("Should have thrown ArtifactMetaDataMissingException");
    } catch (ArtifactMetaDataMissingException e) {
      assertEquals(e.artifactMissingAMD, artifact);
    }
  }

  @Test
  public void fetchMetaData_missingAmdInSavantRepo_noPomFallback() throws Exception {
    // Savant repo serves the JAR but has no AMD file, and there is no Maven POM either.
    // Uses the missing-amd test fixture which has a JAR + MD5 but no .amd file.
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    HttpServer server = makeFileServer(null, null);
    try {
      Workflow workflow = new Workflow(
          new FetchWorkflow(
              output,
              new CacheProcess(output, cache.toString(), cache.toString()),
              new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null)
          ),
          new PublishWorkflow(
              new CacheProcess(output, cache.toString(), cache.toString())
          ),
          output
      );

      Artifact artifact = new ReifiedArtifact(
          new ArtifactID("org.savantbuild.test", "missing-amd", "missing-amd", "jar"),
          new Version("1.0.0"),
          List.of(License.Licenses.get("Apache-2.0")));
      try {
        workflow.fetchMetaData(artifact);
        fail("Should have thrown ArtifactMetaDataMissingException");
      } catch (ArtifactMetaDataMissingException e) {
        assertEquals(e.artifactMissingAMD, artifact);
      }
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void fetchMetaData_pomCacheSharesParents() throws Exception {
    // Two artifacts sharing the same parent POM chain should both resolve correctly.
    // vertx-core and vertx-web both descend from vertx-parent -> oss-parent and import
    // the vertx-dependencies BOM. The POM cache should parse each parent once and reuse.
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), cache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), cache.toString())
        ),
        output
    );
    workflow.mappings.put("io.netty:netty-common:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-buffer:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-transport:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-handler:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-handler-proxy:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-codec-http:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-codec-http2:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-resolver:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-resolver-dns:4.1.65.Final", new Version("4.1.65"));

    Artifact vertxCore = new ReifiedArtifact("io.vertx:vertx-core:3.9.8", License.Licenses.get("Apache-2.0"));
    ArtifactMetaData amd1 = workflow.fetchMetaData(vertxCore);
    assertNotNull(amd1);
    assertNotNull(amd1.dependencies.groups.get("compile"));

    Artifact vertxWeb = new ReifiedArtifact("io.vertx:vertx-web:3.9.8", License.Licenses.get("Apache-2.0"));
    ArtifactMetaData amd2 = workflow.fetchMetaData(vertxWeb);
    assertNotNull(amd2);

    // vertx-web should have vertx-core as a compile dependency
    boolean hasVertxCore = amd2.dependencies.groups.get("compile").dependencies.stream()
        .anyMatch(d -> d.id.project.equals("vertx-core"));
    assertTrue(hasVertxCore, "vertx-web should have vertx-core as a compile dependency");

    // Different artifacts, different AMD instances
    assertNotSame(amd1, amd2);
  }

  @Test
  public void fetchMetaData_amdCacheKeyIncludesName() throws Exception {
    // Regression test: two artifacts sharing group:project:version but with different names
    // must produce different AMD results. The AMD cache key must include the artifact name.
    // leaf:leaf1 (GPL-2.0) and leaf:leaf2 (LGPL-2.1) share group=org.savantbuild.test,
    // project=leaf, version=1.0.0 but have different AMD files with different licenses.
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    HttpServer server = makeFileServer(null, null);
    try {
      Workflow workflow = new Workflow(
          new FetchWorkflow(
              output,
              new CacheProcess(output, cache.toString(), cache.toString()),
              new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null)
          ),
          new PublishWorkflow(
              new CacheProcess(output, cache.toString(), cache.toString())
          ),
          output
      );

      Artifact leaf1 = new ReifiedArtifact(
          new ArtifactID("org.savantbuild.test", "leaf", "leaf1", "jar"),
          new Version("1.0.0"),
          List.of(License.Licenses.get("GPLV2_0")));
      Artifact leaf2 = new ReifiedArtifact(
          new ArtifactID("org.savantbuild.test", "leaf", "leaf2", "jar"),
          new Version("1.0.0"),
          List.of(License.Licenses.get("LGPLV2_1")));

      ArtifactMetaData amd1 = workflow.fetchMetaData(leaf1);
      ArtifactMetaData amd2 = workflow.fetchMetaData(leaf2);

      assertNotNull(amd1);
      assertNotNull(amd2);
      // Must be different instances with different licenses (not a cache collision)
      assertNotSame(amd1, amd2);
      assertFalse(amd1.licenses.equals(amd2.licenses),
          "leaf1 and leaf2 should have different licenses but got the same AMD (cache key collision)");
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void fetchSource_negativeCacheShortCircuit() throws Exception {
    // When a source JAR doesn't exist, a .neg marker file is created.
    // On subsequent calls, the .neg file short-circuits the fetch and returns null
    // immediately without attempting remote lookups.
    HttpServer server = makeFileServer(null, null);
    try {
      Path cache = projectDir.resolve("build/test/cache");
      PathTools.prune(cache);

      Workflow workflow = new Workflow(
          new FetchWorkflow(
              output,
              new CacheProcess(output, cache.toString(), cache.toString()),
              new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null)
          ),
          new PublishWorkflow(
              new CacheProcess(output, cache.toString(), cache.toString())
          ),
          output
      );

      // leaf1 has no source JAR in the test fixtures
      Artifact leaf1 = new ReifiedArtifact(
          new ArtifactID("org.savantbuild.test", "leaf1", "leaf1", "jar"),
          new Version("1.0.0"),
          List.of(License.parse("Commercial", "Commercial license")));

      // First call -- searches, doesn't find source, creates .neg marker
      Path sourceResult1 = workflow.fetchSource(leaf1);
      assertNull(sourceResult1);

      // Verify .neg file was created
      Path negMarker = cache.resolve("org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0-src.jar.neg");
      assertTrue(Files.isRegularFile(negMarker),
          "Negative cache marker should exist at " + negMarker);

      // Second call -- hits the .neg marker and returns null immediately
      Path sourceResult2 = workflow.fetchSource(leaf1);
      assertNull(sourceResult2);
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void fetchMetaData_cacheOnlyWorkflow() throws Exception {
    // Savant-only workflow (CacheProcess + URLProcess, no Maven processes).
    // Verifies Savant-native AMD resolution works without any Maven fallback.
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    HttpServer server = makeFileServer(null, null);
    try {
      Workflow workflow = new Workflow(
          new FetchWorkflow(
              output,
              new CacheProcess(output, cache.toString(), cache.toString()),
              new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null)
          ),
          new PublishWorkflow(
              new CacheProcess(output, cache.toString(), cache.toString())
          ),
          output
      );

      Artifact artifact = new ReifiedArtifact(
          new ArtifactID("org.savantbuild.test", "multiple-versions", "multiple-versions", "jar"),
          new Version("1.0.0"),
          List.of(License.Licenses.get("ApacheV1_0")));
      ArtifactMetaData amd = workflow.fetchMetaData(artifact);
      assertNotNull(amd);
      assertNotNull(amd.dependencies.groups.get("compile"));

      // AMD should be cached on disk
      assertTrue(Files.isRegularFile(cache.resolve(
          "org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar.amd")));

      // Second call should use in-memory cache
      ArtifactMetaData amd2 = workflow.fetchMetaData(artifact);
      assertSame(amd, amd2);
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void fetchMetaData_mavenOnlyWorkflow() throws Exception {
    // Maven-only workflow (MavenCacheProcess + MavenProcess, no Savant CacheProcess or URLProcess).
    // Verifies POM-to-AMD translation works without Savant-native AMD check.
    Path mavenCache = projectDir.resolve("build/test/maven-only-cache");
    PathTools.prune(mavenCache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new MavenCacheProcess(output, mavenCache.toString(), mavenCache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new MavenCacheProcess(output, mavenCache.toString(), mavenCache.toString())
        ),
        output
    );

    Artifact artifact = new ReifiedArtifact("org.apache.groovy:groovy:4.0.5", License.Licenses.get("Apache-2.0"));
    ArtifactMetaData amd = workflow.fetchMetaData(artifact);
    assertNotNull(amd);

    // Everything is optional in groovy POM, so empty dependencies
    Dependencies expected = new Dependencies();
    assertEquals(amd.dependencies, expected);

    // No AMD file on disk (Maven path generates in-memory)
    assertFalse(Files.exists(mavenCache.resolve("org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar.amd")));

    // POM should be cached in the maven cache
    assertTrue(Files.isRegularFile(mavenCache.resolve("org/apache/groovy/groovy/4.0.5/groovy-4.0.5.pom")));

    // In-memory cache should still work
    ArtifactMetaData amd2 = workflow.fetchMetaData(artifact);
    assertSame(amd, amd2);
  }

  @Test
  public void fetchMetaData_twoPathResolution_savantThenMaven() throws Exception {
    // Full two-path resolution in a single workflow: Savant-native artifact uses pre-built AMD
    // (cached to disk), Maven artifact falls through to POM-to-AMD translation (in-memory only).
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    HttpServer server = makeFileServer(null, null);
    try {
      Workflow workflow = new Workflow(
          new FetchWorkflow(
              output,
              new CacheProcess(output, cache.toString(), cache.toString()),
              new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null),
              new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
          ),
          new PublishWorkflow(
              new CacheProcess(output, cache.toString(), cache.toString())
          ),
          output
      );

      // 1. Savant-native artifact: leaf1 has a pre-built AMD in test-deps/savant
      Artifact savantArtifact = new ReifiedArtifact(
          new ArtifactID("org.savantbuild.test", "leaf1", "leaf1", "jar"),
          new Version("1.0.0"),
          List.of(License.parse("Commercial", "Commercial license")));
      ArtifactMetaData savantAmd = workflow.fetchMetaData(savantArtifact);
      assertNotNull(savantAmd);

      // AMD file should exist on disk (Savant-native path caches to disk)
      assertTrue(Files.isRegularFile(cache.resolve(
          "org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar.amd")));

      // 2. Maven artifact: groovy has no AMD in the Savant repo, falls through to POM
      Artifact mavenArtifact = new ReifiedArtifact("org.apache.groovy:groovy:4.0.5", License.Licenses.get("Apache-2.0"));
      ArtifactMetaData mavenAmd = workflow.fetchMetaData(mavenArtifact);
      assertNotNull(mavenAmd);

      // AMD file should NOT exist on disk (Maven path is in-memory only)
      assertFalse(Files.exists(cache.resolve(
          "org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar.amd")));

      // POM should exist on disk (fetched and cached normally)
      assertTrue(Files.isRegularFile(cache.resolve(
          "org/apache/groovy/groovy/4.0.5/groovy-4.0.5.pom")));
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void fetchMetaData_nonSemanticVersionPom() throws Exception {
    // Non-semantic version POM resolution: loadPOM falls back to the non-semantic version
    // when the semantic version POM is not found. Uses the badver test fixture which has
    // version 1.0.0.Final (non-semantic) mapped to 1.0.0 (semantic).
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    HttpServer server = makeFileServer(null, null);
    try {
      Workflow workflow = new Workflow(
          new FetchWorkflow(
              output,
              new CacheProcess(output, cache.toString(), cache.toString()),
              new URLProcess(output, "http://localhost:7042/test-deps/maven", null, null)
          ),
          new PublishWorkflow(
              new CacheProcess(output, cache.toString(), cache.toString())
          ),
          output
      );

      // badver:1.0.0.Final is mapped to semantic version 1.0.0
      Artifact artifact = new ReifiedArtifact(
          new ArtifactID("org.savantbuild.test:badver:badver:jar"),
          new Version("1.0.0"),
          "1.0.0.Final",
          List.of(License.Licenses.get("Apache-2.0")));
      ArtifactMetaData amd = workflow.fetchMetaData(artifact);
      assertNotNull(amd);

      // In-memory cache should work for non-semantic version artifacts too
      ArtifactMetaData amd2 = workflow.fetchMetaData(artifact);
      assertSame(amd, amd2);
    } finally {
      server.stop(0);
    }
  }
}
