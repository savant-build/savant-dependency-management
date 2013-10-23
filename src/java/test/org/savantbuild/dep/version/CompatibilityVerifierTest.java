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
package org.savantbuild.dep.version;

import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.DependencyLinkValue;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.run.output.DefaultOutput;
import org.savantbuild.util.ErrorList;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * <p>
 * This class tests the dependency compatibility verifier.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class CompatibilityVerifierTest {
  /**
   * The graph looks like this:
   * <p/>
   * <pre>
   * project ---1.0---> top1:top1:art ---1.0:1.0---> middle:middle:art ---1.0:1.0---> dep1:dep1:art
   *    \-------1.0---> top2:top2:art ---1.0:1.1-----/            \-------1.1:1.0---> dep2:dep2:art
   * </pre>
   * <p/>
   * This test should prune the artifact <strong>dep1:dep1:art</strong> because it is only used
   * by the 1.0 version of <strong>middle:middle:art</strong>.
   */
  @Test
  public void pruneOutboundVersions() {
    CompatibilityVerifier verifier = new CompatibilityVerifier(new DefaultOutput());

    Artifact project = new Artifact("project", "project", "art", "1.0", "jar");
    Artifact top1 = new Artifact("top1", "top1", "art", "1.0", "jar");
    Artifact top2 = new Artifact("top2", "top2", "art", "1.0", "jar");
    Artifact middle1 = new Artifact("middle", "middle", "art", "1.0", "jar");
    Artifact middle11 = new Artifact("middle", "middle", "art", "1.1", "jar");
    Artifact dep1 = new Artifact("dep1", "dep1", "art", "1.0", "jar");
    Artifact dep2 = new Artifact("dep2", "dep2", "art", "1.0", "jar");

    Dependencies deps = new Dependencies();
    deps.getArtifactGroups().put("compile", new DependencyGroup("compile"));
    deps.getArtifactGroups().get("compile").getArtifacts().add(top1);
    deps.getArtifactGroups().get("compile").getArtifacts().add(top2);

    DependencyGraph graph = new DependencyGraph(project);
    graph.addLink(project.getId(), top1.getId(), new DependencyLinkValue("1.0", "1.0", "compile", "minor"));
    graph.addLink(project.getId(), top2.getId(), new DependencyLinkValue("1.0", "1.0", "compile", "minor"));
    graph.addLink(top1.getId(), middle1.getId(), new DependencyLinkValue("1.0", "1.0", "compile", "minor"));
    graph.addLink(top2.getId(), middle11.getId(), new DependencyLinkValue("1.0", "1.1", "compile", "minor"));
    graph.addLink(middle1.getId(), dep1.getId(), new DependencyLinkValue("1.0", "1.0", "compile", "minor"));
    graph.addLink(middle11.getId(), dep2.getId(), new DependencyLinkValue("1.1", "1.0", "compile", "minor"));

    ErrorList errors = verifier.verifyCompatibility(deps, graph, null);
    assertTrue(errors.isEmpty());
    assertEquals(graph.outboundLinks(project.getId()).size(), 2);
    assertEquals(graph.inboundLinks(top1.getId()).size(), 1);
    assertEquals(graph.outboundLinks(top1.getId()).size(), 1);
    assertEquals(graph.inboundLinks(top2.getId()).size(), 1);
    assertEquals(graph.outboundLinks(top2.getId()).size(), 1);
    assertEquals(graph.inboundLinks(middle1.getId()).size(), 2);
    assertEquals(graph.outboundLinks(middle1.getId()).size(), 1);
    assertEquals(graph.inboundLinks(middle11.getId()).size(), 2);
    assertEquals(graph.outboundLinks(middle11.getId()).size(), 1);
    assertEquals(graph.inboundLinks(dep1.getId()).size(), 0);
    assertEquals(graph.inboundLinks(dep2.getId()).size(), 1);
    assertEquals(graph.outboundLinks(middle1.getId()).get(0).destination.getValue().getGroup(), "dep2");
    assertEquals(graph.outboundLinks(middle1.getId()).get(0).destination.getValue().getProject(), "dep2");
    assertEquals(graph.outboundLinks(middle1.getId()).get(0).destination.getValue().getName(), "art");
    assertEquals(graph.outboundLinks(middle1.getId()).get(0).value.getDependentVersion(), "1.1");
    assertEquals(graph.outboundLinks(middle1.getId()).get(0).value.getDependencyVersion(), "1.0");
  }
}
