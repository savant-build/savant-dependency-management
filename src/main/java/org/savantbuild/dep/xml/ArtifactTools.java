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
import java.util.HashMap;
import java.util.Map;

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
      pw.printf("<artifact-meta-data>\n");

      artifactMetaData.licenses.forEach((license, text) -> {
        if (text != null) {
          pw.printf("  <license type=\"%s\">\n<![CDATA[%s]]>\n  </license>\n", license, text);
        } else {
          pw.printf("  <license type=\"%s\"/>\n", license);
        }
      });

      Dependencies dependencies = artifactMetaData.dependencies;
      if (dependencies != null) {
        pw.println("  <dependencies>");

        dependencies.groups.forEach((type, group) -> {
          if (!group.export) {
            return;
          }

          pw.printf("    <dependency-group name=\"%s\">\n", group.name);

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
    return new ArtifactMetaData(handler.dependencies, handler.licenses);
  }

  public static class ArtifactMetaDataHandler extends DefaultHandler {
    public final Map<License, String> licenses = new HashMap<>();

    public License currentLicense;

    public Dependencies dependencies;

    public DependencyGroup group;

    public StringBuilder licenseText;

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
      if (licenseText != null) {
        licenseText.append(ch, start, length);
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
      if (qName.equals("license")) {
        String text = licenseText.toString().trim();
        if (text.length() > 0) {
          licenses.put(currentLicense, text);
        } else {
          licenses.put(currentLicense, null);
        }

        currentLicense = null;
        licenseText = null;
      }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      switch (qName) {
        case "dependencies":
          dependencies = new Dependencies();
          break;
        case "dependency-group":
          String name = attributes.getValue("name");
          if (name == null) {
            throw new SAXException("Invalid AMD file. The dependency-group elements must specify a [name] attribute");
          }

          group = new DependencyGroup(name, true);
          dependencies.groups.put(name, group);

          break;
        case "dependency":
          String dependencyGroup = attributes.getValue("group");
          if (dependencyGroup == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [group] attribute");
          }
          String dependencyProject = attributes.getValue("project");
          if (dependencyProject == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [project] attribute");
          }
          String dependencyName = attributes.getValue("name");
          if (dependencyName == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [name] attribute");
          }
          String dependencyType = attributes.getValue("type");
          if (dependencyType == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [type] attribute");
          }
          String dependencyVersion = attributes.getValue("version");
          if (dependencyVersion == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [version] attribute");
          }

          if (group == null) {
            throw new SAXException("Invalid AMD file. A dependency doesn't appear to be inside a dependency-group element");
          }

          Artifact dependency = new Artifact(new ArtifactID(dependencyGroup, dependencyProject, dependencyName, dependencyType), new Version(dependencyVersion), false);
          group.dependencies.add(dependency);

          break;
        case "license":
          String type = attributes.getValue("type");
          if (type == null) {
            throw new SAXException("Invalid AMD file. The license elements must contain a [type] attribute");
          }

          try {
            this.currentLicense = License.valueOf(type);
          } catch (IllegalArgumentException e) {
            throw new SAXException("Invalid AMD file. The license [" + type + "] is not an allowed license type. Allowable values are " + asList(License.values()), e);
          }

          licenseText = new StringBuilder();

          break;
        case "artifact-meta-data":
          if (attributes.getValue("license") != null) {
            throw new IllegalArgumentException("Invalid AMD file. It contains the old license definition on the <artifact-meta-data> element");
          }

          break;
        default:
          throw new SAXException("Invalid element encountered in AMD file [" + qName + "].");
      }
    }
  }
}
