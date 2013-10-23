/*
 * Copyright (c) 2001-2010, Inversoft, All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.dep.workflow.process;

import org.savantbuild.dep.DependencyException;
import org.savantbuild.dep.NegativeCacheException;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.io.DoesNotExistException;
import org.savantbuild.dep.io.FileTools;
import org.savantbuild.dep.version.ArtifactVersionTools;
import org.savantbuild.dep.workflow.PublishWorkflow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * This is an implementation of the Process that uses the a local cache to fetch and publish artifacts.
 *
 * @author Brian Pontarelli
 */
public class CacheProcess implements Process {
  private final static Logger logger = Logger.getLogger(CacheProcess.class.getName());

  private String dir;

  public CacheProcess(Map<String, String> attributes) {
    this.dir = attributes.get("dir");
    if (dir == null) {
      dir = System.getProperty("user.home") + "/.savant/cache";
    }
  }

  /**
   * Deletes the artifact item.
   *
   * @param artifact The artifact if needed.
   * @param item     The item to delete.
   * @return True if the item was deleted, false otherwise.
   * @throws DependencyException If the delete failed.
   */
  @Override
  public boolean delete(Artifact artifact, String item) throws DependencyException {
    String path = String.join("/", dir, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version, item);
    File file = new File(path);
    boolean deleted = false;
    if (file.isFile()) {
      deleted = file.delete();
    }

    return deleted;
  }

  /**
   * Deletes the integration builds from the cache.
   *
   * @param artifact The artifact. This artifacts version is the next integration build version.
   */
  @Override
  public void deleteIntegrationBuilds(Artifact artifact) {
    String path = String.join("/", dir, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version + "-{integration}");
    Path dir = Paths.get(path);
    if (!Files.isDirectory(dir)) {
      return;
    }

    try {
      FileTools.prune(dir);
    } catch (IOException e) {
      throw new DependencyException("Unable to delete integration builds from [" + dir.toAbsolutePath() + "]", e);
    }
  }

  /**
   * Finds the latest or integration build of the artifact inside the cache.
   *
   * @param artifact The artifact to get the version for.
   * @return The version or null if it couldn't be found.
   */
  @Override
  public String determineVersion(Artifact artifact) {
    String version = artifact.version;
    if (version.equals(ArtifactVersionTools.LATEST)) {
      File dir = new File(String.join("/", this.dir, artifact.id.group.replace('.', '/'), artifact.id.project));
      Set<String> names = listFiles(dir);
      version = ArtifactVersionTools.latest(names);
    } else if (version.endsWith(ArtifactVersionTools.INTEGRATION)) {
      File dir = new File(String.join("/", this.dir, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version));
      Set<String> names = listFiles(dir);
      version = ArtifactVersionTools.bestIntegration(artifact, names);
    }

    return version;
  }

  /**
   * Checks the cache directory for the item. If it exists it is returned. If not, either a NegativeCacheException or a
   * DoesNotExistException is thrown (depending on if there is a negative cache record or not).
   *
   * @param artifact               The artifact that the item is associated with.
   * @param item                   The name of the item being fetched.
   * @param publishWorkflow The PublishWorkflow that is used to store the item if it can be found.
   * @return The File from the cache.
   * @throws DoesNotExistException  If the file doesn't exist.
   * @throws NegativeCacheException If there is a negative cache record of the file, meaning it doesn't exist anywhere
   *                                in the world.
   */
  @Override
  public Path fetch(Artifact artifact, String item, PublishWorkflow publishWorkflow)
      throws DoesNotExistException, NegativeCacheException {
    String path = String.join("/", dir, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version, item);
    Path file = Paths.get(path);
    if (!Files.isRegularFile(file)) {
      file = Paths.get(path + ".neg");
      if (Files.isRegularFile(file)) {
        throw new NegativeCacheException();
      } else {
        throw new DoesNotExistException();
      }
    }

    return file;
  }

  /**
   * Publishes the given artifact item into the cache.
   *
   * @param artifact The artifact that the item might be associated with.
   * @param item     The name of the item to publish.
   * @param artifactFile     The path to the artifact.
   * @return Always null.
   * @throws DependencyException If the publish fails.
   */
  @Override
  public Path publish(Artifact artifact, String item, Path artifactFile) throws DependencyException {
    String cachePath = String.join("/", dir, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version, item);
    Path cacheFile = Paths.get(cachePath);
    if (Files.isDirectory(cacheFile)) {
      throw new DependencyException("Cache location is for an artifact to be stored is a directory [" + cacheFile.toAbsolutePath() + "]");
    }

    if (Files.isRegularFile(cacheFile)) {
      try {
        Files.delete(cacheFile);
      } catch (IOException e) {
        throw new DependencyException("Unable to clean out old file to replace [" + cacheFile.toAbsolutePath() + "]", e);
      }
    } else if (!Files.exists(cacheFile)) {
      try {
        Files.createDirectories(cacheFile.getParent());
      } catch (IOException e) {
        throw new DependencyException("Unable to create cache directory [" + cacheFile.getParent().toAbsolutePath() + "]");
      }
    }

    try {
      Files.copy(artifactFile, cacheFile);
    } catch (IOException e) {
      // Clean up the artifact if it was a partial copy
      if (Files.exists(cacheFile)) {
        try {
          Files.delete(cacheFile);
        } catch (IOException e1) {
          // Smother since we are already in a failure state
        }
      }

      throw new DependencyException(e);
    }

    if (!item.endsWith("md5")) {
      logger.info("Cached at [" + dir + "/" + artifactFile + "]");
    }

    return cacheFile;
  }

  private Set<String> listFiles(File dir) {
    Set<String> names = new HashSet<>();
    File[] files = dir.listFiles();
    if (files == null || files.length == 0) {
      return names;
    }

    for (File file : files) {
      String fileName = file.getName();
      names.add(fileName);
    }

    return names;
  }
}
