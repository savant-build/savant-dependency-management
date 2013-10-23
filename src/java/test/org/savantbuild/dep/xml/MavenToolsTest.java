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
package org.savantbuild.dep.xml;

import java.io.File;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.Dependencies;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * <p>
 * This test the MavenTools
 * </p>
 *
 * @author Brian Pontarelli
 */
public class MavenToolsTest {
  @Test
  public void pomName() throws Exception {
    Artifact artifact = new Artifact("group", "project", "name", "version", "type");
    assertEquals(MavenTools.pomName(artifact), "name-version.pom");
  }

  @Test
  public void parsePOM() throws Exception {
    Dependencies deps = MavenTools.parsePOM(new File("src/java/test/unit/org/savantbuild/dep/xml/pom-with-no-deps.xml")).getDependencies();
    assertNull(deps);

    deps = MavenTools.parsePOM(new File("src/java/test/unit/org/savantbuild/dep/xml/pom-with-deps.xml")).getDependencies();
    assertNotNull(deps);
    assertEquals(deps.getAllArtifacts().size(), 6);
    assertEquals(deps.getArtifactGroups().get("run").getArtifacts().size(), 2);
    assertEquals(deps.getArtifactGroups().get("run").getArtifacts().get(0).getGroup(), "javax.mail");
    assertEquals(deps.getArtifactGroups().get("run").getArtifacts().get(0).getProject(), "mail");
    assertEquals(deps.getArtifactGroups().get("run").getArtifacts().get(0).getName(), "mail");
    assertEquals(deps.getArtifactGroups().get("run").getArtifacts().get(0).getVersion(), "1.4.1");
    assertEquals(deps.getArtifactGroups().get("run").getArtifacts().get(0).getType(), "jar");

    assertEquals(deps.getArtifactGroups().get("run").getArtifacts().get(1).getGroup(), "javax.activation");
    assertEquals(deps.getArtifactGroups().get("run").getArtifacts().get(1).getProject(), "activation");
    assertEquals(deps.getArtifactGroups().get("run").getArtifacts().get(1).getName(), "activation");
    assertEquals(deps.getArtifactGroups().get("run").getArtifacts().get(1).getVersion(), "1.1");
    assertEquals(deps.getArtifactGroups().get("run").getArtifacts().get(1).getType(), "jar");

    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().size(), 4);
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(0).getGroup(), "junit");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(0).getProject(), "junit");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(0).getName(), "junit");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(0).getVersion(), "3.8.2");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(0).getType(), "jar");

    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(1).getGroup(), "net.sf.retrotranslator");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(1).getProject(), "retrotranslator-runtime");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(1).getName(), "retrotranslator-runtime");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(1).getVersion(), "1.2.1");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(1).getType(), "jar");

    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(2).getGroup(), "org.subethamail");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(2).getProject(), "subethasmtp-smtp");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(2).getName(), "subethasmtp-smtp");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(2).getVersion(), "1.2");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(2).getType(), "jar");

    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(3).getGroup(), "org.subethamail");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(3).getProject(), "subethasmtp-wiser");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(3).getName(), "subethasmtp-wiser");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(3).getVersion(), "1.2");
    assertEquals(deps.getArtifactGroups().get("test-run").getArtifacts().get(3).getType(), "jar");
  }
}
