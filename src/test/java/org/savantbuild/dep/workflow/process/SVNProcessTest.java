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

import java.nio.file.Files;
import java.nio.file.Path;

import org.savantbuild.dep.BaseUnitTest;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.ReifiedArtifact;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.io.FileTools;
import org.savantbuild.lang.RuntimeTools;
import org.savantbuild.security.MD5;
import org.savantbuild.util.MapBuilder;
import org.testng.annotations.BeforeMethod;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * SVNProcess Tester.
 *
 * @author Brian Pontarelli
 */
public class SVNProcessTest extends BaseUnitTest {
  @BeforeMethod
  public void deleteRepository() throws Exception {
    if (Files.isDirectory(projectDir.resolve("build/test/svn-repository"))) {
      FileTools.prune(projectDir.resolve("build/test/svn-repository"));
    }

    if (Files.isDirectory(projectDir.resolve("build/test/cache"))) {
      FileTools.prune(projectDir.resolve("build/test/cache"));
    }

    assertFalse(Files.exists(projectDir.resolve("build/test/cache")));
    assertFalse(Files.exists(projectDir.resolve("build/test/svn-repository")));
    assertEquals(RuntimeTools.exec("svnadmin", "create", projectDir.resolve("build/test/svn-repository").toString()).exitCode, 0);
    assertTrue(Files.exists(projectDir.resolve("build/test/svn-repository")));
  }

  public void run() throws Exception {
    Artifact artifact = new ReifiedArtifact("org.savantbuild.test:svn-process-test:1.0", MapBuilder.simpleMap(License.ApacheV2_0, null));

    Path md5File = FileTools.createTempPath("savant-process", "md5", true);
    Path file = projectDir.resolve("src/test/java/org/savantbuild/dep/BaseUnitTest.java").toRealPath();
    MD5.writeMD5(MD5.forBytes(Files.readAllBytes(file), "BaseTest.java"), md5File);

    SVNProcess process = new SVNProcess(output, "file:///" + projectDir.resolve("build/test/svn-repository").toRealPath(), null, null);
    process.publish(artifact, artifact.getArtifactFile() + ".md5", md5File);
    process.publish(artifact, artifact.getArtifactFile(), file);

    process.fetch(artifact, artifact.getArtifactFile(), new PublishWorkflow(new CacheProcess(output, cache.toString())));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/cache/org/savantbuild/test/svn-process-test/1.0.0/svn-process-test-1.0.0.jar")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/cache/org/savantbuild/test/svn-process-test/1.0.0/svn-process-test-1.0.0.jar.md5")));
  }
}
