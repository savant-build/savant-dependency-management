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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.maven.MavenTools;
import org.savantbuild.domain.Version;
import org.savantbuild.domain.VersionException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class is a toolkit for handling artifact operations.
 *
 * @author Brian Pontarelli
 */
public class ArtifactTools {
  /**
   * Generates a temporary file that contains ArtifactMetaData XML which includes all the artifacts in the
   * ArtifactMetaData given.
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

      artifactMetaData.licenses.forEach(license -> {
        if (license.text != null && license.customText) {
          print(pw, "  <license type=\"%s\"><![CDATA[%s]]></license>\n", license.identifier, license.text);
        } else {
          print(pw, "  <license type=\"%s\"/>\n", license.identifier);
        }
      });

      Dependencies dependencies = artifactMetaData.dependencies;
      if (dependencies != null) {
        pw.println("  <dependencies>");

        dependencies.groups.forEach((type, group) -> {
          if (!group.export) {
            return;
          }

          print(pw, "    <dependency-group name=\"%s\">\n", group.name);

          for (Artifact dependency : group.dependencies) {
            ArtifactID id = dependency.id;
            String version = dependency.nonSemanticVersion != null ? dependency.nonSemanticVersion : dependency.version.toString();
            print(pw, "      <dependency group=\"%s\" project=\"%s\" name=\"%s\" version=\"%s\" type=\"%s\">\n", id.group, id.project, id.name, version, id.type);

            if (dependency.exclusions.size() > 0) {
              for (ArtifactID exclusion : dependency.exclusions) {
                print(pw, "        <exclusion group=\"%s\" project=\"%s\" name=\"%s\" type=\"%s\"/>\n", exclusion.group, exclusion.project, exclusion.name, exclusion.type);
              }
            }

            pw.printf("      </dependency>\n");
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
   * @param file     The File to read the XML MetaData information from.
   * @param mappings The semantic version mappings that are used when the AMD is parsed and uses non-semantic versions
   *                 for transitive dependencies.
   * @return The MetaData parsed.
   * @throws SAXException If the SAX parsing failed.
   * @throws VersionException If any of the version strings could not be parsed.
   * @throws ParserConfigurationException If the parser configuration in the JDK is invalid.
   * @throws IOException If the parse operation failed because of an IO error.
   */
  public static ArtifactMetaData parseArtifactMetaData(Path file, Map<String, Version> mappings) throws SAXException, VersionException, ParserConfigurationException, IOException {
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    ArtifactMetaDataHandler handler = new ArtifactMetaDataHandler(mappings);
    parser.parse(file.toFile(), handler);
    return new ArtifactMetaData(handler.dependencies, handler.licenses);
  }

  private static void print(PrintWriter writer, String message, Object... args) {
    for (int i = 0; i < args.length; i++) {
      args[i] = args[i].toString().replace("\"", "&quot;");
    }

    writer.printf(message, args);
  }

  public static class ArtifactMetaDataHandler extends DefaultHandler {
    public final List<ArtifactID> exclusions = new ArrayList<>();

    public final List<License> licenses = new ArrayList<>();

    private final Map<String, Version> mappings;

    public Dependencies dependencies;

    public ArtifactID dependencyId;

    public String dependencyNonSemanticVersion;

    public Version dependencyVersion;

    public DependencyGroup group;

    public String licenseId;

    public StringBuilder licenseText;

    public ArtifactMetaDataHandler(Map<String, Version> mappings) {
      this.mappings = mappings;
    }

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
        if (text.trim().isEmpty()) {
          text = null;
        }

        try {
          License license = License.parse(licenseId, text);
          licenses.add(license);
        } catch (IllegalArgumentException e) {
          throw new SAXException("Invalid AMD file. The license [" + licenseId + "] is not an allowed license type. Allowable values are " + License.Licenses.keySet() + " plus the custom license types of [Commercial, Other, OtherDistributableOpenSource, OtherNonDistributableOpenSource]", e);
        }

        licenseId = null;
        licenseText = null;
      } else if (qName.equals("dependency")) {
        group.dependencies.add(new Artifact(dependencyId, dependencyVersion, dependencyNonSemanticVersion, exclusions));
        dependencyId = null;
        dependencyVersion = null;
        exclusions.clear();
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
          String group = attributes.getValue("group");
          if (group == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [group] attribute");
          }

          String project = attributes.getValue("project");
          if (project == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [project] attribute");
          }

          name = attributes.getValue("name");
          if (name == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [name] attribute");
          }

          String type = attributes.getValue("type");
          if (type == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [type] attribute");
          }

          String version = attributes.getValue("version");
          if (version == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [version] attribute");
          }

          if (this.group == null) {
            throw new SAXException("Invalid AMD file. A dependency doesn't appear to be inside a dependency-group element");
          }

          dependencyId = new ArtifactID(group, project, name, type);
          try {
            dependencyVersion = new Version(version);
            dependencyNonSemanticVersion = version; // This is a fallback just for Maven
          } catch (VersionException e) {
            dependencyNonSemanticVersion = version;

            String spec = group + ":" + project + ":" + name + ":" + version;
            dependencyVersion = mappings.get(spec);
            if (dependencyVersion == null) {
              spec = group + ":" + project + ":" + version;
              dependencyVersion = mappings.get(spec);
            }
            if (dependencyVersion == null) {
              throw new VersionException(String.format(MavenTools.VersionError, spec));
            }
          }

          break;
        case "exclusion":
          group = attributes.getValue("group");
          if (group == null) {
            throw new SAXException("Invalid AMD file. The exclusion elements must specify a [group] attribute");
          }
          project = attributes.getValue("project");
          if (project == null) {
            throw new SAXException("Invalid AMD file. The exclusion elements must specify a [project] attribute");
          }
          name = attributes.getValue("name");
          if (name == null) {
            throw new SAXException("Invalid AMD file. The exclusion elements must specify a [name] attribute");
          }
          type = attributes.getValue("type");
          if (type == null) {
            throw new SAXException("Invalid AMD file. The exclusion elements must specify a [type] attribute");
          }

          if (dependencyId == null || dependencyVersion == null) {
            throw new SAXException("Invalid AMD file. An exclusion doesn't appear to be inside a dependency element");
          }

          exclusions.add(new ArtifactID(group, project, name, type));
          break;
        case "license":
          licenseId = attributes.getValue("type");
          if (licenseId == null) {
            throw new SAXException("Invalid AMD file. The license elements must contain a [type] attribute");
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
