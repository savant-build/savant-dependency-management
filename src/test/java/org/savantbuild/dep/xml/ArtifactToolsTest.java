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
package org.savantbuild.dep.xml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.savantbuild.dep.BaseUnitTest;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.License;
import org.savantbuild.domain.Version;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * This class tests the artifact toolkit.
 *
 * @author Brian Pontarelli
 */
public class ArtifactToolsTest extends BaseUnitTest {
  @Test
  public void parse() throws Exception {
    ArtifactMetaData amd = ArtifactTools.parseArtifactMetaData(projectDir.resolve("src/test/resources/amd.xml"));
    assertEquals(amd.licenses, Arrays.asList(License.Licenses.get("ApacheV2_0"), new License("BSD_2_Clause", "Override the BSD license.")));
    assertEquals(amd.dependencies.groups.size(), 2);
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.size(), 2);
    assertEquals(amd.dependencies.groups.get("runtime").name, "runtime");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).id.project, "test-project");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).id.name, "test-project");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).version, new Version("1.0.0"));
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).id.type, "jar");

    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).id.project, "test-project2");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).id.name, "test-project2");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).version, new Version("2.0.0"));
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).id.type, "jar");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).exclusions, Arrays.asList(
        new ArtifactID("org.example", "exclude-1", "exclude-1", "jar"),
        new ArtifactID("org.example", "exclude-2", "exclude-2", "xml"),
        new ArtifactID("org.example", "exclude-3", "exclude-4", "zip")
    ));

    assertEquals(amd.dependencies.groups.get("compile").dependencies.size(), 2);
    assertEquals(amd.dependencies.groups.get("compile").name, "compile");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(0).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(0).id.project, "test-project3");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(0).id.name, "test-project3");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(0).version, new Version("3.0.0"));
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(0).id.type, "jar");

    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(1).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(1).id.project, "test-project4");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(1).id.name, "test-project4");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(1).version, new Version("4.0.0"));
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(1).id.type, "jar");
  }

  /**
   * Tests that the XML generation works correctly.
   */
  @Test
  public void xml() throws Exception {
    Artifact d1 = new Artifact("group_name\"quoted\":project_name:name\"quoted\":1.0.0:type", false);
    Artifact d2 = new Artifact("group_name2:project_name2\"quoted\":name2:2.0.0:type2", false);
    Artifact d3 = new Artifact("group_name3:project_name3:name3:3.0.0+\"quoted\":type3\"quoted\"", false);
    Artifact d4 = new Artifact("group_name4:project_name4:name4:4.0.0:type4", false, Arrays.asList(
        new ArtifactID("org.example:exclude-1"),
        new ArtifactID("org.example:exclude-2:zip"),
        new ArtifactID("org.example:exclude-3:exclude-4:xml")
    ));
    Artifact d5 = new Artifact("group_name5:project_name5:name5:5.0.0:type5", false, Arrays.asList(
        new ArtifactID("org.example:exclude-1"),
        new ArtifactID("org.example:exclude-2:zip"),
        new ArtifactID("org.example:exclude-3:exclude-4:xml")
    ));

    DependencyGroup compile = new DependencyGroup("compile", true);
    compile.dependencies.add(d1);
    compile.dependencies.add(d2);

    DependencyGroup runtime = new DependencyGroup("runtime", true);
    runtime.dependencies.add(d3);

    DependencyGroup compileOptional = new DependencyGroup("compile-optional", true);
    compileOptional.dependencies.add(d4);

    // Not exported
    DependencyGroup test = new DependencyGroup("test", false);
    test.dependencies.add(d5);

    Dependencies deps = new Dependencies();
    deps.groups.put("compile", compile);
    deps.groups.put("runtime", runtime);
    deps.groups.put("compile-optional", compileOptional);
    deps.groups.put("test", test);

    ArtifactMetaData amd = new ArtifactMetaData(deps, License.Licenses.get("ApacheV2_0"), License.Licenses.get("BSD_2_Clause"), new License("BSD-1-Clause", "Override the license."));
    Path tmp = ArtifactTools.generateXML(amd);
    assertNotNull(tmp);
    assertTrue(Files.isRegularFile(tmp));

    String actual = new String(Files.readAllBytes(tmp));
    String expected = new String(Files.readAllBytes(Paths.get("../savant-dependency-management/src/test/resources/expected-amd.xml")));
    assertEquals(actual, expected);

    // Remove the non-exported group
    deps.groups.remove("test");

    // Then load and compare
    ArtifactMetaData amdOut = ArtifactTools.parseArtifactMetaData(tmp);
    assertEquals(amd, amdOut);

    Files.delete(tmp);
  }
}
