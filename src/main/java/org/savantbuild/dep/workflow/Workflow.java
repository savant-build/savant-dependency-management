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
package org.savantbuild.dep.workflow;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.ResolvableItem;
import org.savantbuild.dep.maven.MavenDependency;
import org.savantbuild.dep.maven.MavenTools;
import org.savantbuild.dep.maven.POM;
import org.savantbuild.dep.workflow.process.NegativeCacheException;
import org.savantbuild.dep.workflow.process.ProcessFailureException;
import org.savantbuild.dep.xml.ArtifactTools;
import org.savantbuild.domain.Version;
import org.savantbuild.domain.VersionException;
import org.savantbuild.output.Output;
import org.savantbuild.security.MD5;
import org.savantbuild.security.MD5Exception;
import org.xml.sax.SAXException;

/**
 * This class models a grouping of a fetch and publish workflow.
 *
 * @author Brian Pontarelli
 */
public class Workflow {
  public final FetchWorkflow fetchWorkflow;

  public final Map<String, Version> mappings = new HashMap<>();

  public final Output output;

  public final PublishWorkflow publishWorkflow;

  public Workflow(FetchWorkflow fetchWorkflow, PublishWorkflow publishWorkflow, Output output) {
    this.fetchWorkflow = fetchWorkflow;
    this.publishWorkflow = publishWorkflow;
    this.output = output;
  }

  /**
   * Fetches the artifact itself. Every artifact in a Savant dependency graph is required to exist. Therefore, Savant
   * never negative caches artifact files and this method will always return the artifact file or throw an
   * ArtifactMissingException.
   *
   * @param artifact The artifact to fetch.
   * @return The Path of the artifact and never null.
   * @throws ArtifactMissingException If the artifact could not be found.
   * @throws ProcessFailureException If any of the processes encountered a failure while attempting to fetch the
   *     artifact.
   * @throws MD5Exception If the item's MD5 file did not match the item.
   */
  public Path fetchArtifact(Artifact artifact) throws ArtifactMissingException, ProcessFailureException, MD5Exception {
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path file = fetchWorkflow.fetchItem(item, publishWorkflow);
    if (file == null && artifact.nonSemanticVersion != null) {
      // Try the bad version
      item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.nonSemanticVersion, artifact.getArtifactNonSemanticFile());
      file = fetchWorkflow.fetchItem(item, publishWorkflow);
    }

    if (file == null) {
      throw new ArtifactMissingException(artifact);
    }

    return file;
  }

  /**
   * Fetches the artifact metadata. Every artifact in Savant is required to have an AMD file. Otherwise, it is
   * considered a missing artifact entirely. Therefore, Savant never negative caches AMD files and this method will
   * always return an AMD file or throw an ArtifactMetaDataMissingException.
   *
   * @param artifact The artifact to fetch the metadata for.
   * @return The ArtifactMetaData object and never null.
   * @throws ArtifactMetaDataMissingException If the AMD file could not be found.
   * @throws ProcessFailureException If any of the processes encountered a failure while attempting to fetch the AMD
   *     file.
   * @throws MD5Exception If the item's MD5 file did not match the item.
   */
  public ArtifactMetaData fetchMetaData(Artifact artifact) throws ArtifactMetaDataMissingException, ProcessFailureException, MD5Exception {
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactMetaDataFile());
    try {
      Path file = fetchWorkflow.fetchItem(item, publishWorkflow);
      if (file == null) {
        POM pom = loadPOM(artifact);
        if (pom != null) {
          ArtifactMetaData amd = translatePOM(pom);
          Path amdFile = ArtifactTools.generateXML(amd);
          file = publishWorkflow.publish(item, amdFile);

          // Write the MD5
          MD5 amdMD5 = MD5.forPath(amdFile);
          Path amdMD5File = Files.createTempFile("savant", "amd-md5");
          MD5.writeMD5(amdMD5, amdMD5File);
          ResolvableItem md5Item = new ResolvableItem(item, item.item + ".md5");
          publishWorkflow.publish(md5Item, amdMD5File);
          Files.delete(amdMD5File);
        }
      }

      if (file == null) {
        throw new ArtifactMetaDataMissingException(artifact);
      }

      return ArtifactTools.parseArtifactMetaData(file, mappings);
    } catch (IllegalArgumentException | NullPointerException | SAXException | ParserConfigurationException |
             IOException | VersionException e) {
      throw new ProcessFailureException(item, e);
    }
  }

  /**
   * Fetches the source of the artifact. If a source file is missing, this method stores a negative file in the cache so
   * that an attempt to download the source file isn't made each time. This is required so that offline work can be done
   * by only hitting the local cache of dependencies.
   *
   * @param artifact The artifact to fetch the source for.
   * @return The Path of the source or null if it doesn't exist.
   * @throws ProcessFailureException If any of the processes encountered a failure while attempting to fetch the
   *     source file.
   * @throws MD5Exception If the item's MD5 file did not match the item.
   */
  public Path fetchSource(Artifact artifact) throws ProcessFailureException, MD5Exception {
    try {
      ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactSourceFile());
      Path file = fetchWorkflow.fetchItem(item, publishWorkflow);
      if (file == null) {
        item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactAlternativeSourceFile());
        file = fetchWorkflow.fetchItem(item, publishWorkflow);

        // Publish the Savant named source JAR to prevent going back out to remote repositories next time we want to load source JARs
        if (file != null) {
          item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactSourceFile());
          publishWorkflow.publish(item, file);
        }
      }

      if (file == null && artifact.nonSemanticVersion != null) {
        item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.nonSemanticVersion, artifact.getArtifactNonSemanticAlternativeSourceFile());
        file = fetchWorkflow.fetchItem(item, publishWorkflow);

        // Publish the Savant named source JAR to prevent going back out to remote repositories next time we want to load source JARs
        if (file != null) {
          item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactSourceFile());
          publishWorkflow.publish(item, file);
        }
      }

      if (file == null) {
        item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactSourceFile());
        publishWorkflow.publishNegative(item);
      }

      return file;
    } catch (NegativeCacheException e) {
      // This is a short-circuit exit from the workflow. It is only thrown by the CacheProcess and indicates that the
      // search for the source JAR should stop immediately.
      return null;
    }
  }

  private POM loadPOM(Artifact artifact) {
    // Maven doesn't use artifact names (via classifiers) when resolving POMs. Therefore, we need to use the project id twice for the item
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.project, artifact.version.toString(), artifact.getArtifactPOMFile());
    Path file = fetchWorkflow.fetchItem(item, publishWorkflow);

    // Try the POM with the non-semantic (bad) version
    if (file == null && artifact.nonSemanticVersion != null) {
      output.debugln("[Looking for POM using non-semantic version]");
      item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.project, artifact.nonSemanticVersion, artifact.getArtifactNonSemanticPOMFile());
      file = fetchWorkflow.fetchItem(item, publishWorkflow);
    }

    if (file == null) {
      return null;
    }

    POM pom = MavenTools.parsePOM(file, output);
    pom.replaceKnownVariablesAndFillInDependencies();

    // Recursively load the parent POM and any dependencies that are `import`
    if (pom.parentGroup != null && pom.parentId != null && pom.parentVersion != null) {
      POM parent = new POM(pom.parentGroup, pom.parentId, pom.parentVersion);
      Artifact parentPOM = MavenTools.toArtifact(parent, "pom", mappings);
      pom.parent = loadPOM(parentPOM);
    }

    // Now that we have the parents (recursively), load all the variables and fix top-level POM definitions
    pom.replaceKnownVariablesAndFillInDependencies();
    if (pom.group == null) {
      pom.group = pom.parent.group;
    }
    if (pom.version == null) {
      pom.version = pom.parent.version;
    }

    // Load the imports in the POM until there are no more imports. Each iteration needs to fill in variables
    List<MavenDependency> imports = pom.imports();
    while (imports.size() > 0) {
      for (MavenDependency anImport : imports) {
        Artifact dep = MavenTools.toArtifact(anImport, "pom", mappings);
        POM importPOM = loadPOM(dep);
        if (importPOM == null) {
          throw new ProcessFailureException("Unable to import Maven dependency definitions into a POM because the referenced import could not be found. [" + dep + "]");
        }

        pom.removeDependencyDefinition(anImport);
        pom.dependenciesDefinitions.addAll(importPOM.dependenciesDefinitions);
      }

      imports = pom.imports();
    }

    // Fill in variables again in case there are new ones. This also fills out any missing dependencies
    pom.replaceKnownVariablesAndFillInDependencies();

    return pom;
  }

  private ArtifactMetaData translatePOM(POM pom) {
    return new ArtifactMetaData(MavenTools.toSavantDependencies(pom, mappings), MavenTools.toSavantLicenses(pom));
  }
}
