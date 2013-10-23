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
import java.io.File;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.Dependencies;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.testng.Assert.*;

/**
 * <p>
 * This class tests the artifact toolkit.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class ArtifactToolsTest {
  /**
   * Tests that the XML generation works correctly.
   */
  @Test
  public void xml() throws Exception {
    Artifact a1 = new Artifact("group_name", "project_name", "name", "version", "type");
    Artifact a2 = new Artifact("group_name2", "project_name2", "name2", "version2", "type2");
    Artifact a3 = new Artifact("group_name3", "project_name3", "name3", "version3", "type3");

    DependencyGroup group = new DependencyGroup("compile");
    group.getArtifacts().add(a1);
    group.getArtifacts().add(a2);

    DependencyGroup group1 = new DependencyGroup("run");
    group1.getArtifacts().add(a3);

    Dependencies deps = new Dependencies(null);
    deps.getArtifactGroups().put("compile", group);
    deps.getArtifactGroups().put("run", group1);

    ArtifactMetaData amd = new ArtifactMetaData(deps, null);

    File tmp = ArtifactTools.generateXML(amd);
    assertNotNull(tmp);
    assertTrue(tmp.exists());
    assertTrue(tmp.isFile());

    DocumentBuilder b = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document d = b.parse(tmp);
    Element root = d.getDocumentElement();
    assertEquals(2, root.getElementsByTagName("artifact-group").getLength());

    Element groupElem = (Element) root.getElementsByTagName("artifact-group").item(0);
    assertEquals(2, groupElem.getElementsByTagName("artifact").getLength());

    Element elem = (Element) groupElem.getElementsByTagName("artifact").item(0);
    assertEquals("group_name", elem.getAttribute("group"));
    assertEquals("project_name", elem.getAttribute("project"));
    assertEquals("name", elem.getAttribute("name"));
    assertEquals("version", elem.getAttribute("version"));
    assertEquals("type", elem.getAttribute("type"));
    elem = (Element) groupElem.getElementsByTagName("artifact").item(1);
    assertEquals("group_name2", elem.getAttribute("group"));
    assertEquals("project_name2", elem.getAttribute("project"));
    assertEquals("name2", elem.getAttribute("name"));
    assertEquals("version2", elem.getAttribute("version"));
    assertEquals("type2", elem.getAttribute("type"));

    groupElem = (Element) root.getElementsByTagName("artifact-group").item(1);
    assertEquals(1, groupElem.getElementsByTagName("artifact").getLength());

    elem = (Element) groupElem.getElementsByTagName("artifact").item(0);
    assertEquals("group_name3", elem.getAttribute("group"));
    assertEquals("project_name3", elem.getAttribute("project"));
    assertEquals("name3", elem.getAttribute("name"));
    assertEquals("version3", elem.getAttribute("version"));
    assertEquals("type3", elem.getAttribute("type"));

    ArtifactMetaData amdOut = ArtifactTools.parseArtifactMetaData(tmp);

    assertEquals(amdOut.getDependencies().getAllArtifacts(), deps.getAllArtifacts());
    assertEquals(amdOut.getDependencies().getArtifactGroups(), deps.getArtifactGroups());

    tmp.delete();
  }

  @DataProvider(name = "parseData")
  public Object[][] parseData() {
    return new Object[][]{
      {"src/java/test/unit/org/savantbuild/dep/xml/amd-old-format.xml", "minor"},
      {"src/java/test/unit/org/savantbuild/dep/xml/amd-new-format.xml", "minor"}
    };
  }

  @Test(dataProvider = "parseData")
  public void parse(String file, String compat) {
    ArtifactMetaData amd = ArtifactTools.parseArtifactMetaData(new File(file));
    assertEquals(amd.getCompatibility(), compat);
    assertEquals(amd.getDependencies().getArtifactGroups().size(), 2);
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().size(), 2);
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getType(), "run");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(0).getGroup(), "org.example.test");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(0).getProject(), "test-project");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(0).getName(), "test-project");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(0).getVersion(), "1.0");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(0).getType(), "jar");

    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(1).getGroup(), "org.example.test");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(1).getProject(), "test-project2");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(1).getName(), "test-project2");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(1).getVersion(), "2.0");
    assertEquals(amd.getDependencies().getArtifactGroups().get("run").getArtifacts().get(1).getType(), "jar");

    assertEquals(amd.getDependencies().getArtifactGroups().get("compile").getArtifacts().size(), 2);
    assertEquals(amd.getDependencies().getArtifactGroups().get("compile").getType(), "compile");
    assertEquals(amd.getDependencies().getArtifactGroups().get("compile").getArtifacts().get(0).getGroup(), "org.example.test");
    assertEquals(amd.getDependencies().getArtifactGroups().get("compile").getArtifacts().get(0).getProject(), "test-project3");
    assertEquals(amd.getDependencies().getArtifactGroups().get("compile").getArtifacts().get(0).getName(), "test-project3");
    assertEquals(amd.getDependencies().getArtifactGroups().get("compile").getArtifacts().get(0).getVersion(), "3.0");
    assertEquals(amd.getDependencies().getArtifactGroups().get("compile").getArtifacts().get(0).getType(), "jar");

    assertEquals(amd.getDependencies().getArtifactGroups().get("compile").getArtifacts().get(1).getGroup(), "org.example.test");
    assertEquals(amd.getDependencies().getArtifactGroups().get("compile").getArtifacts().get(1).getProject(), "test-project4");
    assertEquals(amd.getDependencies().getArtifactGroups().get("compile").getArtifacts().get(1).getName(), "test-project4");
    assertEquals(amd.getDependencies().getArtifactGroups().get("compile").getArtifacts().get(1).getVersion(), "4.0");
    assertEquals(amd.getDependencies().getArtifactGroups().get("compile").getArtifacts().get(1).getType(), "jar");
  }
}
