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

import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Dependency;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.Version;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * This class tests the artifact toolkit.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class ArtifactToolsTest {
  /**
   * Tests that the XML generation works correctly.
   */
  @Test
  public void xml() throws Exception {
    Dependency d1 = new Dependency("group_name:project_name:name:1.0.0:type", false);
    Dependency d2 = new Dependency("group_name2:project_name2:name2:2.0.0:type2", true);
    Dependency d3 = new Dependency("group_name3:project_name3:name3:3.0.0:type3", false);

    DependencyGroup group = new DependencyGroup("compile");
    group.dependencies.add(d1);
    group.dependencies.add(d2);

    DependencyGroup group1 = new DependencyGroup("run");
    group1.dependencies.add(d3);

    Dependencies deps = new Dependencies();
    deps.groups.put("compile", group);
    deps.groups.put("run", group1);

    ArtifactMetaData amd = new ArtifactMetaData(deps);

    Path tmp = ArtifactTools.generateXML(amd);
    assertNotNull(tmp);
    assertTrue(Files.isRegularFile(tmp));

    DocumentBuilder b = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document d = b.parse(tmp.toFile());
    Element root = d.getDocumentElement();
    assertEquals(2, root.getElementsByTagName("dependency-group").getLength());

    Element groupElem = (Element) root.getElementsByTagName("dependency-group").item(0);
    assertEquals(2, groupElem.getElementsByTagName("dependency").getLength());

    Element elem = (Element) groupElem.getElementsByTagName("dependency").item(0);
    assertEquals("group_name", elem.getAttribute("group"));
    assertEquals("project_name", elem.getAttribute("project"));
    assertEquals("name", elem.getAttribute("name"));
    assertEquals("1.0.0", elem.getAttribute("version"));
    assertEquals("type", elem.getAttribute("type"));
    assertEquals("false", elem.getAttribute("optional"));

    elem = (Element) groupElem.getElementsByTagName("dependency").item(1);
    assertEquals("group_name2", elem.getAttribute("group"));
    assertEquals("project_name2", elem.getAttribute("project"));
    assertEquals("name2", elem.getAttribute("name"));
    assertEquals("2.0.0", elem.getAttribute("version"));
    assertEquals("type2", elem.getAttribute("type"));
    assertEquals("true", elem.getAttribute("optional"));

    groupElem = (Element) root.getElementsByTagName("dependency-group").item(1);
    assertEquals(1, groupElem.getElementsByTagName("dependency").getLength());

    elem = (Element) groupElem.getElementsByTagName("dependency").item(0);
    assertEquals("group_name3", elem.getAttribute("group"));
    assertEquals("project_name3", elem.getAttribute("project"));
    assertEquals("name3", elem.getAttribute("name"));
    assertEquals("3.0.0", elem.getAttribute("version"));
    assertEquals("type3", elem.getAttribute("type"));
    assertEquals("false", elem.getAttribute("optional"));

    ArtifactMetaData amdOut = ArtifactTools.parseArtifactMetaData(tmp);
    assertEquals(amdOut, amd);

    Files.delete(tmp);
  }

  @Test
  public void parse() throws Exception {
    ArtifactMetaData amd = ArtifactTools.parseArtifactMetaData(Paths.get("src/java/test/org/savantbuild/dep/xml/amd.xml"));
    assertEquals(amd.dependencies.groups.size(), 2);
    assertEquals(amd.dependencies.groups.get("run").dependencies.size(), 2);
    assertEquals(amd.dependencies.groups.get("run").type, "run");
    assertEquals(amd.dependencies.groups.get("run").dependencies.get(0).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("run").dependencies.get(0).id.project, "test-project");
    assertEquals(amd.dependencies.groups.get("run").dependencies.get(0).id.name, "test-project");
    assertEquals(amd.dependencies.groups.get("run").dependencies.get(0).version, new Version("1.0.0"));
    assertEquals(amd.dependencies.groups.get("run").dependencies.get(0).id.type, "jar");

    assertEquals(amd.dependencies.groups.get("run").dependencies.get(1).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("run").dependencies.get(1).id.project, "test-project2");
    assertEquals(amd.dependencies.groups.get("run").dependencies.get(1).id.name, "test-project2");
    assertEquals(amd.dependencies.groups.get("run").dependencies.get(1).version, new Version("2.0.0"));
    assertEquals(amd.dependencies.groups.get("run").dependencies.get(1).id.type, "jar");

    assertEquals(amd.dependencies.groups.get("compile").dependencies.size(), 2);
    assertEquals(amd.dependencies.groups.get("compile").type, "compile");
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
}
