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

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.run.output.DefaultOutput;
import org.testng.annotations.Test;

import static org.savantbuild.TestTools.*;
import static org.testng.Assert.*;

/**
 * <p>
 * This class is a test case that tests the dependency deletion.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class DefaultDependencyDeleterTest extends AbstractDependencyTest {
  private DefaultDependencyResolverTest setup = new DefaultDependencyResolverTest();

  @Test(enabled = true)
  public void cleanEverything() throws Exception {
    setup.projectHasTransitives();

    Artifact a = new Artifact("org.savantbuild.test", "dependencies", "dependencies", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    File root = new File("test-deps/savant");
    Workflow w = makeWorkflow(root);

    DefaultDependencyDeleter dm = new DefaultDependencyDeleter(new DefaultOutput());
    dm.delete(d, w, true);

    File[] files = new File("target/test/deps/org/savantbuild/test/dependencies/1.0").listFiles();
    assertEquals(files.length, 0);

    files = new File("target/test/deps/org/savantbuild/test/major-compat/2.0").listFiles();
    assertEquals(files.length, 0);

    files = new File("target/test/deps/org/savantbuild/test/minor-compat/1.1").listFiles();
    assertEquals(files.length, 0);

    files = new File("target/test/deps/org/savantbuild/test/patch-compat/1.0").listFiles();
    assertEquals(files.length, 0);
  }

  @Test(enabled = true)
  public void multiplePublish() throws Exception {
    setup.resolveSingleProjectDependencyNoTransitivesMultipleCaches();

    Artifact a = new Artifact("org.savantbuild.test", "no-amd", "no-amd", "1.0", "jar");
    DependencyGroup group = new DependencyGroup("run");
    group.getArtifacts().add(a);

    Dependencies d = new Dependencies();
    d.getArtifactGroups().put("run", group);

    File root = new File("test-deps/savant");
    Workflow w = makeWorkflow(root);
    w.getPublishProcesses().add(new Process(map("type", "cache", "dir", "target/test/deps2")));

    DefaultDependencyDeleter dm = new DefaultDependencyDeleter(new DefaultOutput());
    dm.delete(d, w, true);

    File[] files = new File("target/test/deps/org/savantbuild/test/no-amd/1.0").listFiles();
    assertEquals(files.length, 0);

    // Second cache
    files = new File("target/test/deps2/org/savantbuild/test/no-amd/1.0").listFiles();
    assertEquals(files.length, 0);
  }
}
