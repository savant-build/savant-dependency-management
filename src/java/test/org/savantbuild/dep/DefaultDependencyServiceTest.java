/*
 * Copyright (c) 2001-2013, Inversoft, All Rights Reserved
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

import com.sun.net.httpserver.HttpServer;
import org.savantbuild.dep.DependencyService.ResolveConfiguration;
import org.savantbuild.dep.DependencyService.ResolveConfiguration.TypeResolveConfiguration;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Dependency;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.ResolvedArtifact;
import org.savantbuild.dep.domain.Version;
import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.DependencyLinkValue;
import org.savantbuild.dep.graph.ResolvedArtifactGraph;
import org.savantbuild.dep.io.FileTools;
import org.savantbuild.dep.io.MD5Exception;
import org.savantbuild.dep.workflow.ArtifactMetaDataMissingException;
import org.savantbuild.dep.workflow.ArtifactMissingException;
import org.savantbuild.dep.workflow.FetchWorkflow;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.workflow.Workflow;
import org.savantbuild.dep.workflow.process.CacheProcess;
import org.savantbuild.dep.workflow.process.URLProcess;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

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
  public Path cache = Paths.get("build/test/cache");

  public Dependencies dependencies;

  public DependencyGraph goodGraph;

  public Artifact project = new Artifact("org.savantbuild.test:project:1.0");

  public ResolvedArtifact projectResolved = new ResolvedArtifact(project.id, project.version);

  public HttpServer server;

  public DefaultDependencyService service = new DefaultDependencyService();

  public Workflow workflow = new Workflow(
      new FetchWorkflow(new CacheProcess("build/test/cache"), new URLProcess("http://localhost:7000/test-deps/savant", null, null)),
      new PublishWorkflow(new CacheProcess("build/test/cache"))
  );

  /**
   * Graph:
   * <p/>
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
  public DefaultDependencyServiceTest() {
    ArtifactID leaf1 = new ArtifactID("org.savantbuild.test", "leaf", "leaf1", "jar");
    ArtifactID leaf2 = new ArtifactID("org.savantbuild.test", "leaf", "leaf2", "jar");
    ArtifactID leaf1_1 = new ArtifactID("org.savantbuild.test", "leaf1", "leaf1", "jar");
    ArtifactID leaf2_2 = new ArtifactID("org.savantbuild.test", "leaf2", "leaf2", "jar");
    ArtifactID leaf3_3 = new ArtifactID("org.savantbuild.test", "leaf3", "leaf3", "jar");
    ArtifactID integrationBuild = new ArtifactID("org.savantbuild.test", "integration-build", "integration-build", "jar");
    ArtifactID intermediate = new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar");
    ArtifactID multipleVersions = new ArtifactID("org.savantbuild.test", "multiple-versions", "multiple-versions", "jar");
    ArtifactID multipleVersionsDifferentDeps = new ArtifactID("org.savantbuild.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar");

    goodGraph = new DependencyGraph(project);
    goodGraph.addLink(project.id, multipleVersions, new DependencyLinkValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false));
    goodGraph.addLink(project.id, intermediate, new DependencyLinkValue(new Version("1.0.0"), new Version("1.0.0"), "run", false));
    goodGraph.addLink(project.id, multipleVersionsDifferentDeps, new DependencyLinkValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false));

    goodGraph.addLink(intermediate, multipleVersions, new DependencyLinkValue(new Version("1.0.0"), new Version("1.1.0"), "compile", false));
    goodGraph.addLink(intermediate, multipleVersionsDifferentDeps, new DependencyLinkValue(new Version("1.0.0"), new Version("1.1.0"), "run", false));

    goodGraph.addLink(multipleVersions, leaf1, new DependencyLinkValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false));
    goodGraph.addLink(multipleVersions, leaf1, new DependencyLinkValue(new Version("1.1.0"), new Version("1.0.0"), "compile", false));
    goodGraph.addLink(multipleVersions, integrationBuild, new DependencyLinkValue(new Version("1.0.0"), new Version("2.1.1-{integration}"), "compile", false));
    goodGraph.addLink(multipleVersions, integrationBuild, new DependencyLinkValue(new Version("1.1.0"), new Version("2.1.1-{integration}"), "compile", false));

    goodGraph.addLink(multipleVersionsDifferentDeps, leaf2, new DependencyLinkValue(new Version("1.0.0"), new Version("1.0.0"), "run", false));
    goodGraph.addLink(multipleVersionsDifferentDeps, leaf1_1, new DependencyLinkValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false));

    goodGraph.addLink(multipleVersionsDifferentDeps, leaf1_1, new DependencyLinkValue(new Version("1.1.0"), new Version("1.0.0"), "compile", false));
    goodGraph.addLink(multipleVersionsDifferentDeps, leaf2_2, new DependencyLinkValue(new Version("1.1.0"), new Version("1.0.0"), "compile", false));
    goodGraph.addLink(multipleVersionsDifferentDeps, leaf3_3, new DependencyLinkValue(new Version("1.1.0"), new Version("1.0.0"), "run", true));

    dependencies = new Dependencies(
        new DependencyGroup("compile",
            new Dependency(multipleVersions, new Version("1.0.0"), false),
            new Dependency(multipleVersionsDifferentDeps, new Version("1.0.0"), false)
        ),
        new DependencyGroup("run",
            new Dependency(intermediate, new Version("1.0.0"), false)
        )
    );
  }

  @AfterMethod
  public void afterMethodStopFileServer() {
    server.stop(0);
  }

  @BeforeMethod
  public void beforeMethodStartFileServer() throws IOException {
    server = makeFileServer(null, null);
    FileTools.prune(cache);
    assertFalse(Files.isDirectory(cache));
  }

  @Test
  public void buildGraphFailureBadAMDMD5() throws Exception {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.savantbuild.test:bad-amd-md5:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (MD5Exception e) {
      assertEquals(e.getMessage(), "MD5 mismatch when downloading item from [http://localhost:7000/test-deps/savant/org/savantbuild/test/bad-amd-md5/1.0.0/bad-amd-md5-1.0.0.jar.amd]");
    }
  }

  @Test
  public void buildGraphFailureMissingAMD() throws Exception {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.savantbuild.test:missing-amd:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (ArtifactMetaDataMissingException e) {
      assertEquals(e.artifactMissingAMD, new Artifact("org.savantbuild.test:missing-amd:1.0.0"));
    }
  }

  @Test
  public void buildGraphFailureMissingDependency() throws Exception {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.savantbuild.test:missing:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (ArtifactMetaDataMissingException e) {
      assertEquals(e.artifactMissingAMD, new Artifact("org.savantbuild.test:missing:1.0.0"));
    }
  }

  @Test
  public void buildGraphFailureMissingMD5() throws Exception {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.savantbuild.test:missing-md5:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (ArtifactMetaDataMissingException e) {
      assertEquals(e.artifactMissingAMD, new Artifact("org.savantbuild.test:missing-md5:1.0.0"));
    }
  }

  @Test
  public void buildGraph() throws Exception {
    DependencyGraph actual = service.buildGraph(project, dependencies, workflow);
    assertEquals(actual, goodGraph);
  }

  @Test
  public void resolveGraphFailureMD5() throws Exception {
    DependencyGraph graph = makeSimpleGraph("org.savantbuild.test:bad-md5:1.0.0");
    try {
      service.resolve(graph, workflow, new ResolveConfiguration().with("compile", new TypeResolveConfiguration(true, true)));
    } catch (MD5Exception e) {
      assertEquals(e.getMessage(), "MD5 mismatch when downloading item from [http://localhost:7000/test-deps/savant/org/savantbuild/test/bad-md5/1.0.0/bad-md5-1.0.0.jar]");
    }
  }

  @Test
  public void resolveGraphFailureMissingDependency() throws Exception {
    DependencyGraph graph = makeSimpleGraph("org.savantbuild.test:missing-item:1.0.0");
    try {
      service.resolve(graph, workflow, new ResolveConfiguration().with("compile", new TypeResolveConfiguration(true, true)));
    } catch (ArtifactMissingException e) {
      assertEquals(e.artifact, new Artifact("org.savantbuild.test:missing-item:1.0.0"));
    }
  }

  @Test
  public void resolveGraph() throws Exception {
    ResolvedArtifactGraph expected = new ResolvedArtifactGraph(projectResolved);
    ResolvedArtifact intermediate = new ResolvedArtifact("org.savantbuild.test:intermediate:1.0.0", cache.resolve("org/savantbuild/test/intermediate/1.0.0/intermediate-1.0.0.jar"));
    ResolvedArtifact multipleVersions = new ResolvedArtifact("org.savantbuild.test:multiple-versions:1.1.0", cache.resolve("org/savantbuild/test/multiple-versions/1.1.0/multiple-versions-1.1.0.jar"));
    ResolvedArtifact multipleVersionsDifferentDeps = new ResolvedArtifact("org.savantbuild.test:multiple-versions-different-dependencies:1.1.0", cache.resolve("org/savantbuild/test/multiple-versions-different-dependencies/1.1.0/multiple-versions-different-dependencies-1.1.0.jar"));
    ResolvedArtifact leaf1 = new ResolvedArtifact("org.savantbuild.test:leaf:leaf1:1.0.0:jar", cache.resolve("org/savantbuild/test/leaf/1.0.0/leaf1-1.0.0.jar"));
    ResolvedArtifact leaf1_1 = new ResolvedArtifact("org.savantbuild.test:leaf1:1.0.0", cache.resolve("org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar"));
    ResolvedArtifact leaf2_2 = new ResolvedArtifact("org.savantbuild.test:leaf2:1.0.0", cache.resolve("org/savantbuild/test/leaf2/1.0.0/leaf2-1.0.0.jar"));
    ResolvedArtifact integrationBuild = new ResolvedArtifact("org.savantbuild.test:integration-build:2.1.1-{integration}", cache.resolve("org/savantbuild/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar"));

    expected.addLink(projectResolved, multipleVersions, "compile");
    expected.addLink(projectResolved, intermediate, "run");
    expected.addLink(projectResolved, multipleVersionsDifferentDeps, "compile");

    expected.addLink(intermediate, multipleVersions, "compile");
    expected.addLink(intermediate, multipleVersionsDifferentDeps, "run");

    expected.addLink(multipleVersions, leaf1, "compile");
    expected.addLink(multipleVersions, integrationBuild, "compile");

    expected.addLink(multipleVersionsDifferentDeps, leaf1_1, "compile");
    expected.addLink(multipleVersionsDifferentDeps, leaf2_2, "compile");

    ResolvedArtifactGraph actual = service.resolve(goodGraph, workflow,
        new ResolveConfiguration().with("compile", new TypeResolveConfiguration(true, true))
                                  .with("run", new TypeResolveConfiguration(true, true))
    );

    assertEquals(actual, expected);

    // Verify that all the artifacts have files and they all exist (except for the project)
    Set<ResolvedArtifact> artifacts = actual.values();
    artifacts.remove(projectResolved);
    artifacts.forEach((artifact) -> assertTrue(Files.isRegularFile(artifact.file)));
  }

  @Test
  public void verifyCompatibility() throws Exception {

  }

  @Test
  public void verifyCompatibilityFailure() throws Exception {

  }

  private Dependencies makeSimpleDependencies(String dependency) {
    return new Dependencies(
        new DependencyGroup("compile",
            new Dependency(dependency, false)
        )
    );
  }

  /**
   * Builds a simple DependencyGraph that only contains a link from the project to a single dependency.
   *
   * @param dependency The dependency.
   * @return The graph.
   */
  private DependencyGraph makeSimpleGraph(String dependency) {
    DependencyGraph graph = new DependencyGraph(project);
    Artifact artifact = new Artifact(dependency);
    graph.addLink(project.id, artifact.id, new DependencyLinkValue(project.version, artifact.version, "compile", false));
    return graph;
  }
}
