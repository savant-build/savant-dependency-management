/*
 * Copyright (c) 2022, Inversoft Inc., All Rights Reserved
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
package org.savantbuild.dep.maven;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.savantbuild.dep.LicenseException;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.ReifiedArtifact;
import org.savantbuild.domain.Version;
import org.savantbuild.domain.VersionException;
import org.savantbuild.output.Output;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Maven helpers for things like parsing POMs.
 *
 * @author Brian Pontarelli
 */
public class MavenTools {
  /**
   * Parses a POM XML file.
   *
   * @param file   The file.
   * @param output The output in case the POM is borked.
   * @return The POM object and never null;
   * @throws POMException If the parsing failed.
   */
  public static POM parsePOM(Path file, Output output) throws POMException {
    POM pom = new POM();

    try {
      removeInvalidCharactersInPom(file, output);

      DocumentBuilder b = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document d = b.parse(file.toFile());
      Element pomElement = d.getDocumentElement();
      pom.version = childText(pomElement, "version");
      if (pom.version != null) {
        pom.properties.put("project.version", pom.version);
        // 'pom' and no prefix are deprecated in favor of 'project' but they still exist in the wild.
        pom.properties.put("pom.version", pom.properties.get("project.version"));
        pom.properties.put("version", pom.properties.get("project.version"));
      }

      pom.group = childText(pomElement, "groupId");
      if (pom.group != null) {
        pom.properties.put("project.groupId", pom.group);
        // 'pom' and no prefix are deprecated in favor of 'project' but they still exist in the wild.
        pom.properties.put("pom.groupId", pom.group);
        pom.properties.put("groupId", pom.group);
      }

      pom.id = childText(pomElement, "artifactId");
      if (pom.id != null) {
        pom.properties.put("project.artifactId", pom.id);
        // 'pom' and no prefix are deprecated in favor of 'project' but they still exist in the wild.
        pom.properties.put("pom.artifactId", pom.id);
        pom.properties.put("artifactId", pom.id);
      }

      pom.name = childText(pomElement, "name");
      if (pom.name != null) {
        pom.properties.put("project.name", pom.name);
      }

      pom.packaging = childText(pomElement, "packaging");
      if (pom.packaging != null) {
        pom.properties.put("project.packaging", pom.packaging);
      }

      // Grab the parent info
      Element parentNode = firstChild(pomElement, "parent");
      if (parentNode != null) {
        pom.parentGroup = childText(parentNode, "groupId");
        pom.parentId = childText(parentNode, "artifactId");
        pom.parentVersion = childText(parentNode, "version");
      }

      // Grab the properties
      Element propertiesNode = firstChild(pomElement, "properties");
      if (propertiesNode != null) {
        NodeList propertyList = propertiesNode.getChildNodes();
        for (int i = 0; i < propertyList.getLength(); i++) {
          String name = propertyList.item(i).getNodeName();
          String value = propertyList.item(i).getTextContent().trim();
          pom.properties.put(name, value);
        }
      }

      // Grab the dependencies (top-level)
      Element dependenciesNode = firstChild(pomElement, "dependencies");
      if (dependenciesNode != null) {
        NodeList dependencyList = dependenciesNode.getChildNodes();
        for (int i = 0; i < dependencyList.getLength(); i++) {
          if (dependencyList.item(i).getNodeType() != Node.ELEMENT_NODE) {
            continue;
          }

          pom.dependencies.add(parseDependency((Element) dependencyList.item(i)));
        }
      }

      // Grab the dependencyManagement info (top-level)
      Element depMgntNode = firstChild(pomElement, "dependencyManagement");
      if (depMgntNode != null) {
        Element depMgntDepsNode = firstChild(depMgntNode, "dependencies");
        if (depMgntDepsNode != null) {
          NodeList dependencyList = depMgntDepsNode.getChildNodes();
          for (int i = 0; i < dependencyList.getLength(); i++) {
            if (dependencyList.item(i).getNodeType() != Node.ELEMENT_NODE) {
              continue;
            }

            pom.dependenciesDefinitions.add(parseDependency((Element) dependencyList.item(i)));
          }
        }
      }

      // Grab the licenses
      Element licensesNode = firstChild(pomElement, "licenses");
      if (licensesNode != null) {
        NodeList licenseList = licensesNode.getChildNodes();
        for (int i = 0; i < licenseList.getLength(); i++) {
          if (licenseList.item(i).getNodeType() != Node.ELEMENT_NODE) {
            continue;
          }

          pom.licenses.add(parseLicense((Element) licenseList.item(i)));
        }
      }

      return pom;
    } catch (Exception e) {
      writeOutBadPom(file, output);
      throw new POMException("Unable to parse a Maven POM.", e);
    }
  }

  public static String replaceProperties(String value, Map<String, String> properties) {
    if (value == null) {
      return value;
    }

    for (String key : properties.keySet()) {
      value = value.replace("${" + key + "}", properties.get(key));
    }

    return value;
  }

  public static Artifact toArtifact(POM pom, String type, Map<String, Version> mappings) {
    Version version = determineVersion(pom, mappings);
    ArtifactID id = new ArtifactID(pom.group, pom.id, pom.id, type);
    return new Artifact(id, version, pom.version, null);
  }

  public static Dependencies toSavantDependencies(POM pom, Map<String, Version> mappings) {
    Dependencies savantDependencies = new Dependencies();
    pom.resolveAllDependencies().forEach(dep -> {
      String groupName = dep.scope;
      if (groupName.equalsIgnoreCase("test")) {
        groupName = "test-compile";
      }
      if (dep.optional != null && dep.optional.equalsIgnoreCase("true")) {
        groupName += "-optional";
      }

      DependencyGroup savantDependencyGroup = savantDependencies.groups.get(groupName);
      if (savantDependencyGroup == null) {
        savantDependencyGroup = new DependencyGroup(groupName, true);
        savantDependencies.groups.put(groupName, savantDependencyGroup);
      }

      List<ArtifactID> exclusions = new ArrayList<>();
      if (dep.exclusions.size() > 0) {
        for (MavenExclusion exclusion : dep.exclusions) {
          exclusions.add(new ArtifactID(exclusion.group, exclusion.id, exclusion.id, "*"));
        }
      }

      Version mapping = determineVersion(dep, mappings);
      dep.savantArtifact = new ReifiedArtifact(new ArtifactID(dep.group, dep.id, dep.getArtifactName(), (dep.type == null ? "jar" : dep.type)), mapping, exclusions, Collections.emptyList());
      savantDependencyGroup.dependencies.add(new Artifact(dep.savantArtifact.id, dep.savantArtifact.version, exclusions));
    });

    return savantDependencies;
  }

  public static List<License> toSavantLicenses(POM pom) {
    List<License> licenses = new ArrayList<>();
    for (MavenLicense license : pom.licenses) {
      // Lookup by SPDX id
      License savantLicense;
      try {
        savantLicense = License.parse(license.name, null);
      } catch (LicenseException e) {
        // Try the URL now
        savantLicense = License.lookupByURL(license.url);
      }

      if (savantLicense != null) {
        licenses.add(savantLicense);
      }
    }

    return licenses;
  }

  private static String childText(Element root, String childName) {
    Element child = firstChild(root, childName);
    if (child != null) {
      return child.getTextContent();
    }

    return null;
  }

  private static Version determineVersion(POM pom, Map<String, Version> mappings) {
    Version version;
    try {
      version = new Version(pom.version);
    } catch (VersionException e) {
      // Look up the mapping
      String key = pom.toSpecification();
      version = mappings.get(key);
      if (version == null) {
        throw new VersionException("Invalid Version in the dependency graph from a Maven dependency [" + pom.toSpecification() + "]. You must specify a semantic version mapping for Savant to properly handle Maven dependencies.");
      }
    }

    return version;
  }

  private static Element firstChild(Element root, String childName) {
    NodeList childList = root.getChildNodes();
    for (int i = 0; i < childList.getLength(); i++) {
      Node childNode = childList.item(i);
      if (childNode.getNodeType() == Node.ELEMENT_NODE && childNode.getNodeName().equals(childName)) {
        return (Element) childNode;
      }
    }

    return null;
  }

  private static MavenDependency parseDependency(Element dependencyNode) {
    MavenDependency artifact = new MavenDependency();
    artifact.group = childText(dependencyNode, "groupId");
    artifact.id = childText(dependencyNode, "artifactId");
    artifact.version = childText(dependencyNode, "version");
    artifact.classifier = childText(dependencyNode, "classifier");
    artifact.type = childText(dependencyNode, "type");
    artifact.optional = childText(dependencyNode, "optional");
    artifact.scope = childText(dependencyNode, "scope");

    Element exclusions = firstChild(dependencyNode, "exclusions");
    if (exclusions != null) {
      NodeList exclusionList = exclusions.getElementsByTagName("exclusion");
      for (int i = 0; i < exclusionList.getLength(); i++) {
        if (exclusionList.item(i).getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }

        Element exclusion = (Element) exclusionList.item(i);
        String group = childText(exclusion, "groupId");
        String id = childText(exclusion, "artifactId");
        artifact.exclusions.add(new MavenExclusion(group, id));
      }
    }

    return artifact;
  }

  private static MavenLicense parseLicense(Element licenseNode) {
    MavenLicense license = new MavenLicense();
    license.distribution = childText(licenseNode, "distribution");
    license.name = childText(licenseNode, "name");
    license.url = childText(licenseNode, "url");
    return license;
  }

  private static void removeInvalidCharactersInPom(Path file, Output output) {
    try {
      String pomString = new String(Files.readAllBytes(file), UTF_8);
      if (pomString.contains("&oslash;")) {
        output.warning("Found and replaced [&oslash;] with [O] to keep the parser from exploding.");
        Files.write(file, pomString.replace("&oslash;", "O").getBytes(UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void writeOutBadPom(Path file, Output output) {
    output.error("Bad POM, failed to parse. I copied it to /tmp/invalid_pom if you want to take a look and see what is fookered.");

    try {
      Files.copy(file, Paths.get("/tmp/invalid_pom"), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException ignore) {
    }
  }
}
