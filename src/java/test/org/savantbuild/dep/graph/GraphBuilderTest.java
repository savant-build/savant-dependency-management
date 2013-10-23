/*
 * Copyright (c) 2008, Inversoft, All Rights Reserved.
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
package org.savantbuild.dep.graph;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.savantbuild.dep.ResolutionContext;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.workflow.FetchWorkflow;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.workflow.Workflow;
import org.savantbuild.dep.workflow.process.CacheProcess;
import org.savantbuild.dep.workflow.process.URLProcess;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.io.FileTools;
import org.savantbuild.run.output.DefaultOutput;
import org.testng.annotations.Test;

import static org.savantbuild.TestTools.*;
import static org.testng.Assert.*;

/**
 * <p>
 * This class is a test case that tests the dependency graph builder.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class GraphBuilderTest {
  @Test
  public void transitiveDependencies() throws Exception {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Artifact transArtifact = new Artifact("org.savantbuild.test", "transitive-dependencies", "transitive-dependencies", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(transArtifact);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    DefaultOutput output = new DefaultOutput();
    Workflow wh = new Workflow(new FetchWorkflow(output), new PublishWorkflow());
    wh.getFetchWorkflow().getProcesses().add(new URLProcess(new DefaultOutput(), map("url", new File("test-deps/savant").toURI().toURL().toString())));
    wh.getPublishWorkflow().getProcesses().add(new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps")));

    ResolutionContext resolutionContext = new ResolutionContext();
    GraphBuilder builder = new GraphBuilder(new DefaultOutput(), d, wh, true);
    DependencyGraph graph = builder.buildGraph(resolutionContext);

    // Check the negatives
    Artifact noAMD = new Artifact("org.savantbuild.test", "no-amd", "no-amd", "1.0", "jar");
    Map<Artifact, Set<String>> missingItems = resolutionContext.getMissingItems();
    assertEquals(missingItems.size(), 1);
    assertEquals(missingItems.get(noAMD).size(), 1);
    assertEquals("AMD_FILE", missingItems.get(noAMD).iterator().next());

    // Including the project node.
    assertEquals(graph.values().size(), 7);

    Dependencies artDeps = graph.getDependencies(transArtifact);
    assertEquals(artDeps.getArtifactGroups().size(), 1);
    assertEquals(artDeps.getAllArtifacts().size(), 2);
    assertEquals(artDeps.getArtifactGroups().get("run").getArtifacts().size(), 2);
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(new Artifact("org.savantbuild.test", "dependencies", "dependencies", "1.0", "jar")));
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(new Artifact("org.savantbuild.test", "no-amd", "no-amd", "1.0", "jar")));

    artDeps = graph.getDependencies(new Artifact("org.savantbuild.test", "dependencies", "dependencies", "1.0", "jar"));
    assertEquals(artDeps.getArtifactGroups().size(), 1);
    assertEquals(artDeps.getAllArtifacts().size(), 3);
    assertEquals(artDeps.getArtifactGroups().get("run").getArtifacts().size(), 3);
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(new Artifact("org.savantbuild.test", "major-compat", "major-compat", "2.0", "jar")));
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(new Artifact("org.savantbuild.test", "minor-compat", "minor-compat", "1.1", "jar")));
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(new Artifact("org.savantbuild.test", "patch-compat", "patch-compat", "1.0", "jar")));

    GraphNode node = graph.getNode(transArtifact.getId());
    assertEquals(transArtifact.getId(), node.getValue());

    ArtifactID id = new ArtifactID("org.savantbuild.test", "dependencies", "dependencies", "jar");
    List<GraphPath<ArtifactID>> paths = graph.paths(transArtifact.getId(), id);
    assertEquals(paths.size(), 1);
    assertEquals(paths.get(0).getPath().size(), 2);
    assertEquals(paths.get(0).getPath().get(0), transArtifact.getId());
    assertEquals(paths.get(0).getPath().get(1), id);

    id = new ArtifactID("org.savantbuild.test", "no-amd", "no-amd", "jar");
    paths = graph.paths(transArtifact.getId(), id);
    assertEquals(paths.size(), 1);
    assertEquals(paths.get(0).getPath().size(), 2);
    assertEquals(paths.get(0).getPath().get(0), transArtifact.getId());
    assertEquals(paths.get(0).getPath().get(1), id);

    id = new ArtifactID("org.savantbuild.test", "major-compat", "major-compat", "jar");
    paths = graph.paths(transArtifact.getId(), id);
    assertEquals(paths.size(), 1);
    assertEquals(paths.get(0).getPath().size(), 3);
    assertEquals(paths.get(0).getPath().get(0), transArtifact.getId());
    assertEquals(paths.get(0).getPath().get(1), new ArtifactID("org.savantbuild.test", "dependencies", "dependencies", "jar"));
    assertEquals(paths.get(0).getPath().get(2), id);

    id = new ArtifactID("org.savantbuild.test", "minor-compat", "minor-compat", "jar");
    paths = graph.paths(transArtifact.getId(), id);
    assertEquals(paths.get(0).getPath().size(), 3);
    assertEquals(paths.get(0).getPath().get(0), transArtifact.getId());
    assertEquals(paths.get(0).getPath().get(1), new ArtifactID("org.savantbuild.test", "dependencies", "dependencies", "jar"));
    assertEquals(paths.get(0).getPath().get(2), id);

    id = new ArtifactID("org.savantbuild.test", "patch-compat", "patch-compat", "jar");
    paths = graph.paths(transArtifact.getId(), id);
    assertEquals(paths.get(0).getPath().size(), 3);
    assertEquals(paths.get(0).getPath().get(0), transArtifact.getId());
    assertEquals(paths.get(0).getPath().get(1), new ArtifactID("org.savantbuild.test", "dependencies", "dependencies", "jar"));
    assertEquals(paths.get(0).getPath().get(2), id);
  }

  @Test(enabled = true)
  public void multipleVersion() throws Exception {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Artifact upgrade = new Artifact("org.savantbuild.test", "upgrade-versions", "upgrade-versions", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(upgrade);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    DefaultOutput output = new DefaultOutput();
    Workflow wh = new Workflow(new FetchWorkflow(output), new PublishWorkflow());
    wh.getFetchWorkflow().getProcesses().add(new URLProcess(new DefaultOutput(), map("url", new File("test-deps/savant").toURI().toURL().toString())));
    wh.getPublishWorkflow().getProcesses().add(new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps")));

    ResolutionContext resolutionContext = new ResolutionContext();
    GraphBuilder builder = new GraphBuilder(new DefaultOutput(), d, wh, true);
    DependencyGraph graph = builder.buildGraph(resolutionContext);

    // Including the project node.
    assertEquals(graph.values().size(), 6);

    Dependencies artDeps = graph.getDependencies(upgrade);
    assertEquals(artDeps.getArtifactGroups().size(), 1);
    assertEquals(artDeps.getAllArtifacts().size(), 4);
    assertEquals(artDeps.getArtifactGroups().get("run").getArtifacts().size(), 4);
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(new Artifact("org.savantbuild.test", "dependencies", "dependencies", "1.0", "jar")));
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(new Artifact("org.savantbuild.test", "major-compat", "major-compat", "1.0", "jar")));
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(new Artifact("org.savantbuild.test", "minor-compat", "minor-compat", "1.0", "jar")));
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(new Artifact("org.savantbuild.test", "patch-compat", "patch-compat", "1.0.1", "jar")));

    artDeps = graph.getDependencies(new Artifact("org.savantbuild.test", "dependencies", "dependencies", "1.0", "jar"));
    assertEquals(artDeps.getArtifactGroups().size(), 1);
    assertEquals(artDeps.getAllArtifacts().size(), 3);
    assertEquals(artDeps.getArtifactGroups().get("run").getArtifacts().size(), 3);
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(new Artifact("org.savantbuild.test", "major-compat", "major-compat", "2.0", "jar")));
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(new Artifact("org.savantbuild.test", "minor-compat", "minor-compat", "1.1", "jar")));
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(new Artifact("org.savantbuild.test", "patch-compat", "patch-compat", "1.0", "jar")));

    GraphNode node = graph.getNode(upgrade.getId());
    assertEquals(upgrade.getId(), node.getValue());

    ArtifactID id = new ArtifactID("org.savantbuild.test", "dependencies", "dependencies", "jar");
    List<GraphPath<ArtifactID>> paths = graph.paths(upgrade.getId(), id);
    assertEquals(paths.size(), 1);
    assertEquals(paths.get(0).getPath().size(), 2);
    assertEquals(paths.get(0).getPath().get(0), upgrade.getId());
    assertEquals(paths.get(0).getPath().get(1), id);

    id = new ArtifactID("org.savantbuild.test", "major-compat", "major-compat", "jar");
    paths = graph.paths(upgrade.getId(), id);
    assertEquals(paths.size(), 2);
    assertEquals(paths.get(0).getPath().size(), 3);
    assertEquals(paths.get(0).getPath().get(0), upgrade.getId());
    assertEquals(paths.get(0).getPath().get(1), new ArtifactID("org.savantbuild.test", "dependencies", "dependencies", "jar"));
    assertEquals(paths.get(0).getPath().get(2), id);
    assertEquals(paths.get(1).getPath().size(), 2);
    assertEquals(paths.get(1).getPath().get(0), upgrade.getId());
    assertEquals(paths.get(1).getPath().get(1), id);

    id = new ArtifactID("org.savantbuild.test", "minor-compat", "minor-compat", "jar");
    paths = graph.paths(upgrade.getId(), id);
    assertEquals(paths.size(), 2);
    assertEquals(paths.get(0).getPath().size(), 3);
    assertEquals(paths.get(0).getPath().get(0), upgrade.getId());
    assertEquals(paths.get(0).getPath().get(1), new ArtifactID("org.savantbuild.test", "dependencies", "dependencies", "jar"));
    assertEquals(paths.get(0).getPath().get(2), id);
    assertEquals(paths.get(1).getPath().size(), 2);
    assertEquals(paths.get(1).getPath().get(0), upgrade.getId());
    assertEquals(paths.get(1).getPath().get(1), id);

    id = new ArtifactID("org.savantbuild.test", "patch-compat", "patch-compat", "jar");
    paths = graph.paths(upgrade.getId(), id);
    assertEquals(paths.size(), 2);
    assertEquals(paths.get(0).getPath().size(), 3);
    assertEquals(paths.get(0).getPath().get(0), upgrade.getId());
    assertEquals(paths.get(0).getPath().get(1), new ArtifactID("org.savantbuild.test", "dependencies", "dependencies", "jar"));
    assertEquals(paths.get(0).getPath().get(2), id);
    assertEquals(paths.get(1).getPath().size(), 2);
    assertEquals(paths.get(1).getPath().get(0), upgrade.getId());
    assertEquals(paths.get(1).getPath().get(1), id);
  }

  @Test(enabled = true)
  public void integration() throws Exception {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Artifact integration = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "2.1.1-{integration}", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(integration);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    DefaultOutput output = new DefaultOutput();
    Workflow wh = new Workflow(new FetchWorkflow(output), new PublishWorkflow());
    wh.getFetchWorkflow().getProcesses().add(new URLProcess(new DefaultOutput(), map("url", new File("test-deps/savant").toURI().toURL().toString())));
    wh.getPublishWorkflow().getProcesses().add(new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps")));

    ResolutionContext resolutionContext = new ResolutionContext();
    GraphBuilder builder = new GraphBuilder(new DefaultOutput(), d, wh, true);
    DependencyGraph graph = builder.buildGraph(resolutionContext);

    // Including the project node.
    assertEquals(graph.values().size(), 2);

    Dependencies artDeps = graph.getDependencies(graph.getRoot());
    assertEquals(artDeps.getArtifactGroups().size(), 1);
    assertEquals(artDeps.getAllArtifacts().size(), 1);
    assertEquals(artDeps.getArtifactGroups().get("run").getArtifacts().size(), 1);
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(new Artifact("org.savantbuild.test", "integration-build", "integration-build", "2.1.1-{integration}", "jar")));

    GraphNode<ArtifactID, DependencyLinkValue> node = graph.getNode(integration.getId());
    assertEquals(integration.getId(), node.getValue());

    GraphLink<ArtifactID, DependencyLinkValue> link = node.getInboundLink(graph.getNode(graph.getRoot().getId()));
    assertEquals(link.value.getDependencyVersion(), "2.1.1-{integration}");
    assertEquals(link.value.getDependencyIntegrationVersion(), "2.1.1-IB20080103144403111");
  }

  @Test(enabled = true)
  public void transitiveIntegration() throws Exception {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Artifact a = new Artifact("org.savantbuild.test", "transitive-integration", "transitive-integration", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    DefaultOutput output = new DefaultOutput();
    Workflow wh = new Workflow(new FetchWorkflow(output), new PublishWorkflow());
    wh.getFetchWorkflow().getProcesses().add(new URLProcess(new DefaultOutput(), map("url", new File("test-deps/savant").toURI().toURL().toString())));
    wh.getPublishWorkflow().getProcesses().add(new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps")));

    ResolutionContext resolutionContext = new ResolutionContext();
    GraphBuilder builder = new GraphBuilder(new DefaultOutput(), d, wh, true);
    DependencyGraph graph = builder.buildGraph(resolutionContext);

    // Including the project node.
    assertEquals(graph.values().size(), 3);

    Artifact integration = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "2.1.1-{integration}", "jar");
    Dependencies artDeps = graph.getDependencies(a);
    assertEquals(artDeps.getArtifactGroups().size(), 1);
    assertEquals(artDeps.getAllArtifacts().size(), 1);
    assertEquals(artDeps.getArtifactGroups().get("run").getArtifacts().size(), 1);
    assertTrue(artDeps.getArtifactGroups().get("run").getArtifacts().contains(integration));

    GraphNode<ArtifactID, DependencyLinkValue> node = graph.getNode(integration.getId());
    GraphLink<ArtifactID, DependencyLinkValue> link = node.getInboundLink(graph.getNode(a.getId()));
    assertEquals(link.value.getDependencyVersion(), "2.1.1-{integration}");
    assertEquals(link.value.getDependencyIntegrationVersion(), "2.1.1-IB20080103144403111");
  }


  @Test(enabled = true)
  public void testNoProjectDefinedInArtifact() throws Exception {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Artifact noProject = new Artifact("org.savantbuild.test", "no-project", "no-project", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(noProject);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    DefaultOutput output = new DefaultOutput();
    Workflow wh = new Workflow(new FetchWorkflow(output), new PublishWorkflow());
    wh.getFetchWorkflow().getProcesses().add(new URLProcess(new DefaultOutput(), map("url", new File("test-deps/savant").toURI().toURL().toString())));
    wh.getPublishWorkflow().getProcesses().add(new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps")));

    ResolutionContext resolutionContext = new ResolutionContext();
    GraphBuilder builder = new GraphBuilder(new DefaultOutput(), d, wh, true);
    DependencyGraph graph = builder.buildGraph(resolutionContext);

    // Including the project node.
    assertEquals(graph.values().size(), 6);
  }

  @Test(enabled = true)
  public void testNoNameDefinedInArtifact() throws Exception {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Artifact noProject = new Artifact("org.savantbuild.test", "no-name", "no-name", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(noProject);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    DefaultOutput output = new DefaultOutput();
    Workflow wh = new Workflow(new FetchWorkflow(output), new PublishWorkflow());
    wh.getFetchWorkflow().getProcesses().add(new URLProcess(new DefaultOutput(), map("url", new File("test-deps/savant").toURI().toURL().toString())));
    wh.getPublishWorkflow().getProcesses().add(new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps")));

    ResolutionContext resolutionContext = new ResolutionContext();
    GraphBuilder builder = new GraphBuilder(new DefaultOutput(), d, wh, true);
    DependencyGraph graph = builder.buildGraph(resolutionContext);

    // Including the project node.
    assertEquals(graph.values().size(), 6);
  }
}
