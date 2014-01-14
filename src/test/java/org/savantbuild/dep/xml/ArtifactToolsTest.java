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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import org.savantbuild.dep.BaseUnitTest;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Dependency;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.Version;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * This class tests the artifact toolkit.
 *
 * @author Brian Pontarelli
 */
public class ArtifactToolsTest extends BaseUnitTest {
  /**
   * Tests that the XML generation works correctly.
   */
  @Test
  public void xml() throws Exception {
    Dependency d1 = new Dependency("group_name:project_name:name:1.0.0:type", false);
    Dependency d2 = new Dependency("group_name2:project_name2:name2:2.0.0:type2", true);
    Dependency d3 = new Dependency("group_name3:project_name3:name3:3.0.0:type3", false);
    Dependency d4 = new Dependency("group_name4:project_name4:name4:4.0.0:type4", false);

    DependencyGroup group = new DependencyGroup("compile", true);
    group.dependencies.add(d1);
    group.dependencies.add(d2);

    DependencyGroup group1 = new DependencyGroup("run", true);
    group1.dependencies.add(d3);

    // Not exported
    DependencyGroup group2 = new DependencyGroup("test", false);
    group2.dependencies.add(d4);

    Dependencies deps = new Dependencies();
    deps.groups.put("compile", group);
    deps.groups.put("run", group1);
    deps.groups.put("test", group2);

    ArtifactMetaData amd = new ArtifactMetaData(deps, License.Apachev2);

    Path tmp = ArtifactTools.generateXML(amd);
    assertNotNull(tmp);
    assertTrue(Files.isRegularFile(tmp));

    DocumentBuilder b = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document d = b.parse(tmp.toFile());
    Element root = d.getDocumentElement();
    assertEquals(root.getElementsByTagName("dependency-group").getLength(), 2);

    Element groupElem = (Element) root.getElementsByTagName("dependency-group").item(0);
    assertEquals(groupElem.getElementsByTagName("dependency").getLength(), 2);

    Element elem = (Element) groupElem.getElementsByTagName("dependency").item(0);
    assertEquals(elem.getAttribute("group"), "group_name");
    assertEquals(elem.getAttribute("project"), "project_name");
    assertEquals(elem.getAttribute("name"), "name");
    assertEquals(elem.getAttribute("version"), "1.0.0");
    assertEquals(elem.getAttribute("type"), "type");
    assertEquals(elem.getAttribute("optional"), "false");

    elem = (Element) groupElem.getElementsByTagName("dependency").item(1);
    assertEquals(elem.getAttribute("group"), "group_name2");
    assertEquals(elem.getAttribute("project"), "project_name2");
    assertEquals(elem.getAttribute("name"), "name2");
    assertEquals(elem.getAttribute("version"), "2.0.0");
    assertEquals(elem.getAttribute("type"), "type2");
    assertEquals(elem.getAttribute("optional"), "true");

    groupElem = (Element) root.getElementsByTagName("dependency-group").item(1);
    assertEquals(groupElem.getElementsByTagName("dependency").getLength(), 1);

    elem = (Element) groupElem.getElementsByTagName("dependency").item(0);
    assertEquals(elem.getAttribute("group"), "group_name3");
    assertEquals(elem.getAttribute("project"), "project_name3");
    assertEquals(elem.getAttribute("name"), "name3");
    assertEquals(elem.getAttribute("version"), "3.0.0");
    assertEquals(elem.getAttribute("type"), "type3");
    assertEquals(elem.getAttribute("optional"), "false");

    // Remove the non-exported group
    deps.groups.remove("test");

    // Then load and compare
    ArtifactMetaData amdOut = ArtifactTools.parseArtifactMetaData(tmp);
    assertEquals(amd, amdOut);

    Files.delete(tmp);
  }

  @Test
  public void parse() throws Exception {
    ArtifactMetaData amd = ArtifactTools.parseArtifactMetaData(projectDir.resolve("src/test/java/org/savantbuild/dep/xml/amd.xml"));
    assertEquals(amd.license, License.Apachev2);
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
