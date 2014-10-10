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
package org.savantbuild.dep;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

import org.savantbuild.dep.DependencyService.TraversalRules;
import org.savantbuild.dep.DependencyService.TraversalRules.GroupTraversalRule;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.CompatibilityException;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.Publication;
import org.savantbuild.dep.domain.ReifiedArtifact;
import org.savantbuild.dep.domain.ResolvedArtifact;
import org.savantbuild.dep.domain.Version;
import org.savantbuild.dep.graph.ArtifactGraph;
import org.savantbuild.dep.graph.DependencyEdgeValue;
import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.DependencyGraph.Dependency;
import org.savantbuild.dep.graph.ResolvedArtifactGraph;
import org.savantbuild.dep.workflow.ArtifactMetaDataMissingException;
import org.savantbuild.dep.workflow.ArtifactMissingException;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.workflow.process.CacheProcess;
import org.savantbuild.security.MD5;
import org.savantbuild.security.MD5Exception;
import org.savantbuild.util.MapBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.sun.net.httpserver.HttpServer;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the default dependency service.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class DefaultDependencyServiceTest extends BaseUnitTest {
  public Dependencies dependencies;

  public DependencyGraph goodGraph;

  public ArtifactGraph goodReducedGraph;

  public ReifiedArtifact integrationBuild = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "integration-build", "integration-build", "jar"), new Version("2.1.1-{integration}"), MapBuilder.simpleMap(License.ApacheV2_0, null));

  public ReifiedArtifact intermediate = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.ApacheV2_0, null));

  public ReifiedArtifact leaf1 = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "leaf", "leaf1", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.GPLV2_0, null));

  public ReifiedArtifact leaf1_1 = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "leaf1", "leaf1", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.Commercial, "Commercial license"));

  public ReifiedArtifact leaf2 = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "leaf", "leaf2", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.LGPLV2_1, null));

  public ReifiedArtifact leaf2_2 = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "leaf2", "leaf2", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.OtherNonDistributableOpenSource, "Open source"));

  public ReifiedArtifact leaf3_3 = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "leaf3", "leaf3", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.ApacheV2_0, null));

  public ReifiedArtifact multipleVersions = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "multiple-versions", "multiple-versions", "jar"), new Version("1.1.0"), MapBuilder.simpleMap(License.ApacheV2_0, null));

  public ReifiedArtifact multipleVersionsDifferentDeps = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar"), new Version("1.1.0"), MapBuilder.simpleMap(License.ApacheV2_0, null));

  public ReifiedArtifact project = new ReifiedArtifact("org.savantbuild.test:project:1.0", MapBuilder.simpleMap(License.ApacheV2_0, null));

  public ResolvedArtifact projectResolved = new ResolvedArtifact(project.id, project.version, MapBuilder.simpleMap(License.ApacheV2_0, null), null, null);

  public ResolvedArtifact resolvedIntegrationBuild;

  public ResolvedArtifact resolvedIntermediate;

  public ResolvedArtifact resolvedLeaf1;

  public ResolvedArtifact resolvedLeaf1_1;

  public ResolvedArtifact resolvedLeaf2_2;

  public ResolvedArtifact resolvedLeaf3_3;

  public ResolvedArtifact resolvedMultipleVersions;

  public ResolvedArtifact resolvedMultipleVersionsDifferentDeps;

  public HttpServer server;

  public DefaultDependencyService service = new DefaultDependencyService(output);

  @AfterMethod
  public void afterMethodStopFileServer() {
    server.stop(0);
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf1
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)leaf:leaf1
   *              |              ^           (1.0.0)-->(2.1.1-{integration})integration-build
   *              |              |           (1.1.0)-->(2.1.1-{integration})integration-build
   *              |              |
   *              |->(1.0.0)intermediate
   *              |              |
   *              |             \/
   *              |          (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies(1.0.0)-->(1.0.0)leaf:leaf2
   *              |                                                 (1.0.0,1.1.0)-->(1.0.0)leaf1:leaf1
   *              |                                                 (1.1.0)-->(1.0.0)leaf2:leaf2
   *              |                                                 (1.1.0)-->(1.0.0)leaf3:leaf3 (optional)
   * </pre>
   */
  @BeforeClass
  public void beforeClass() {
    goodGraph = new DependencyGraph(project);
    goodGraph.addEdge(new Dependency(project.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.ApacheV1_0, null)));
    goodGraph.addEdge(new Dependency(project.id), new Dependency(intermediate.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.ApacheV2_0, null)));
    goodGraph.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.ApacheV2_0, null)));
    goodGraph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", MapBuilder.simpleMap(License.ApacheV2_0, null)));
    goodGraph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime", MapBuilder.simpleMap(License.ApacheV2_0, null)));
    goodGraph.addEdge(new Dependency(multipleVersions.id), new Dependency(leaf1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.GPLV2_0 , null)));
    goodGraph.addEdge(new Dependency(multipleVersions.id), new Dependency(leaf1.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.GPLV2_0, null)));
    goodGraph.addEdge(new Dependency(multipleVersions.id), new Dependency(integrationBuild.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("2.1.1-{integration}"), "compile", MapBuilder.simpleMap(License.ApacheV2_0, null)));
    goodGraph.addEdge(new Dependency(multipleVersions.id), new Dependency(integrationBuild.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.1.1-{integration}"), "compile", MapBuilder.simpleMap(License.ApacheV2_0, null)));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf2.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.LGPLV2_1, null)));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, "Commercial license")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, "Commercial license")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf2_2.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.OtherNonDistributableOpenSource, "Open source")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf3_3.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.ApacheV2_0, null)));

    goodReducedGraph = new ArtifactGraph(project);
    goodReducedGraph.addEdge(project, multipleVersions, "compile");
    goodReducedGraph.addEdge(project, intermediate, "runtime");
    goodReducedGraph.addEdge(project, multipleVersionsDifferentDeps, "compile");
    goodReducedGraph.addEdge(intermediate, multipleVersions, "compile");
    goodReducedGraph.addEdge(intermediate, multipleVersionsDifferentDeps, "runtime");
    goodReducedGraph.addEdge(multipleVersions, leaf1, "compile");
    goodReducedGraph.addEdge(multipleVersions, integrationBuild, "compile");
    goodReducedGraph.addEdge(multipleVersionsDifferentDeps, leaf1_1, "compile");
    goodReducedGraph.addEdge(multipleVersionsDifferentDeps, leaf1_1, "compile");
    goodReducedGraph.addEdge(multipleVersionsDifferentDeps, leaf2_2, "compile");
    goodReducedGraph.addEdge(multipleVersionsDifferentDeps, leaf3_3, "runtime");

    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact(multipleVersions.id, new Version("1.0.0"), false),
            new Artifact(multipleVersionsDifferentDeps.id, new Version("1.0.0"), false)
        ),
        new DependencyGroup("runtime", true,
            new Artifact(intermediate.id, new Version("1.0.0"), false)
        )
    );


    resolvedIntermediate = new ResolvedArtifact("org.savantbuild.test:intermediate:1.0.0", MapBuilder.simpleMap(License.ApacheV2_0, null), cache.resolve("org/savantbuild/test/intermediate/1.0.0/intermediate-1.0.0.jar").toAbsolutePath(), null);
    resolvedMultipleVersions = new ResolvedArtifact("org.savantbuild.test:multiple-versions:1.1.0", MapBuilder.simpleMap(License.ApacheV2_0, null), cache.resolve("org/savantbuild/test/multiple-versions/1.1.0/multiple-versions-1.1.0.jar").toAbsolutePath(), null);
    resolvedMultipleVersionsDifferentDeps = new ResolvedArtifact("org.savantbuild.test:multiple-versions-different-dependencies:1.1.0", MapBuilder.simpleMap(License.ApacheV2_0, null), cache.resolve("org/savantbuild/test/multiple-versions-different-dependencies/1.1.0/multiple-versions-different-dependencies-1.1.0.jar").toAbsolutePath(), null);
    resolvedLeaf1 = new ResolvedArtifact("org.savantbuild.test:leaf:leaf1:1.0.0:jar", MapBuilder.simpleMap(License.GPLV2_0, null), cache.resolve("org/savantbuild/test/leaf/1.0.0/leaf1-1.0.0.jar").toAbsolutePath(), null);
    resolvedLeaf1_1 = new ResolvedArtifact("org.savantbuild.test:leaf1:1.0.0", MapBuilder.simpleMap(License.Commercial, "Commercial license"), cache.resolve("org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar").toAbsolutePath(), null);
    resolvedLeaf2_2 = new ResolvedArtifact("org.savantbuild.test:leaf2:1.0.0", MapBuilder.simpleMap(License.OtherNonDistributableOpenSource, "Open source"), cache.resolve("org/savantbuild/test/leaf2/1.0.0/leaf2-1.0.0.jar").toAbsolutePath(), null);
    resolvedLeaf3_3 = new ResolvedArtifact("org.savantbuild.test:leaf3:1.0.0", MapBuilder.simpleMap(License.ApacheV2_0, null), cache.resolve("org/savantbuild/test/leaf3/1.0.0/leaf3-1.0.0.jar").toAbsolutePath(), null);
    resolvedIntegrationBuild = new ResolvedArtifact("org.savantbuild.test:integration-build:2.1.1-{integration}", MapBuilder.simpleMap(License.ApacheV2_0, null), cache.resolve("org/savantbuild/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar").toAbsolutePath(), null);
  }

  @BeforeMethod
  public void beforeMethodStartFileServer() throws IOException {
    server = makeFileServer(null, null);
    PathTools.prune(cache);
    assertFalse(Files.isDirectory(cache));
  }

  @Test
  public void buildGraph() throws Exception {
    DependencyGraph actual = service.buildGraph(project, dependencies, workflow);
    assertEquals(actual, goodGraph);
  }

  @Test
  public void buildGraphFailureBadAMDMD5() throws Exception {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.savantbuild.test:bad-amd-md5:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (MD5Exception e) {
      assertEquals(e.getMessage(), "MD5 mismatch when fetching item from [http://localhost:7000/test-deps/savant/org/savantbuild/test/bad-amd-md5/1.0.0/bad-amd-md5-1.0.0.jar.amd]");
    }
  }

  @Test
  public void buildGraphFailureMissingAMD() throws Exception {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.savantbuild.test:missing-amd:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (ArtifactMetaDataMissingException e) {
      assertEquals(e.artifactMissingAMD, new Artifact("org.savantbuild.test:missing-amd:1.0.0", false));
    }
  }

  @Test
  public void buildGraphFailureMissingDependency() throws Exception {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.savantbuild.test:missing:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (ArtifactMetaDataMissingException e) {
      assertEquals(e.artifactMissingAMD, new Artifact("org.savantbuild.test:missing:1.0.0", false));
    }
  }

  @Test
  public void buildGraphFailureMissingMD5() throws Exception {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.savantbuild.test:missing-md5:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (ArtifactMetaDataMissingException e) {
      assertEquals(e.artifactMissingAMD, new Artifact("org.savantbuild.test:missing-md5:1.0.0", false));
    }
  }

  @Test
  public void publishWithSource() throws IOException {
    PathTools.prune(projectDir.resolve("build/test/publish"));

    Artifact artifact = new Artifact("org.savantbuild.test:publication-with-source:1.0.0", false);
    ArtifactMetaData amd = new ArtifactMetaData(dependencies, MapBuilder.simpleMap(License.BSD_2_Clause, null));
    Publication publication = new Publication(artifact, amd, projectDir.resolve("src/test/java/org/savantbuild/dep/TestFile.txt"), projectDir.resolve("src/test/java/org/savantbuild/dep/TestFile.txt"));
    PublishWorkflow workflow = new PublishWorkflow(new CacheProcess(output, projectDir.resolve("build/test/publish").toString()));
    service.publish(publication, workflow);

    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.amd.md5")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.amd")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.md5")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0-src.jar.md5")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0-src.jar")));

    // Ensure the MD5 files are correct (these methods throw exceptions if they aren't
    MD5.load(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.amd.md5"));
    MD5.load(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.md5"));
    MD5.load(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0-src.jar.md5"));
  }

  @Test
  public void publishMissingFile() throws IOException {
    Artifact artifact = new Artifact("org.savantbuild.test:publication-with-source:1.0.0", false);
    ArtifactMetaData amd = new ArtifactMetaData(dependencies, MapBuilder.simpleMap(License.BSD_2_Clause, null));
    Publication publication = new Publication(artifact, amd, projectDir.resolve("MissingFile.txt"), null);
    PublishWorkflow workflow = new PublishWorkflow(new CacheProcess(output, projectDir.resolve("build/test/publish").toString()));
    try {
      service.publish(publication, workflow);
    } catch (PublishException e) {
      assertTrue(e.getMessage().contains("The publication file"));
    }
  }

  @Test
  public void publishMissingSourceFile() throws IOException {
    Artifact artifact = new Artifact("org.savantbuild.test:publication-with-source:1.0.0", false);
    ArtifactMetaData amd = new ArtifactMetaData(dependencies, MapBuilder.simpleMap(License.BSD_2_Clause, null));
    Publication publication = new Publication(artifact, amd, projectDir.resolve("src/test/java/org/savantbuild/dep/TestFile.txt"), projectDir.resolve("MissingFile.txt"));
    PublishWorkflow workflow = new PublishWorkflow(new CacheProcess(output, projectDir.resolve("build/test/publish").toString()));
    try {
      service.publish(publication, workflow);
    } catch (PublishException e) {
      assertTrue(e.getMessage().contains("The publication source file"));
    }
  }

  @Test
  public void publishWithoutSource() throws IOException {
    PathTools.prune(projectDir.resolve("build/test/publish"));

    Artifact artifact = new Artifact("org.savantbuild.test:publication-without-source:1.0.0", false);
    ArtifactMetaData amd = new ArtifactMetaData(dependencies, MapBuilder.simpleMap(License.BSD_2_Clause, null));
    Publication publication = new Publication(artifact, amd, projectDir.resolve("src/test/java/org/savantbuild/dep/TestFile.txt"), null);
    PublishWorkflow workflow = new PublishWorkflow(new CacheProcess(output, projectDir.resolve("build/test/publish").toString()));
    service.publish(publication, workflow);

    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0.jar.amd.md5")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0.jar.amd")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0.jar.md5")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0.jar")));
    assertFalse(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0-src.jar.md5")));
    assertFalse(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0-src.jar")));

    // Ensure the MD5 files are correct (these methods throw exceptions if they aren't
    MD5.load(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0.jar.amd.md5"));
    MD5.load(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0.jar.md5"));
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf1
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)leaf:leaf1
   *              |              ^           (1.0.0)-->(2.1.1-{integration})integration-build
   *              |              |           (1.1.0)-->(2.1.1-{integration})integration-build
   *              |           (1.0.0)
   *              |->(1.0.0)intermediate
   *              |           (1.0.0)
   *              |              |
   *              |             \/
   *              |          (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies(1.0.0)-->(1.0.0)leaf:leaf2
   *              |                                                 (1.0.0)-->(1.0.0)leaf1:leaf1
   *              |                                                 (1.1.0)-->(2.0.0)leaf1:leaf1 // This is the upgrade
   *              |                                                 (1.1.0)-->(1.0.0)leaf2:leaf2
   *              |                                                 (1.1.0)-->(1.0.0)leaf3:leaf3 (optional)
   * </pre>
   * <p>
   * Notice that the leaf1:leaf1 node gets upgrade across a major version. This is allowed because the
   * multiple-versions-different-dependencies node gets upgrade to 1.1.0 and therefore all of the dependencies below it
   * are from the 1.1.0 version.
   */
  @Test
  public void reduceComplex() throws Exception {
    ReifiedArtifact leaf1 = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "leaf", "leaf1", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact leaf2 = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "leaf", "leaf2", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact leaf1_1 = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "leaf1", "leaf1", "jar"), new Version("2.0.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact leaf2_2 = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "leaf2", "leaf2", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact leaf3_3 = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "leaf3", "leaf3", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact integrationBuild = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "integration-build", "integration-build", "jar"), new Version("2.1.1-{integration}"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact intermediate = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact multipleVersions = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "multiple-versions", "multiple-versions", "jar"), new Version("1.1.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact multipleVersionsDifferentDeps = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar"), new Version("1.1.0"), MapBuilder.simpleMap(License.Commercial, null));

    DependencyGraph graph = new DependencyGraph(project);
    graph.addEdge(new Dependency(project.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(project.id), new Dependency(intermediate.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(leaf1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(leaf1.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(integrationBuild.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("2.1.1-{integration}"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(integrationBuild.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.1.1-{integration}"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf2.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf2_2.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf3_3.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));

    ArtifactGraph expected = new ArtifactGraph(project);
    expected.addEdge(project, multipleVersions, "compile");
    expected.addEdge(project, intermediate, "runtime");
    expected.addEdge(project, multipleVersionsDifferentDeps, "compile");
    expected.addEdge(intermediate, multipleVersions, "compile");
    expected.addEdge(intermediate, multipleVersionsDifferentDeps, "runtime");
    expected.addEdge(multipleVersions, leaf1, "compile");
    expected.addEdge(multipleVersions, integrationBuild, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf1_1, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf2_2, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf3_3, "runtime");

    ArtifactGraph actual = service.reduce(graph);
    assertEquals(actual, expected);
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf
   *              |            (1.1.0)       (1.1.0)-->(2.0.0)leaf:leaf
   *              |              ^                         (1.0.0) (2.0.0)
   *              |              |                           |       ^
   *              |->(1.0.0)intermediate                     |       |
   *              |              |                           |       |
   *              |             \/                           |       |
   *              |          (1.1.0)                      (1.0.0) (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies
   *              |
   *              |
   *              |
   *              |
   * </pre>
   * <p>
   * Notice that the leaf has two versions, 1.0.0 and 2.0.0. Since the first visit to this node from the
   * multiple-versions node will upgrade leaf to 2.0.0, it should ignore the 1.0.0 version of it and not generate an
   * error.
   */
  @Test
  public void reduceComplexCross() throws Exception {
    ReifiedArtifact leaf = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "leaf", "leaf", "jar"), new Version("2.0.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact intermediate = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact multipleVersions = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "multiple-versions", "multiple-versions", "jar"), new Version("1.1.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact multipleVersionsDifferentDeps = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar"), new Version("1.1.0"), MapBuilder.simpleMap(License.Commercial, null));

    DependencyGraph graph = new DependencyGraph(project);
    graph.addEdge(new Dependency(project.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(project.id), new Dependency(intermediate.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(leaf.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(leaf.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));

    ArtifactGraph expected = new ArtifactGraph(project);
    expected.addEdge(project, multipleVersions, "compile");
    expected.addEdge(project, intermediate, "runtime");
    expected.addEdge(project, multipleVersionsDifferentDeps, "compile");
    expected.addEdge(intermediate, multipleVersions, "compile");
    expected.addEdge(intermediate, multipleVersionsDifferentDeps, "runtime");
    expected.addEdge(multipleVersions, leaf, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf, "runtime");

    ArtifactGraph actual = service.reduce(graph);
    assertEquals(actual, expected);
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)intermediate2:intermediate2(2.0.0)-->(2.0.0)leaf
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)intermediate2:intermediate2                (1.0.0)
   *              |              ^                                  (2.0.0) (1.0.0)                        ^
   *              |              |                                    ^      ^                             |
   *              |->(1.0.0)intermediate                    ----------|      |                             |
   *              |              |                     -----|  --------------                              |
   *              |             \/                    |       |                                            |
   *              |          (1.1.0)               (1.0.0) (1.1.0)                                         |
   *              |-->(1.0.0)multiple-versions-different-dependencies(1.1.0)-------------------------------|
   *              |
   *              |
   *              |
   *              |
   * </pre>
   * <p>
   * Notice that the intermediate2 has two versions, 1.0.0 and 2.0.0. However, multiple-versions-different-dependencies
   * gets upgraded to 1.1.0, which means that intermediate2 gets downgraded to 1.0.0. This also means that leaf should
   * be downgraded to 1.0.0 since the dependency from intermediate2(2.0.0) should be ignored since that version is never
   * used in the graph.
   */
  @Test
  public void reduceDowngrade() throws Exception {
    ReifiedArtifact leaf = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "leaf", "leaf", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact intermediate = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact intermediate2 = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "intermediate2", "intermediate2", "jar"), new Version("1.0.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact multipleVersions = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "multiple-versions", "multiple-versions", "jar"), new Version("1.1.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact multipleVersionsDifferentDeps = new ReifiedArtifact(new ArtifactID("org.savantbuild.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar"), new Version("1.1.0"), MapBuilder.simpleMap(License.Commercial, null));

    DependencyGraph graph = new DependencyGraph(project);
    graph.addEdge(new Dependency(project.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(project.id), new Dependency(intermediate.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(intermediate2.id), new Dependency(leaf.id), new DependencyEdgeValue(new Version("2.0.0"), new Version("2.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(intermediate2.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(intermediate2.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(intermediate2.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("2.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(intermediate2.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));

    ArtifactGraph expected = new ArtifactGraph(project);
    expected.addEdge(project, multipleVersions, "compile");
    expected.addEdge(project, intermediate, "runtime");
    expected.addEdge(project, multipleVersionsDifferentDeps, "compile");
    expected.addEdge(intermediate, multipleVersions, "compile");
    expected.addEdge(intermediate, multipleVersionsDifferentDeps, "runtime");
    expected.addEdge(multipleVersions, intermediate2, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, intermediate2, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf, "compile");

    ArtifactGraph actual = service.reduce(graph);
    assertEquals(actual, expected);
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)leaf
   *              |     * (2.0.0)
   *              |          ^
   *              |          |
   *              |          |
   *              |->(1.0.0)intermediate
   * </pre>
   * <p>
   * Notice that leaf has two versions, 1.0.0 and 2.0.0. Since this artifact is reachable from the root node, it will
   * cause a failure.
   */
  @Test
  public void reduceFailureFromRoot() throws Exception {
    ArtifactID leaf = new ArtifactID("org.savantbuild.test", "leaf", "leaf", "jar");
    ArtifactID intermediate = new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar");

    DependencyGraph incompatible = new DependencyGraph(project);
    incompatible.addEdge(new Dependency(project.id), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(project.id), new Dependency(intermediate), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(intermediate), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("2.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));

    try {
      service.reduce(incompatible);
      fail("Should have failed");
    } catch (CompatibilityException e) {
      assertEquals(e.artifactID, leaf);
      assertEquals(e.min, new Version("1.0.0"));
      assertEquals(e.max, new Version("2.0.0"));
      e.printStackTrace();
    }
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)leaf:leaf
   *              |              ^                          (1.0.0) (2.0.0)
   *              |              |                              ^      ^
   *              |->(1.0.0)intermediate                        |      |
   *              |              |                              |      |
   *              |             \/                              |      |
   *              |          (1.1.0)                        (1.0.0) (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies
   *              |
   *              |
   *              |
   *              |
   * </pre>
   * <p>
   * Notice that the leaf has two versions, 1.0.0 and 2.0.0. Since the first visit to this node from the
   * multiple-versions node will encounter two incompatible versions, it will cause a failure.
   */
  @Test
  public void reduceFailureNested() throws Exception {
    ArtifactID leaf = new ArtifactID("org.savantbuild.test", "leaf", "leaf", "jar");
    ArtifactID intermediate = new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar");
    ArtifactID multipleVersions = new ArtifactID("org.savantbuild.test", "multiple-versions", "multiple-versions", "jar");
    ArtifactID multipleVersionsDifferentDeps = new ArtifactID("org.savantbuild.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar");

    DependencyGraph incompatible = new DependencyGraph(project);
    incompatible.addEdge(new Dependency(project.id), new Dependency(multipleVersions), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(project.id), new Dependency(intermediate), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(intermediate), new Dependency(multipleVersions), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(intermediate), new Dependency(multipleVersionsDifferentDeps), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(multipleVersions), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(multipleVersions), new Dependency(leaf), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(multipleVersionsDifferentDeps), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(multipleVersionsDifferentDeps), new Dependency(leaf), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));

    try {
      service.reduce(incompatible);
      fail("Should have failed");
    } catch (CompatibilityException e) {
      assertEquals(e.artifactID, leaf);
      assertEquals(e.min, new Version("1.0.0"));
      assertEquals(e.max, new Version("2.0.0"));
      e.printStackTrace();
    }
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)leaf:leaf
   *              |              ^                          (1.0.0) (2.0.0)
   *              |              |                              ^      ^
   *              |->(1.0.0)intermediate                        |      |
   *              |              |                              |      |
   *              |             \/                              |      |
   *              |          (1.1.0)                        (1.0.0) (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies
   *              |
   *              |
   *              |
   *              |
   * </pre>
   * <p>
   * Notice that the leaf has two versions, 1.0.0 and 2.0.0. Since the first visit to this node from the
   * multiple-versions node will encounter two incompatible versions. However, we have set skipCompatibilityCheck to
   * true, which should upgrade to 2.0.0.
   */
  @Test
  public void reduceFailureButSkipCompatibilityCheck() throws Exception {
    ArtifactID leaf = new ArtifactID("org.savantbuild.test", "leaf", "leaf", "jar");
    ArtifactID intermediate = new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar");
    ArtifactID multipleVersions = new ArtifactID("org.savantbuild.test", "multiple-versions", "multiple-versions", "jar");
    ArtifactID multipleVersionsDifferentDeps = new ArtifactID("org.savantbuild.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar");

    DependencyGraph incompatible = new DependencyGraph(project);
    incompatible.addEdge(new Dependency(project.id), new Dependency(multipleVersions), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(project.id), new Dependency(intermediate), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(intermediate), new Dependency(multipleVersions), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(intermediate), new Dependency(multipleVersionsDifferentDeps), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(multipleVersions), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(multipleVersions), new Dependency(leaf), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(multipleVersionsDifferentDeps), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.addEdge(new Dependency(multipleVersionsDifferentDeps), new Dependency(leaf), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "compile", MapBuilder.simpleMap(License.Commercial, null)));

    // Add the skip node
    incompatible.addEdge(new Dependency(project.id), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("2.0.0"), "runtime", MapBuilder.simpleMap(License.Commercial, null)));
    incompatible.skipCompatibilityCheck(leaf);

    ReifiedArtifact intermediateArtifact = new ReifiedArtifact(intermediate, new Version("1.0.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact multipleVersionsArtifact = new ReifiedArtifact(multipleVersions, new Version("1.1.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact multipleVersionsDifferentDepsArtifact = new ReifiedArtifact(multipleVersionsDifferentDeps, new Version("1.1.0"), MapBuilder.simpleMap(License.Commercial, null));
    ReifiedArtifact leafArtifact = new ReifiedArtifact(leaf, new Version("2.0.0"), MapBuilder.simpleMap(License.Commercial, null));
    ArtifactGraph actual = service.reduce(incompatible);
    ArtifactGraph expected = new ArtifactGraph(project);
    expected.addEdge(project, multipleVersionsArtifact, "compile");
    expected.addEdge(project, intermediateArtifact, "runtime");
    expected.addEdge(project, multipleVersionsDifferentDepsArtifact, "compile");
    expected.addEdge(project, leafArtifact, "runtime");
    expected.addEdge(intermediateArtifact, multipleVersionsArtifact, "compile");
    expected.addEdge(intermediateArtifact, multipleVersionsDifferentDepsArtifact, "runtime");
    expected.addEdge(multipleVersionsArtifact, leafArtifact, "compile");
    expected.addEdge(multipleVersionsDifferentDepsArtifact, leafArtifact, "compile");
    assertEquals(actual, expected);
  }

  @Test
  public void reduceSimple() throws Exception {
    ArtifactGraph actual = service.reduce(goodGraph);
    assertEquals(actual, goodReducedGraph);
  }

  @Test
  public void resolveGraph() throws Exception {
    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    ResolvedArtifactGraph actual = service.resolve(artifactGraph, workflow,
        new TraversalRules().with("compile", new GroupTraversalRule(true, true))
                            .with("runtime", new GroupTraversalRule(true, true))
    );

    ResolvedArtifactGraph expected = new ResolvedArtifactGraph(projectResolved);
    expected.addEdge(projectResolved, resolvedMultipleVersions, "compile");
    expected.addEdge(projectResolved, resolvedIntermediate, "runtime");
    expected.addEdge(projectResolved, resolvedMultipleVersionsDifferentDeps, "compile");
    expected.addEdge(resolvedIntermediate, resolvedMultipleVersions, "compile");
    expected.addEdge(resolvedIntermediate, resolvedMultipleVersionsDifferentDeps, "runtime");
    expected.addEdge(resolvedMultipleVersions, resolvedLeaf1, "compile");
    expected.addEdge(resolvedMultipleVersions, resolvedIntegrationBuild, "compile");
    expected.addEdge(resolvedMultipleVersionsDifferentDeps, resolvedLeaf1_1, "compile");
    expected.addEdge(resolvedMultipleVersionsDifferentDeps, resolvedLeaf2_2, "compile");
    expected.addEdge(resolvedMultipleVersionsDifferentDeps, resolvedLeaf3_3, "runtime");

    assertEquals(actual, expected);

    verifyResolvedArtifacts(actual);

    String expectedClasspath = String.join(File.pathSeparator, resolvedIntermediate.file.toAbsolutePath().toString(),
        resolvedMultipleVersions.file.toAbsolutePath().toString(), resolvedLeaf1.file.toAbsolutePath().toString(),
        resolvedIntegrationBuild.file.toAbsolutePath().toString(), resolvedMultipleVersionsDifferentDeps.file.toAbsolutePath().toString(),
        resolvedLeaf1_1.file.toAbsolutePath().toString(), resolvedLeaf2_2.file.toAbsolutePath().toString(),
        resolvedLeaf3_3.file.toAbsolutePath().toString());
    assertEquals(actual.toClasspath().toString(), expectedClasspath);
  }

  @Test
  public void resolveGraphFailureBadLicense() throws Exception {
    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    try {
      service.resolve(artifactGraph, workflow,
          new TraversalRules().with("compile", new GroupTraversalRule(true, true, License.GPLV2_0))
                              .with("runtime", new GroupTraversalRule(true, true))
      );
    } catch (LicenseException e) {
      assertEquals(e.artifact, leaf1);
    }
  }

  @Test
  public void resolveGraphFailureMD5() throws Exception {
    DependencyGraph graph = makeSimpleGraph("org.savantbuild.test:bad-md5:1.0.0");
    try {
      ArtifactGraph artifactGraph = service.reduce(graph);
      service.resolve(artifactGraph, workflow, new TraversalRules().with("compile", new GroupTraversalRule(true, true)));
    } catch (MD5Exception e) {
      assertEquals(e.getMessage(), "MD5 mismatch when fetching item from [http://localhost:7000/test-deps/savant/org/savantbuild/test/bad-md5/1.0.0/bad-md5-1.0.0.jar]");
    }
  }

  @Test
  public void resolveGraphFailureMissingDependency() throws Exception {
    DependencyGraph graph = makeSimpleGraph("org.savantbuild.test:missing-item:1.0.0");
    try {
      ArtifactGraph artifactGraph = service.reduce(graph);
      service.resolve(artifactGraph, workflow, new TraversalRules().with("compile", new GroupTraversalRule(true, true)));
    } catch (ArtifactMissingException e) {
      assertEquals(e.artifact, new Artifact("org.savantbuild.test:missing-item:1.0.0", false));
    }
  }

  @Test
  public void resolveGraphNonTransitiveSpecificGroups() throws Exception {
    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    ResolvedArtifactGraph actual = service.resolve(artifactGraph, workflow,
        new TraversalRules().with("compile", new GroupTraversalRule(true, false))
    );

    ResolvedArtifactGraph expected = new ResolvedArtifactGraph(projectResolved);
    ResolvedArtifact multipleVersions = new ResolvedArtifact("org.savantbuild.test:multiple-versions:1.1.0", MapBuilder.simpleMap(License.ApacheV2_0, null), cache.resolve("org/savantbuild/test/multiple-versions/1.1.0/multiple-versions-1.1.0.jar").toAbsolutePath(), null);
    ResolvedArtifact multipleVersionsDifferentDeps = new ResolvedArtifact("org.savantbuild.test:multiple-versions-different-dependencies:1.1.0", MapBuilder.simpleMap(License.ApacheV2_0, null), cache.resolve("org/savantbuild/test/multiple-versions-different-dependencies/1.1.0/multiple-versions-different-dependencies-1.1.0.jar").toAbsolutePath(), null);

    expected.addEdge(projectResolved, multipleVersions, "compile");
    expected.addEdge(projectResolved, multipleVersionsDifferentDeps, "compile");

    assertEquals(actual, expected);

    verifyResolvedArtifacts(actual);
  }

  @Test
  public void resolveGraphTransitiveWithTransitiveGroups() throws Exception {
    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    ResolvedArtifactGraph actual = service.resolve(artifactGraph, workflow,
        new TraversalRules().with("runtime", new GroupTraversalRule(true, "compile", "runtime"))
    );

    ResolvedArtifactGraph expected = new ResolvedArtifactGraph(projectResolved);
    expected.addEdge(projectResolved, resolvedIntermediate, "runtime");
    expected.addEdge(resolvedIntermediate, resolvedMultipleVersions, "compile");
    expected.addEdge(resolvedIntermediate, resolvedMultipleVersionsDifferentDeps, "runtime");
    expected.addEdge(resolvedMultipleVersions, resolvedLeaf1, "compile");
    expected.addEdge(resolvedMultipleVersions, resolvedIntegrationBuild, "compile");
    expected.addEdge(resolvedMultipleVersionsDifferentDeps, resolvedLeaf1_1, "compile");
    expected.addEdge(resolvedMultipleVersionsDifferentDeps, resolvedLeaf2_2, "compile");
    expected.addEdge(resolvedMultipleVersionsDifferentDeps, resolvedLeaf3_3, "runtime");

    assertEquals(actual, expected);

    verifyResolvedArtifacts(actual);
  }

  @Test
  public void resolveGraphTransitiveWithTransitiveMissingGroups() throws Exception {
    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    ResolvedArtifactGraph actual = service.resolve(artifactGraph, workflow,
        new TraversalRules().with("runtime", new GroupTraversalRule(true, "missing"))
    );

    ResolvedArtifactGraph expected = new ResolvedArtifactGraph(projectResolved);
    expected.addEdge(projectResolved, resolvedIntermediate, "runtime");

    assertEquals(actual, expected);

    verifyResolvedArtifacts(actual);
  }

  @Test
  public void resolveGraphTransitiveWithTransitiveSingleGroup() throws Exception {
    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    ResolvedArtifactGraph actual = service.resolve(artifactGraph, workflow,
        new TraversalRules().with("runtime", new GroupTraversalRule(true, "compile"))
    );

    ResolvedArtifactGraph expected = new ResolvedArtifactGraph(projectResolved);
    expected.addEdge(projectResolved, resolvedIntermediate, "runtime");
    expected.addEdge(resolvedIntermediate, resolvedMultipleVersions, "compile");
    expected.addEdge(resolvedMultipleVersions, resolvedLeaf1, "compile");
    expected.addEdge(resolvedMultipleVersions, resolvedIntegrationBuild, "compile");

    assertEquals(actual, expected);

    verifyResolvedArtifacts(actual);
  }

  private Dependencies makeSimpleDependencies(String dependency) {
    return new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact(dependency, false)
        )
    );
  }

  /**
   * Builds a simple DependencyGraph that only contains an edge from the project to a single dependency.
   *
   * @param dependency The dependency.
   * @return The graph.
   */
  private DependencyGraph makeSimpleGraph(String dependency) {
    DependencyGraph graph = new DependencyGraph(project);
    Artifact artifact = new Artifact(dependency, false);
    graph.addEdge(new Dependency(project.id), new Dependency(artifact.id), new DependencyEdgeValue(project.version, artifact.version, "compile", MapBuilder.simpleMap(License.Commercial, null)));
    return graph;
  }

  private void verifyResolvedArtifacts(ResolvedArtifactGraph actual) {
    // Verify that all the artifacts have files and they all exist (except for the project)
    Set<ResolvedArtifact> artifacts = actual.values();
    artifacts.remove(projectResolved);
    artifacts.forEach((artifact) -> assertTrue(Files.isRegularFile(artifact.file)));
  }
}
