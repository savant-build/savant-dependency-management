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
import org.savantbuild.dep.workflow.FetchWorkflow;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.workflow.Workflow;
import org.savantbuild.domain.Version;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

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
}
