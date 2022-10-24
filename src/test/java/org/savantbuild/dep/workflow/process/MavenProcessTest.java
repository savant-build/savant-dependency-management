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
import org.savantbuild.dep.maven.MavenTools;
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
 * This class tests the MavenProcess class.
 *
 * @author Brian Pontarelli
 */
public class MavenProcessTest extends BaseUnitTest {
  @Test(enabled = false)
  public void mavenCentral() throws Exception {
    PathTools.prune(projectDir.resolve("build/test/cache"));

    Artifact artifact = new ReifiedArtifact("org.apache.groovy:groovy-all:groovy-all:4.0.5:pom", License.Licenses.get("Apache-2.0"));
    MavenProcess process = new MavenProcess(output, "https://repo1.maven.org/maven2", null, null);
    Path file = process.fetch(artifact, artifact.getArtifactFile(), new PublishWorkflow(new CacheProcess(output, cache.toString())));
    assertEquals(file, Paths.get("build/test/cache/org/apache/groovy/groovy-all/4.0.5/groovy-all-4.0.5.pom"));

    artifact = new ReifiedArtifact("org.apache.groovy:groovy:groovy:4.0.5:jar", License.Licenses.get("Apache-2.0"));
    file = process.fetch(artifact, artifact.getArtifactMetaDataFile(), new PublishWorkflow(new CacheProcess(output, cache.toString())));
    assertEquals(file, Paths.get("build/test/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar.amd"));
  }
}
