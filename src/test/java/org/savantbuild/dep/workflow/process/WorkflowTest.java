/*
 * Copyright (c) 2022, Inversoft Inc., All Rights Reserved
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
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

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
import static org.testng.Assert.assertTrue;

/**
 * Tests the Workflow for fetching artifacts, specifically the Maven handling.
 *
 * @author Brian Pontarelli
 */
public class WorkflowTest extends BaseUnitTest {
  @Test
  public void mavenCentral() throws Exception {
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString())
        ),
        output
    );

    Artifact artifact = new ReifiedArtifact("org.apache.groovy:groovy:4.0.5", License.Licenses.get("Apache-2.0"));
    ArtifactMetaData amd = workflow.fetchMetaData(artifact);
    assertNotNull(amd);

    Dependencies expected = new Dependencies(
        new DependencyGroup("compile-optional", true,
            new Artifact("org.codehaus.gpars:gpars:1.2.1", null, false,
                Collections.singletonList(
                    new ArtifactID("org.codehaus.groovy:groovy-all:*")
                )
            ),
            new Artifact("org.apache.ivy:ivy:2.5.0", null, false,
                Collections.singletonList(
                    new ArtifactID("*:*:*")
                )
            ),
            new Artifact("com.thoughtworks.xstream:xstream:1.4.19", null, false,
                Arrays.asList(
                    new ArtifactID("junit:junit:*"),
                    new ArtifactID("xpp3:xpp3_min:*"),
                    new ArtifactID("xmlpull:xmlpull:*"),
                    new ArtifactID("jmock:jmock:*")
                )
            ),
            new Artifact("org.fusesource.jansi:jansi:2.4.0")
        )
    );
    assertEquals(amd.dependencies, expected);

    assertTrue(Files.isRegularFile(Paths.get("build/test/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.pom")));
    assertTrue(Files.isRegularFile(Paths.get("build/test/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.pom.md5")));

    // AMDs are not written to the cache. They are stored in memory
    assertFalse(Files.isRegularFile(Paths.get("build/test/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar.amd")));
    assertFalse(Files.isRegularFile(Paths.get("build/test/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar.amd.md5")));
  }

  @Test
  public void mavenCentralMapping() throws Exception {
    Path cache = projectDir.resolve("build/test/cache");
    PathTools.prune(cache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString())
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
        ),
        new DependencyGroup("compile-optional", true,
            new Artifact("io.netty:netty-transport-native-epoll:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-transport-native-kqueue:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.vertx:vertx-codegen:3.9.8"),
            new Artifact("io.vertx:vertx-docgen:0.9.2")
        )
    );
    assertEquals(amd.dependencies, expected);

    assertTrue(Files.isRegularFile(Paths.get("build/test/cache/io/vertx/vertx-core/3.9.8/vertx-core-3.9.8.pom")));
    assertTrue(Files.isRegularFile(Paths.get("build/test/cache/io/vertx/vertx-core/3.9.8/vertx-core-3.9.8.pom.md5")));

    // AMDs are not written to the cache. They are stored in memory
    assertFalse(Files.isRegularFile(Paths.get("build/test/cache/io/vertx/vertx-core/3.9.8/vertx-core-3.9.8.jar.amd")));
    assertFalse(Files.isRegularFile(Paths.get("build/test/cache/io/vertx/vertx-core/3.9.8/vertx-core-3.9.8.jar.amd.md5")));
  }
}
