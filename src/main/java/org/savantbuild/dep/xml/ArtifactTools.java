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

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Objects;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.Version;
import org.savantbuild.dep.domain.VersionException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import static java.util.Arrays.asList;

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
      pw.printf("<artifact-meta-data license=\"%s\">\n", artifactMetaData.license);

      Dependencies dependencies = artifactMetaData.dependencies;
      if (dependencies != null) {
        pw.println("  <dependencies>");

        dependencies.groups.forEach((type, group) -> {
          if (!group.export) {
            return;
          }

          pw.printf("    <dependency-group type=\"%s\">\n", group.name);

          for (Artifact dependency : group.dependencies) {
            pw.printf("      <dependency group=\"%s\" project=\"%s\" name=\"%s\" version=\"%s\" type=\"%s\"/>\n",
                dependency.id.group, dependency.id.project, dependency.id.name, dependency.version, dependency.id.type);
          }
          pw.println("    </dependency-group>");
        });

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
   * @throws SAXException If the SAX parsing failed.
   * @throws VersionException If any of the version strings could not be parsed.
   * @throws ParserConfigurationException If the parser configuration in the JDK is invalid.
   * @throws IOException If the parse operation failed because of an IO error.
   */
  public static ArtifactMetaData parseArtifactMetaData(Path file)
      throws SAXException, VersionException, ParserConfigurationException, IOException {
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    ArtifactMetaDataHandler handler = new ArtifactMetaDataHandler();
    parser.parse(file.toFile(), handler);
    return new ArtifactMetaData(handler.dependencies, handler.license);
  }

  public static class ArtifactMetaDataHandler extends DefaultHandler {
    public Dependencies dependencies;

    public DependencyGroup group;

    public License license;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      switch (qName) {
        case "dependencies":
          dependencies = new Dependencies();
          break;
        case "dependency-group":
          try {
            String type = attributes.getValue("type");
            group = new DependencyGroup(type, true);
            dependencies.groups.put(type, group);
          } catch (NullPointerException e) {
            throw new NullPointerException("Invalid AMD file. The dependency-group elements must specify a [type] attribute");
          }

          break;
        case "dependency":
          try {
            Artifact dependency = new Artifact(new ArtifactID(attributes.getValue("group"), attributes.getValue("project"),
                attributes.getValue("name"), attributes.getValue("type")), new Version(attributes.getValue("version")));
            group.dependencies.add(dependency);
          } catch (NullPointerException e) {
            throw new NullPointerException("Invalid AMD file. The dependency element is missing a required attribute. The error message is [" + e.getMessage() + "]");
          }

          break;
        case "artifact-meta-data":
          String license = attributes.getValue("license");
          try {
            Objects.requireNonNull(license, "AMD files must contain a license attribute on the root element");
            this.license = License.valueOf(license);
          } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid AMD file. The license [" + license + "] is not an allowed license type. Allowable values are " + asList(License.values()), e);
          }

          break;
        default:
          throw new SAXException("Invalid element encountered in AMD file [" + qName + "].");
      }
    }
  }
}
