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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.savantbuild.BuildException;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.io.FileTools;
import org.savantbuild.run.output.DefaultOutput;
import org.testng.annotations.Test;

import static java.util.Arrays.*;
import static org.savantbuild.TestTools.*;
import static org.testng.Assert.*;

/**
 * <p>
 * This class is a test case that tests the dependency mediator
 * and how the artifact dependencies are resolved.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class DefaultDependencyResolverTest {
  @Test(enabled = true)
  public void projectHasNoDependencies() {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    File root = new File("test-deps/savant");
    Workflow w = makeWorkflow(root);
    Dependencies d = new Dependencies();

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());
    dm.resolve(d, w, null, true);

    assertFalse(cache.exists());
  }

  @Test(enabled = true)
  public void projectHasOnEemptyArtifactGroup() {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Dependencies d = new Dependencies();
    DependencyGroup empty = new DependencyGroup("compile");
    d.getArtifactGroups().put("compile", empty);

    File root = new File("test-deps/savant");
    Workflow w = makeWorkflow(root);

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());
    dm.resolve(d, w, null, true);

    assertFalse(cache.exists());
  }

  @Test(enabled = true)
  public void resolveSingleProjectDependencyNoTransitives() {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Artifact a = new Artifact("org.savantbuild.test", "no-amd", "no-amd", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    File root = new File("test-deps/savant");
    Workflow w = makeWorkflow(root);

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());
    dm.resolve(d, w, null, true);

    File[] files = new File(cache, "org/savantbuild/test").listFiles();
    assertEquals(files.length, 1);

    files = new File(cache, "org/savantbuild/test/no-amd/1.0").listFiles();
    assertEquals(files.length, 4);

    Arrays.sort(files);
    assertEquals(files[0].getName(), "no-amd-1.0-src.jar.neg");
    assertEquals(files[1].getName(), "no-amd-1.0.jar");
    assertEquals(files[2].getName(), "no-amd-1.0.jar.amd.neg");
    assertEquals(files[3].getName(), "no-amd-1.0.jar.md5");

    Dependencies dependencies = dm.dependencies(a, w);
    assertNotNull(dependencies);
    assertEquals(dependencies.getArtifactGroups().size(), 0);
  }

  @Test(enabled = true)
  public void resolveSingleProjectDependencyNoTransitivesMultipleCaches() {
    File cache = new File("target/test/deps");
    File cache2 = new File("target/test/deps2");
    FileTools.prune(cache);
    FileTools.prune(cache2);

    Artifact a = new Artifact("org.savantbuild.test", "no-amd", "no-amd", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    File root = new File("test-deps/savant");
    Workflow w = makeWorkflow(root);
    w.getPublishProcesses().add(new Process(map("type", "cache", "dir", "target/test/deps2")));

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());
    dm.resolve(d, w, null, true);

    File[] files = new File(cache, "org/savantbuild/test").listFiles();
    assertEquals(files.length, 1);

    files = new File(cache, "org/savantbuild/test/no-amd/1.0").listFiles();
    assertEquals(files.length, 4);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "no-amd-1.0-src.jar.neg");
    assertEquals(files[1].getName(), "no-amd-1.0.jar");
    assertEquals(files[2].getName(), "no-amd-1.0.jar.amd.neg");
    assertEquals(files[3].getName(), "no-amd-1.0.jar.md5");

    files = new File(cache2, "org/savantbuild/test").listFiles();
    assertEquals(files.length, 1);

    files = new File(cache, "org/savantbuild/test/no-amd/1.0").listFiles();
    assertEquals(files.length, 4);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "no-amd-1.0-src.jar.neg");
    assertEquals(files[1].getName(), "no-amd-1.0.jar");
    assertEquals(files[2].getName(), "no-amd-1.0.jar.amd.neg");
    assertEquals(files[3].getName(), "no-amd-1.0.jar.md5");
  }

  @Test(enabled = true)
  public void projectHasTransitives() {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Artifact a = new Artifact("org.savantbuild.test", "dependencies", "dependencies", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    File root = new File("test-deps/savant");
    Workflow w = makeWorkflow(root);

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());
    dm.resolve(d, w, null, true);

    File result = new File(cache, "org/savantbuild/test");
    File[] files = result.listFiles();
    assertEquals(files.length, 4);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "dependencies");
    assertEquals(files[1].getName(), "major-compat");
    assertEquals(files[2].getName(), "minor-compat");
    assertEquals(files[3].getName(), "patch-compat");

    files = new File(cache, "org/savantbuild/test/dependencies/1.0").listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "dependencies-1.0-src.jar.neg");
    assertEquals(files[1].getName(), "dependencies-1.0.jar");
    assertEquals(files[2].getName(), "dependencies-1.0.jar.amd");
    assertEquals(files[3].getName(), "dependencies-1.0.jar.amd.md5");
    assertEquals(files[4].getName(), "dependencies-1.0.jar.md5");

    files = new File(cache, "org/savantbuild/test/major-compat/2.0").listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "major-compat-2.0-src.jar.neg");
    assertEquals(files[1].getName(), "major-compat-2.0.jar");
    assertEquals(files[2].getName(), "major-compat-2.0.jar.amd");
    assertEquals(files[3].getName(), "major-compat-2.0.jar.amd.md5");
    assertEquals(files[4].getName(), "major-compat-2.0.jar.md5");

    files = new File(cache, "org/savantbuild/test/minor-compat/1.1").listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "minor-compat-1.1-src.jar.neg");
    assertEquals(files[1].getName(), "minor-compat-1.1.jar");
    assertEquals(files[2].getName(), "minor-compat-1.1.jar.amd");
    assertEquals(files[3].getName(), "minor-compat-1.1.jar.amd.md5");
    assertEquals(files[4].getName(), "minor-compat-1.1.jar.md5");

    files = new File(cache, "org/savantbuild/test/patch-compat/1.0").listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "patch-compat-1.0-src.jar.neg");
    assertEquals(files[1].getName(), "patch-compat-1.0.jar");
    assertEquals(files[2].getName(), "patch-compat-1.0.jar.amd");
    assertEquals(files[3].getName(), "patch-compat-1.0.jar.amd.md5");
    assertEquals(files[4].getName(), "patch-compat-1.0.jar.md5");

    Dependencies dependencies = dm.dependencies(a, w);
    assertNotNull(dependencies);
    assertEquals(dependencies.getAllArtifacts().size(), 3);
  }

  @Test(enabled = true)
  public void missingDependencyFailure() {
    Artifact a = new Artifact("bad-group", "missing-project", "missing-artifact", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    Workflow w = makeWorkflow(new File("target/test/deps"));

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());

    try {
      dm.resolve(d, w, null, true);
      fail("Should have failed because dependency isn't found");
    } catch (BuildException e) {
      // Expected
    }
  }

  @Test(enabled = true)
  public void versionUpgrade() {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Artifact a = new Artifact("org.savantbuild.test", "upgrade-versions", "upgrade-versions", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    File root = new File("test-deps/savant");
    Workflow w = makeWorkflow(root);

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());
    dm.resolve(d, w, null, true);

    File result = new File(cache, "org/savantbuild/test");
    File[] files = result.listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "dependencies");
    assertEquals(files[1].getName(), "major-compat");
    assertEquals(files[2].getName(), "minor-compat");
    assertEquals(files[3].getName(), "patch-compat");
    assertEquals(files[4].getName(), "upgrade-versions");

    files = new File(cache, "org/savantbuild/test/dependencies/1.0").listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "dependencies-1.0-src.jar.neg");
    assertEquals(files[1].getName(), "dependencies-1.0.jar");
    assertEquals(files[2].getName(), "dependencies-1.0.jar.amd");
    assertEquals(files[3].getName(), "dependencies-1.0.jar.amd.md5");
    assertEquals(files[4].getName(), "dependencies-1.0.jar.md5");

    files = new File(cache, "org/savantbuild/test/major-compat/1.0").listFiles();
    assertEquals(files.length, 2);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "major-compat-1.0.jar.amd");
    assertEquals(files[1].getName(), "major-compat-1.0.jar.amd.md5");

    files = new File(cache, "org/savantbuild/test/major-compat/2.0").listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "major-compat-2.0-src.jar.neg");
    assertEquals(files[1].getName(), "major-compat-2.0.jar");
    assertEquals(files[2].getName(), "major-compat-2.0.jar.amd");
    assertEquals(files[3].getName(), "major-compat-2.0.jar.amd.md5");
    assertEquals(files[4].getName(), "major-compat-2.0.jar.md5");

    files = new File(cache, "org/savantbuild/test/minor-compat/1.0").listFiles();
    assertEquals(files.length, 2);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "minor-compat-1.0.jar.amd");
    assertEquals(files[1].getName(), "minor-compat-1.0.jar.amd.md5");

    files = new File(cache, "org/savantbuild/test/minor-compat/1.1").listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "minor-compat-1.1-src.jar.neg");
    assertEquals(files[1].getName(), "minor-compat-1.1.jar");
    assertEquals(files[2].getName(), "minor-compat-1.1.jar.amd");
    assertEquals(files[3].getName(), "minor-compat-1.1.jar.amd.md5");
    assertEquals(files[4].getName(), "minor-compat-1.1.jar.md5");

    files = new File(cache, "org/savantbuild/test/patch-compat/1.0").listFiles();
    assertEquals(files.length, 2);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "patch-compat-1.0.jar.amd");
    assertEquals(files[1].getName(), "patch-compat-1.0.jar.amd.md5");

    files = new File(cache, "org/savantbuild/test/patch-compat/1.0.1").listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "patch-compat-1.0.1-src.jar.neg");
    assertEquals(files[1].getName(), "patch-compat-1.0.1.jar");
    assertEquals(files[2].getName(), "patch-compat-1.0.1.jar.amd");
    assertEquals(files[3].getName(), "patch-compat-1.0.1.jar.amd.md5");
    assertEquals(files[4].getName(), "patch-compat-1.0.1.jar.md5");
  }

  @Test(enabled = true)
  public void versionCompatibilityFailure() {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Artifact a = new Artifact("org.savantbuild.test", "incompatible-versions", "incompatible-versions", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    File root = new File("test-deps/savant");
    Workflow w = makeWorkflow(root);

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());
    try {
      dm.resolve(d, w, null, true);
      fail("Should have failed");
    } catch (BuildException e) {
      // Expected
      System.out.println(e.getErrors());
    }

    File result = new File(cache, "org/savantbuild/test");
    File[] files = result.listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "dependencies");
    assertEquals(files[1].getName(), "incompatible-versions");
    assertEquals(files[2].getName(), "major-compat");
    assertEquals(files[3].getName(), "minor-compat");
    assertEquals(files[4].getName(), "patch-compat");

    files = new File(cache, "org/savantbuild/test/dependencies/1.0").listFiles();
    assertEquals(files.length, 2);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "dependencies-1.0.jar.amd");
    assertEquals(files[1].getName(), "dependencies-1.0.jar.amd.md5");

    files = new File(cache, "org/savantbuild/test/major-compat/2.0").listFiles();
    assertEquals(files.length, 2);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "major-compat-2.0.jar.amd");
    assertEquals(files[1].getName(), "major-compat-2.0.jar.amd.md5");

    files = new File(cache, "org/savantbuild/test/minor-compat/1.1").listFiles();
    assertEquals(files.length, 2);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "minor-compat-1.1.jar.amd");
    assertEquals(files[1].getName(), "minor-compat-1.1.jar.amd.md5");

    files = new File(cache, "org/savantbuild/test/minor-compat/2.0").listFiles();
    assertEquals(files.length, 2);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "minor-compat-2.0.jar.amd");
    assertEquals(files[1].getName(), "minor-compat-2.0.jar.amd.md5");

    files = new File(cache, "org/savantbuild/test/patch-compat/1.0").listFiles();
    assertEquals(files.length, 2);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "patch-compat-1.0.jar.amd");
    assertEquals(files[1].getName(), "patch-compat-1.0.jar.amd.md5");
  }

  @Test(enabled = true)
  public void artifactGroupFiltering() throws Exception {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Artifact a = new Artifact("org.savantbuild.test", "dependencies-with-groups", "dependencies-with-groups", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("compile");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("compile", group);

    File root = new File("test-deps/savant");
    Workflow w = makeWorkflow(root);

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());
    dm.resolve(d, w, set("compile"), true);

    File result = new File(cache, "org/savantbuild/test");
    File[] files = result.listFiles();
    assertEquals(files.length, 4);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "dependencies-with-groups");
    assertEquals(files[1].getName(), "major-compat");
    assertEquals(files[2].getName(), "minor-compat");
    assertEquals(files[3].getName(), "patch-compat");

    files = new File(cache, "org/savantbuild/test/dependencies-with-groups/1.0").listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "dependencies-with-groups-1.0-src.jar.neg");
    assertEquals(files[1].getName(), "dependencies-with-groups-1.0.jar");
    assertEquals(files[2].getName(), "dependencies-with-groups-1.0.jar.amd");
    assertEquals(files[3].getName(), "dependencies-with-groups-1.0.jar.amd.md5");
    assertEquals(files[4].getName(), "dependencies-with-groups-1.0.jar.md5");

    files = new File(cache, "org/savantbuild/test/major-compat/2.0").listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "major-compat-2.0-src.jar.neg");
    assertEquals(files[1].getName(), "major-compat-2.0.jar");
    assertEquals(files[2].getName(), "major-compat-2.0.jar.amd");
    assertEquals(files[3].getName(), "major-compat-2.0.jar.amd.md5");
    assertEquals(files[4].getName(), "major-compat-2.0.jar.md5");

    files = new File(cache, "org/savantbuild/test/minor-compat/1.1").listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "minor-compat-1.1-src.jar.neg");
    assertEquals(files[1].getName(), "minor-compat-1.1.jar");
    assertEquals(files[2].getName(), "minor-compat-1.1.jar.amd");
    assertEquals(files[3].getName(), "minor-compat-1.1.jar.amd.md5");
    assertEquals(files[4].getName(), "minor-compat-1.1.jar.md5");

    files = new File(cache, "org/savantbuild/test/patch-compat/1.0").listFiles();
    assertEquals(files.length, 2, "Bad file list " + asList(files));
    Arrays.sort(files);
    assertEquals(files[0].getName(), "patch-compat-1.0.jar.amd");
    assertEquals(files[1].getName(), "patch-compat-1.0.jar.amd.md5");
  }

  @Test(enabled = true)
  public void nonTransitive() throws Exception {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Artifact a = new Artifact("org.savantbuild.test", "dependencies", "dependencies", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    File root = new File("test-deps/savant");
    Workflow w = makeWorkflow(root);

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());
    dm.resolve(d, w, null, false);

    File result = new File(cache, "org/savantbuild/test");
    File[] files = result.listFiles();
    assertEquals(files.length, 1);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "dependencies");

    files = new File(cache, "org/savantbuild/test/dependencies/1.0").listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "dependencies-1.0-src.jar.neg");
    assertEquals(files[1].getName(), "dependencies-1.0.jar");
    assertEquals(files[2].getName(), "dependencies-1.0.jar.amd");
    assertEquals(files[3].getName(), "dependencies-1.0.jar.amd.md5");
    assertEquals(files[4].getName(), "dependencies-1.0.jar.md5");
  }

  @Test
  public void remoteIntegration() {
    File cache = new File("target/test/deps");
    FileTools.prune(cache);

    Artifact a = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "2.1.1-{integration}", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    File root = new File("test-deps/savant");
    Workflow w = makeWorkflow(root);

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());
    dm.resolve(d, w, null, false);

    File result = new File(cache, "org/savantbuild/test");
    File[] files = result.listFiles();
    assertEquals(files.length, 1);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "integration-build");

    files = new File(cache, "org/savantbuild/test/integration-build/2.1.1-{integration}").listFiles();
    assertEquals(files.length, 5);
    Arrays.sort(files);
    assertEquals(files[0].getName(), "integration-build-2.1.1-IB20080103144403111-src.jar.neg");
    assertEquals(files[1].getName(), "integration-build-2.1.1-IB20080103144403111.jar");
    assertEquals(files[2].getName(), "integration-build-2.1.1-IB20080103144403111.jar.amd");
    assertEquals(files[3].getName(), "integration-build-2.1.1-IB20080103144403111.jar.amd.md5");
    assertEquals(files[4].getName(), "integration-build-2.1.1-IB20080103144403111.jar.md5");
  }

  @Test
  public void localIntegration() {
    Artifact a = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "2.1.1-{integration}", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    Workflow w = new Workflow();
    w.getFetchProcesses().add(new Process(map("type", "cache", "dir", "test-deps/savant")));

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());
    Map<Artifact, File> files = dm.resolve(d, w, null, false);
    assertTrue(files.get(a).getAbsolutePath().endsWith("test-deps/savant/org/savantbuild/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-IB20080103144403111.jar"));
  }

  @Test
  public void latest() {
    Artifact a = new Artifact("org.savantbuild.test", "minor-compat", "minor-compat", "{latest}", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    Workflow w = new Workflow();
    w.getFetchProcesses().add(new Process(map("type", "cache", "dir", "test-deps/savant")));

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());
    Map<Artifact, File> files = dm.resolve(d, w, null, false);
    assertTrue(files.get(a).getAbsolutePath().endsWith("test-deps/savant/org/savantbuild/test/minor-compat/2.0/minor-compat-2.0.jar"));
  }

  @Test
  public void latestIntegration() {
    Artifact a = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "{latest}", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    Workflow w = new Workflow();
    w.getFetchProcesses().add(new Process(map("type", "cache", "dir", "test-deps/savant")));

    DefaultDependencyResolver dm = new DefaultDependencyResolver(new DefaultOutput());
    Map<Artifact, File> files = dm.resolve(d, w, null, false);
    assertTrue(files.get(a).getAbsolutePath().endsWith("test-deps/savant/org/savantbuild/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-IB20080103144403111.jar"));
  }

  /**
   * Sets up a simple workflow that fetches via URLs and caches to the target dir.
   *
   * @param root The root for the URls.
   * @return The workflow;
   */
  private Workflow makeWorkflow(File root) {
    try {
      Workflow w = new Workflow();
      w.getFetchProcesses().add(new Process(map("type", "url", "url", root.toURI().toURL().toString())));
      w.getPublishProcesses().add(new Process(map("type", "cache", "dir", "target/test/deps")));
      return w;
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private Set<String> set(String... strs) {
    Set<String> set = new HashSet<String>();
    set.addAll(Arrays.asList(strs));
    return set;
  }
}
