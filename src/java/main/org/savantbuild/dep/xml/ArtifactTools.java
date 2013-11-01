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

import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Dependency;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.Version;
import org.savantbuild.dep.domain.VersionException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * This class is a toolkit for handling artifact operations.
 *
 * @author Brian Pontarelli
 */
public class ArtifactTools {
  /**
   * Generates a temporary file that contains ArtifactMetaData XML which includes all of the artifacts in the
   * artifactMetaData given.
   *
   * @param artifactMetaData The MetaData object to serialize to XML.
   * @return The temp file and never null.
   * @throws IOException If the temp could not be created or the XML could not be written.
   */
  public static Path generateXML(ArtifactMetaData artifactMetaData) throws IOException {
    File tmp = File.createTempFile("savant", "amd");
    tmp.deleteOnExit();

    try (PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
      pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      pw.printf("<artifact-meta-data>\n");

      Dependencies dependencies = artifactMetaData.dependencies;
      if (dependencies != null) {
        pw.println("  <dependencies>");
        Map<String, DependencyGroup> groups = dependencies.groups;
        Set<String> keys = groups.keySet();
        for (String key : keys) {
          DependencyGroup group = groups.get(key);
          pw.printf("    <dependency-group type=\"%s\">\n", group.type);

          for (Dependency dependency : group.dependencies) {
            pw.printf("      <dependency group=\"%s\" project=\"%s\" name=\"%s\" version=\"%s\" type=\"%s\" optional=\"%b\"/>\n",
                dependency.id.group, dependency.id.project, dependency.id.name, dependency.version, dependency.id.type, dependency.optional);
          }
          pw.println("    </dependency-group>");
        }
        pw.println("  </dependencies>");
      }
      pw.println("</artifact-meta-data>");
    }

    return tmp.toPath();
  }

  /**
   * Parses the MetaData from the given Savant .amd file.
   *
   * @param file The File to read the XML MetaData information from.
   * @return The MetaData parsed.
   * @throws SAXException                 If the SAX parsing failed.
   * @throws VersionException             If any of the version strings could not be parsed.
   * @throws ParserConfigurationException If the parser configuration in the JDK is invalid.
   * @throws IOException                  If the parse operation failed because of an IO error.
   */
  public static ArtifactMetaData parseArtifactMetaData(Path file)
      throws SAXException, VersionException, ParserConfigurationException, IOException {
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    ArtifactMetaDataHandler handler = new ArtifactMetaDataHandler();
    parser.parse(file.toFile(), handler);
    return new ArtifactMetaData(handler.dependencies);
  }

  public static class ArtifactMetaDataHandler extends DefaultHandler {
    private Dependencies dependencies;

    private DependencyGroup group;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      switch (qName) {
        case "dependencies":
          dependencies = new Dependencies();
          break;
        case "dependency-group":
          String type = attributes.getValue("type");
          group = new DependencyGroup(type);
          dependencies.groups.put(type, group);
          break;
        case "dependency":
          Dependency dependency = new Dependency(new ArtifactID(attributes.getValue("group"), attributes.getValue("project"),
              attributes.getValue("name"), attributes.getValue("type")), new Version(attributes.getValue("version")),
              Boolean.parseBoolean(attributes.getValue("optional")));

          group.dependencies.add(dependency);
          break;
        case "artifact-meta-data":
          // Do nothing, this is the root element
          break;
        default:
          throw new SAXException("Invalid element encountered in AMD file [" + qName + "].");
      }
    }
  }
}
