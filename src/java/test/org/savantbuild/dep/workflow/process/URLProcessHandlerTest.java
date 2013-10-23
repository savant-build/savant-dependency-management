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
package org.savantbuild.dep.workflow.process;

import java.io.File;
import java.net.MalformedURLException;

import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.io.DoesNotExistException;
import org.savantbuild.io.FileTools;
import org.savantbuild.run.output.DefaultOutput;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.savantbuild.TestTools.*;
import static org.testng.Assert.*;

/**
 * <p>
 * This class tests the SavantInternetFetchProcess class.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class URLProcessHandlerTest {
  @DataProvider(name = "fetchData")
  public Object[][] fetchData() {
    return new Object[][]{
      {"major-compat", "1.0", "target/test/deps/org/savantbuild/test/major-compat/1.0/major-compat-1.0.jar"},
      {"major-compat", "1.0.1", "target/test/deps/org/savantbuild/test/major-compat/1.0.1/major-compat-1.0.1.jar"},
      {"major-compat", "1.1", "target/test/deps/org/savantbuild/test/major-compat/1.1/major-compat-1.1.jar"},
      {"major-compat", "2.0", "target/test/deps/org/savantbuild/test/major-compat/2.0/major-compat-2.0.jar"},
      {"minor-compat", "1.0", "target/test/deps/org/savantbuild/test/minor-compat/1.0/minor-compat-1.0.jar"},
      {"minor-compat", "1.0.1", "target/test/deps/org/savantbuild/test/minor-compat/1.0.1/minor-compat-1.0.1.jar"},
      {"minor-compat", "1.1", "target/test/deps/org/savantbuild/test/minor-compat/1.1/minor-compat-1.1.jar"},
      {"minor-compat", "2.0", "target/test/deps/org/savantbuild/test/minor-compat/2.0/minor-compat-2.0.jar"},
      {"patch-compat", "1.0", "target/test/deps/org/savantbuild/test/patch-compat/1.0/patch-compat-1.0.jar"},
      {"patch-compat", "1.0.1", "target/test/deps/org/savantbuild/test/patch-compat/1.0.1/patch-compat-1.0.1.jar"},
      {"patch-compat", "1.1", "target/test/deps/org/savantbuild/test/patch-compat/1.1/patch-compat-1.1.jar"},
      {"patch-compat", "2.0", "target/test/deps/org/savantbuild/test/patch-compat/2.0/patch-compat-2.0.jar"}
    };
  }

  @Test(dataProvider = "fetchData")
  public void fetch(String name, String version, String result) throws Exception {
    FileTools.prune(new File("target/test/deps"));

    Artifact artifact = new Artifact("org.savantbuild.test", name, name, version, "jar");

    CacheProcess pp = new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps"));
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(pp);

    URLProcess ufp = new URLProcess(new DefaultOutput(), map("url", makeCurDirURL() + "/test-deps/savant"));
    File file = ufp.fetch(artifact, artifact.getArtifactFile(), pw);
    assertNotNull(file);

    assertEquals(file.getAbsolutePath(), new File(result).getAbsolutePath());
  }

  @Test(enabled = true)
  public void missingItem() throws Exception {
    FileTools.prune(new File("target/test/deps"));

    Artifact artifact = new Artifact("org.savantbuild.test", "missing-item", "missing-item", "1.0", "jar");

    CacheProcess process = new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps"));
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(new DefaultOutput(), map("url", makeCurDirURL() + "/test-deps/savant"));
    try {
      ufp.fetch(artifact, artifact.getArtifactFile(), pw);
      fail("Should have failed because item doesn't exist");
    } catch (DoesNotExistException e) {
      // Expected
      assertTrue(e.getMessage().contains("missing-item-1.0.jar"));
    }
  }

  @Test(enabled = true)
  public void missingAMD() throws Exception {
    FileTools.prune(new File("target/test/deps"));

    Artifact artifact = new Artifact("org.savantbuild.test", "missing-item", "missing-item", "1.0", "jar");

    CacheProcess process = new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps"));
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(new DefaultOutput(), map("url", makeCurDirURL() + "/test-deps/savant"));
    try {
      ufp.fetchMetaData(artifact, pw);
      fail("Should have failed because AMD doesn't exist");
    } catch (DoesNotExistException e) {
      // Expected
      assertTrue(e.getMessage().contains("ArtifactMetaData"));
    }
  }

  @Test(enabled = true)
  public void missingMD5() throws Exception {
    FileTools.prune(new File("target/test/deps"));

    Artifact artifact = new Artifact("org.savantbuild.test", "missing-md5", "missing-md5", "1.0", "jar");

    CacheProcess process = new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps"));
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(new DefaultOutput(), map("url", makeCurDirURL() + "/test-deps/savant"));
    try {
      ufp.fetch(artifact, artifact.getArtifactFile(), pw);
      fail("Should have failed because MD5 doesn't exist");
    } catch (DoesNotExistException e) {
      // Expected
      assertTrue(e.getMessage().contains(".md5"));
    }
  }

  @Test(enabled = true)
  public void metaData() throws Exception {
    FileTools.prune(new File("target/test/deps"));

    Artifact artifact = new Artifact("org.savantbuild.test", "dependencies", "dependencies", "1.0", "jar");

    CacheProcess process = new CacheProcess(new DefaultOutput(), map("dir", "target/test/deps"));
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(new DefaultOutput(), map("url", makeCurDirURL() + "/test-deps/savant"));

    ArtifactMetaData amd = ufp.fetchMetaData(artifact, pw);
    assertNull(amd.getCompatibility());
    assertEquals(amd.getDependencies().getArtifactGroups().size(), 1);
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().size(), 3);
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(0).getGroup(), "org.savantbuild.test");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(0).getProject(), "major-compat");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(0).getName(), "major-compat");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(0).getVersion(), "2.0");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(0).getType(), "jar");

    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(1).getGroup(), "org.savantbuild.test");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(1).getProject(), "minor-compat");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(1).getName(), "minor-compat");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(1).getVersion(), "1.1");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(1).getType(), "jar");

    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(2).getGroup(), "org.savantbuild.test");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(2).getProject(), "patch-compat");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(2).getName(), "patch-compat");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(2).getVersion(), "1.0");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(2).getType(), "jar");
  }

  @Test(enabled = true)
  public void integrationVersionFile() throws Exception {
    Artifact artifact = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "2.1.1-{integration}", "jar");
    URLProcess ufp = new URLProcess(new DefaultOutput(), map("url", makeCurDirURL() + "/test-deps/savant"));

    String version = ufp.determineVersion(artifact);
    assertEquals(version, "2.1.1-IB20080103144403111");
  }

  @Test(enabled = true)
  public void latestVersion() throws Exception {
    Artifact artifact = new Artifact("org.savantbuild.test", "major-compat", "major-compat", "{latest}", "jar");
    URLProcess ufp = new URLProcess(new DefaultOutput(), map("url", makeCurDirURL() + "/test-deps/savant"));

    String version = ufp.determineVersion(artifact);
    assertEquals(version, "2.0");
  }

  @Test(enabled = true)
  public void latestAndIntegrationVersion() throws Exception {
    Artifact artifact = new Artifact("org.savantbuild.test", "integration-build", "integration-build", "{latest}", "jar");
    URLProcess ufp = new URLProcess(new DefaultOutput(), map("url", makeCurDirURL() + "/test-deps/savant"));

    String version = ufp.determineVersion(artifact);
    assertEquals(version, "2.1.1-{integration}");
  }

  private String makeCurDirURL() throws MalformedURLException {
    File file = new File("");
    return file.toURI().toURL().toString();
  }
}
