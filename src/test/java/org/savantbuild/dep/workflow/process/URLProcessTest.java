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
package org.savantbuild.dep.workflow.process;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.savantbuild.dep.BaseUnitTest;
import org.savantbuild.dep.PathTools;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.ReifiedArtifact;
import org.savantbuild.dep.domain.ResolvableItem;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.sun.net.httpserver.HttpServer;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * This class tests the URLProcess class.
 *
 * @author Brian Pontarelli
 */
public class URLProcessTest extends BaseUnitTest {
  private HttpServer server;

  @Test(dataProvider = "fetchData")
  public void fetch(String url, String name, String version, String result) throws Exception {
    PathTools.prune(projectDir.resolve("build/test/cache"));

    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:" + name + ":" + name + ":" + version + ":jar", License.Licenses.get("ApacheV2_0"));
    URLProcess ufp = new URLProcess(output, url, null, null);
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path file = ufp.fetch(item, new PublishWorkflow(new CacheProcess(output, cache.toString(), integration.toString())));
    assertNotNull(file);

    assertEquals((Object) file.toAbsolutePath(), Paths.get(result).toAbsolutePath());
  }

  @DataProvider(name = "fetchData")
  public Object[][] fetchData() {
    return new Object[][]{
        {makeLocalURL(), "multiple-versions", "1.0.0", projectDir.resolve("build/test/cache/org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar").toString()},
        {makeLocalURL(), "leaf1", "1.0.0", projectDir.resolve("build/test/cache/org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar").toString()},
        {"http://localhost:7042/test-deps/savant", "multiple-versions", "1.0.0", projectDir.resolve("build/test/cache/org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar").toString()},
        {"http://localhost:7042/test-deps/savant", "leaf1", "1.0.0", projectDir.resolve("build/test/cache/org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar").toString()}
    };
  }

  @Test(dataProvider = "urls")
  public void metaData(String url) throws Exception {
    PathTools.prune(projectDir.resolve("build/test/cache"));

    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:multiple-versions:multiple-versions:1.0:jar", License.Licenses.get("ApacheV2_0"));

    CacheProcess process = new CacheProcess(output, cache.toString(), integration.toString());
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(output, url, null, null);
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactMetaDataFile());
    Path amd = ufp.fetch(item, pw);
    assertNotNull(amd);
  }

  @Test(dataProvider = "urls")
  public void missingAMD(String url) throws Exception {
    PathTools.prune(projectDir.resolve("build/test/cache"));

    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:missing-item:missing-item:1.0:jar", License.Licenses.get("ApacheV2_0"));

    CacheProcess process = new CacheProcess(output, cache.toString(), integration.toString());
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(output, url, null, null);
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactMetaDataFile());
    Path file = ufp.fetch(item, pw);
    assertNull(file);
  }

  @Test(dataProvider = "urls")
  public void missingItem(String url) throws Exception {
    PathTools.prune(projectDir.resolve("build/test/cache"));

    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:missing-item:missing-item:1.0:jar", License.Licenses.get("ApacheV2_0"));

    CacheProcess process = new CacheProcess(output, cache.toString(), integration.toString());
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(output, url, null, null);
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path file = ufp.fetch(item, pw);
    assertNull(file);
  }

  @Test
  public void missingMD5() throws Exception {
    PathTools.prune(projectDir.resolve("build/test/cache"));

    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:missing-md5:missing-md5:1.0:jar", License.Licenses.get("ApacheV2_0"));

    CacheProcess process = new CacheProcess(output, cache.toString(), integration.toString());
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(output, makeLocalURL(), null, null);
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path file = ufp.fetch(item, pw);
    assertNull(file);
  }

  @BeforeMethod
  public void setupFileServer() throws Exception {
    server = makeFileServer(null, null);
  }

  @AfterMethod
  public void stopFileServer() {
    server.stop(0);
  }

  @DataProvider(name = "urls")
  public Object[][] urls() {
    return new Object[][]{
        {makeLocalURL()},
        {"http://localhost:7042/test-deps/savant"}
    };
  }

  private String makeLocalURL() {
    return projectDir.toAbsolutePath().toUri() + "/test-deps/savant";
  }
}
