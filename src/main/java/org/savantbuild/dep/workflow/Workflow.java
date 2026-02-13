/*
 * Copyright (c) 2014-2025, Inversoft Inc., All Rights Reserved
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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.savantbuild.dep.ArtifactTools;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.ResolvableItem;
import org.savantbuild.dep.maven.MavenDependency;
import org.savantbuild.dep.maven.MavenTools;
import org.savantbuild.dep.maven.POM;
import org.savantbuild.dep.workflow.process.DoNotPublishProcess;
import org.savantbuild.dep.workflow.process.NegativeCacheException;
import org.savantbuild.dep.workflow.process.ProcessFailureException;
import org.savantbuild.domain.Version;
import org.savantbuild.domain.VersionException;
import org.savantbuild.output.Output;
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

  public final Map<String, String> rangeMappings = new HashMap<>();

  // In-memory AMD cache for the duration of this build. Keyed by "group:project:name:version".
  // Prevents re-parsing the same artifact's AMD/POM multiple times during a single build invocation.
  // HashMap is safe here -- the resolution pipeline is single-threaded (no parallel streams or executors).
  private final Map<String, ArtifactMetaData> amdCache = new HashMap<>();

  // In-memory POM cache for the duration of this build. Keyed by "group:project:version".
  // Parent POMs and BOM POMs are shared across sibling dependencies -- this avoids re-parsing them.
  // HashMap is safe here -- the resolution pipeline is single-threaded (no parallel streams or executors).
  private final Map<String, POM> pomCache = new HashMap<>();

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
      // Don't write out non-semantic versioned artifacts, we will re-publish below using the semantic version
      file = fetchWorkflow.fetchItem(item, new PublishWorkflow(new DoNotPublishProcess()));
      // Publish the Savant named JAR to prevent going back out to remote repositories next time we want to load JARs
      if (file != null) {
        item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
        file = publishWorkflow.publish(item, file);
      }
    }

    if (file == null) {
      throw new ArtifactMissingException(artifact);
    }

    return file;
  }

  /**
   * Fetches the artifact metadata using a two-path resolution strategy:
   * <ol>
   *   <li>Check in-memory cache (covers both Savant-native and Maven-generated AMDs)</li>
   *   <li>Try fetching a pre-built AMD file via the fetch workflow (Savant-native artifacts have AMD files
   *       in the Savant repository). If found, parse and cache in memory.</li>
   *   <li>If no AMD file exists, this is a Maven artifact. Load the POM, translate it to ArtifactMetaData
   *       using the current build's semantic version mappings, and cache in memory only (no disk write).
   *       This ensures mappings are always applied fresh and never become stale.</li>
   * </ol>
   *
   * @param artifact The artifact to fetch the metadata for.
   * @return The ArtifactMetaData object and never null.
   * @throws ArtifactMetaDataMissingException If neither an AMD file nor a POM could be found.
   * @throws ProcessFailureException If any of the processes encountered a failure while attempting to fetch the AMD
   *     file or POM.
   * @throws MD5Exception If the item's MD5 file did not match the item.
   */
  public ArtifactMetaData fetchMetaData(Artifact artifact) throws ArtifactMetaDataMissingException, ProcessFailureException, MD5Exception {
    String cacheKey = artifact.id.group + ":" + artifact.id.project + ":" + artifact.id.name + ":" + artifact.version;

    // 1. Check in-memory cache first
    ArtifactMetaData cached = amdCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactMetaDataFile());
    try {
      // 2. Try to fetch pre-built AMD (Savant-native artifacts have AMD files in the repo).
      //    If found, the fetch/publish workflow handles disk caching in ~/.savant/cache (global).
      Path file = fetchWorkflow.fetchItem(item, publishWorkflow);
      if (file != null) {
        ArtifactMetaData amd = ArtifactTools.parseArtifactMetaData(file, mappings);
        amdCache.put(cacheKey, amd);
        return amd;
      }

      // 3. No AMD found -- this is a Maven artifact. Generate AMD in-memory from POM.
      //    The POM is fetched from ~/.m2 or downloaded from Maven Central.
      //    Mappings from the current build file are applied fresh (never stale).
      POM pom = loadPOM(artifact);
      if (pom == null) {
        throw new ArtifactMetaDataMissingException(artifact);
      }

      ArtifactMetaData amd = translatePOM(pom);
      amdCache.put(cacheKey, amd);
      return amd;
      // NOTE: No publishWorkflow.publish() for Maven-generated AMDs -- they are in-memory only.
      // This eliminates the stale AMD cache problem where changing semanticVersions mappings
      // in the build file would require manually deleting .savant/cache.
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
          // we should be returning our locally cached/published source path, not the remote source path
          file = publishWorkflow.publish(item, file);
        }
      }

      if (file == null && artifact.nonSemanticVersion != null) {
        item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.nonSemanticVersion, artifact.getArtifactNonSemanticAlternativeSourceFile());
        // Don't write out non-semantic versioned source artifacts, we will re-publish below using the semantic version
        file = fetchWorkflow.fetchItem(item, new PublishWorkflow(new DoNotPublishProcess()));

        // Publish the Savant named source JAR to prevent going back out to remote repositories next time we want to load source JARs
        if (file != null) {
          item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactSourceFile());
          file = publishWorkflow.publish(item, file);
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
    String cacheKey = artifact.id.group + ":" + artifact.id.project + ":" + artifact.version;

    // Check in-memory POM cache first (parent POMs and BOM POMs are shared across siblings)
    POM cachedPom = pomCache.get(cacheKey);
    if (cachedPom != null) {
      return cachedPom;
    }

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
    pom.replaceRangeValuesWithMappings(rangeMappings);

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
    while (!imports.isEmpty()) {
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
    pom.replaceRangeValuesWithMappings(rangeMappings);

    // Cache the fully resolved POM for reuse (parent POMs, BOM POMs shared across siblings).
    // IMPORTANT: POM is mutable -- all mutations (variable replacement, parent resolution, import loading)
    // must be complete before this point. If multi-threaded resolution is ever introduced, cached POMs
    // could be read while still being modified, breaking the cache.
    pomCache.put(cacheKey, pom);

    return pom;
  }

  private ArtifactMetaData translatePOM(POM pom) {
    return new ArtifactMetaData(MavenTools.toSavantDependencies(pom, mappings), MavenTools.toSavantLicenses(pom));
  }
}
