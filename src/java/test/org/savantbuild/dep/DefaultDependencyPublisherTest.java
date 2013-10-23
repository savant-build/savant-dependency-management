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
package org.savantbuild.dep;

import java.io.File;
import java.net.MalformedURLException;

import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.xml.ArtifactTools;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.domain.Project;
import org.savantbuild.dep.domain.Publication;
import org.savantbuild.io.FileTools;
import org.savantbuild.run.output.DefaultOutput;
import org.testng.annotations.Test;

import static java.util.Arrays.*;
import static org.savantbuild.TestTools.*;
import static org.testng.Assert.*;

/**
 * <p>
 * This class tests the dependency publish mediator.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class DefaultDependencyPublisherTest {
  @Test
  public void publishRelease() {
    FileTools.prune(new File("target/test/deps-publish"));

    Project project = new Project();
    project.setGroup("org.savantbuild.test");
    project.setName("publish-test");
    project.setVersion("1.0");

    PublishWorkflow w = new PublishWorkflow();
    w.getProcesses().add(new Process(map("type", "cache", "dir", "target/test/deps-publish")));

    Publication p = new Publication();
    p.setFile("test-deps/savant/org/savantbuild/test/no-amd/1.0/no-amd-1.0.jar");
    p.setName("publish-test-artifact");
    p.setType("jar");
    project.getPublications().add(p);

    DefaultDependencyPublisher dpm = new DefaultDependencyPublisher(new DefaultOutput());
    dpm.publish(project, p, w, false);

    File[] files = new File("target/test/deps-publish/org/savantbuild/test/publish-test/1.0").listFiles();
    sort(files);
    assertEquals(files.length, 4);
    assertEquals(files[0].getName(), "publish-test-artifact-1.0.jar");
    assertEquals(files[1].getName(), "publish-test-artifact-1.0.jar.amd");
    assertEquals(files[2].getName(), "publish-test-artifact-1.0.jar.amd.md5");
    assertEquals(files[3].getName(), "publish-test-artifact-1.0.jar.md5");
  }

  @Test
  public void publishReleaseWithSource() {
    FileTools.prune(new File("target/test/deps-publish"));

    Project project = new Project();
    project.setGroup("org.savantbuild.test");
    project.setName("publish-test");
    project.setVersion("1.0");

    PublishWorkflow w = new PublishWorkflow();
    w.getProcesses().add(new Process(map("type", "cache", "dir", "target/test/deps-publish")));

    Publication p = new Publication();
    p.setFile("test-deps/savant/org/savantbuild/test/with-source/1.0/with-source-1.0.jar");
    p.setName("publish-test-artifact");
    p.setType("jar");
    project.getPublications().add(p);

    DefaultDependencyPublisher dpm = new DefaultDependencyPublisher(new DefaultOutput());
    dpm.publish(project, p, w, false);

    File[] files = new File("target/test/deps-publish/org/savantbuild/test/publish-test/1.0").listFiles();
    sort(files);
    assertEquals(files.length, 6);
    assertEquals(files[0].getName(), "publish-test-artifact-1.0-src.jar");
    assertEquals(files[1].getName(), "publish-test-artifact-1.0-src.jar.md5");
    assertEquals(files[2].getName(), "publish-test-artifact-1.0.jar");
    assertEquals(files[3].getName(), "publish-test-artifact-1.0.jar.amd");
    assertEquals(files[4].getName(), "publish-test-artifact-1.0.jar.amd.md5");
    assertEquals(files[5].getName(), "publish-test-artifact-1.0.jar.md5");
  }

  @Test(enabled = true)
  public void hasIntegrations() {
    DependencyGroup ag = new DependencyGroup("compile");
    ag.getArtifacts().add(new Artifact("test-group", "test-project-2", "test-artifact", "1.0-{integration}", "jar"));

    Dependencies d = new Dependencies("test");
    d.getArtifactGroups().put("compile", ag);

    DefaultDependencyPublisher dpm = new DefaultDependencyPublisher(new DefaultOutput());
    assertTrue(dpm.hasIntegrations(d));
  }

  @Test(enabled = true)
  public void publishIntegration() {
    FileTools.prune(new File("target/test/deps-publish"));

    Project project = new Project();
    project.setGroup("org.savantbuild.test");
    project.setName("publish-test");
    project.setVersion("1.0");

    PublishWorkflow w = new PublishWorkflow();
    w.getProcesses().add(new Process(map("type", "cache", "dir", "target/test/deps-publish")));

    Publication p = new Publication();
    p.setFile("test-deps/savant/org/savantbuild/test/with-source/1.0/with-source-1.0.jar");
    p.setName("publish-test-artifact");
    p.setType("jar");
    project.getPublications().add(p);

    DefaultDependencyPublisher dpm = new DefaultDependencyPublisher(new DefaultOutput());
    dpm.publish(project, p, w, true);

    File[] files = new File("target/test/deps-publish/org/savantbuild/test/publish-test/1.0-{integration}").listFiles();
    assertEquals(files.length, 6);
    for (File file : files) {
      assertTrue(file.getName().startsWith("publish-test-artifact-1.0-IB"));
    }
  }

  @Test(enabled = true)
  public void publishWithIntegrationDependency() throws MalformedURLException {
    FileTools.prune(new File("target/test/deps"));
    FileTools.prune(new File("target/test/deps-publish"));

    Project project = new Project();
    project.setGroup("org.savantbuild.test");
    project.setName("publish-test");
    project.setVersion("1.0");

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", new DependencyGroup("run"));
    d.getArtifactGroups().get("run").getArtifacts().add(new Artifact("org.savantbuild.test", "integration-build", "integration-build", "2.1.1-{integration}", "jar"));
    project.getDependencies().put(null, d);

    // Resolve everything in case that messes up the Dependencies
    Workflow w = new Workflow();
    w.getFetchProcesses().add(new Process(map("type", "url", "url", new File("test-deps/savant").toURI().toURL().toString())));
    w.getPublishProcesses().add(new Process(map("type", "cache", "dir", "target/test/deps")));
    DefaultDependencyResolver resolver = new DefaultDependencyResolver(new DefaultOutput());
    resolver.resolve(d, w, null, true);

    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(new Process(map("type", "cache", "dir", "target/test/deps-publish")));

    Publication p = new Publication();
    p.setFile("test-deps/savant/org/savantbuild/test/with-source/1.0/with-source-1.0.jar");
    p.setName("publish-test-artifact");
    p.setType("jar");
    project.getPublications().add(p);

    DefaultDependencyPublisher dpm = new DefaultDependencyPublisher(new DefaultOutput());
    dpm.publish(project, p, pw, true);

    File[] files = new File("target/test/deps-publish/org/savantbuild/test/publish-test/1.0-{integration}").listFiles();
    assertEquals(files.length, 6);

    File amd = null;
    for (File file : files) {
      assertTrue(file.getName().startsWith("publish-test-artifact-1.0-IB"));
      if (file.getName().endsWith(".amd")) {
        amd = file;
      }
    }

    if (amd == null) {
      fail("Missing AMD");
    }

    ArtifactMetaData metaData = ArtifactTools.parseArtifactMetaData(amd);
    assertEquals(metaData.getDependencies().getArtifactGroups().get("run").getArtifacts().get(0).getVersion(), "2.1.1-{integration}");
  }

  @Test(enabled = true)
  public void publishIntegrationTwice() throws InterruptedException {
    publishIntegration();
    Thread.sleep(100);

    Project project = new Project();
    project.setGroup("org.savantbuild.test");
    project.setName("publish-test");
    project.setVersion("1.0");

    PublishWorkflow w = new PublishWorkflow();
    w.getProcesses().add(new Process(map("type", "cache", "dir", "target/test/deps-publish")));

    Publication p = new Publication();
    p.setFile("test-deps/savant/org/savantbuild/test/with-source/1.0/with-source-1.0.jar");
    p.setName("publish-test-artifact");
    p.setType("jar");
    project.getPublications().add(p);

    DefaultDependencyPublisher dpm = new DefaultDependencyPublisher(new DefaultOutput());
    dpm.publish(project, p, w, true);

    File[] files = new File("target/test/deps-publish/org/savantbuild/test/publish-test/1.0-{integration}").listFiles();
    assertEquals(files.length, 12);
    for (File file : files) {
      assertTrue(file.getName().startsWith("publish-test-artifact-1.0-IB"));
    }
  }

  @Test(enabled = true)
  public void publishIntegrationThenFull() throws InterruptedException {
    publishIntegration();
    Thread.sleep(100);

    Project project = new Project();
    project.setGroup("org.savantbuild.test");
    project.setName("publish-test");
    project.setVersion("1.0");

    PublishWorkflow w = new PublishWorkflow();
    w.getProcesses().add(new Process(map("type", "cache", "dir", "target/test/deps-publish")));

    Publication p = new Publication();
    p.setFile("test-deps/savant/org/savantbuild/test/with-source/1.0/with-source-1.0.jar");
    p.setName("publish-test-artifact");
    p.setType("jar");
    project.getPublications().add(p);

    DefaultDependencyPublisher dpm = new DefaultDependencyPublisher(new DefaultOutput());
    dpm.publish(project, p, w, false);

    File dir = new File("target/test/deps-publish/org/savantbuild/test/publish-test/1.0-{integration}");
    assertFalse(dir.isDirectory());
  }
}
