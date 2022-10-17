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
package org.savantbuild.dep.workflow.process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.savantbuild.dep.PathTools;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.output.Output;

/**
 * This is an implementation of the Process that uses a local cache to fetch and publish artifacts.
 *
 * @author Brian Pontarelli
 */
public class CacheProcess implements Process {
  public final String dir;

  public final Output output;

  public CacheProcess(Output output, String dir) {
    this.output = output;
    if (dir == null) {
      this.dir = System.getProperty("user.home") + "/.savant/cache";
    } else {
      this.dir = dir;
    }
  }

  /**
   * Deletes the integration builds from the cache.
   *
   * @param artifact The artifact. This artifacts version is the next integration build version.
   * @throws ProcessFailureException If the integration builds could not be deleted.
   */
  @Override
  public void deleteIntegrationBuilds(Artifact artifact) throws ProcessFailureException {
    String path = String.join("/", dir, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version + "-{integration}");
    Path dir = Paths.get(path);
    if (!Files.isDirectory(dir)) {
      return;
    }

    try {
      PathTools.prune(dir);
    } catch (IOException e) {
      throw new ProcessFailureException(artifact, "Unable to delete integration builds from the local cache at [" + dir.toAbsolutePath() + "]", e);
    }
  }

  /**
   * Checks the cache directory for the item. If it exists it is returned. If not, null is returned.
   *
   * @param artifact        The artifact that the item is associated with.
   * @param item            The name of the item being fetched.
   * @param publishWorkflow The PublishWorkflow that is used to store the item if it can be found.
   * @return The File from the cache or null if it doesn't exist.
   * @throws NegativeCacheException If there is a negative cache record of the file, meaning it doesn't exist anywhere
   * in the world.
   */
  @Override
  public Path fetch(Artifact artifact, String item, PublishWorkflow publishWorkflow)
      throws NegativeCacheException {
    String path = String.join("/", dir, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), item);
    Path file = Paths.get(path);
    if (!Files.isRegularFile(file)) {
      file = Paths.get(path + ".neg");
      if (Files.isRegularFile(file)) {
        throw new NegativeCacheException(artifact);
      } else {
        file = null;
      }
    }

    return file;
  }

  /**
   * Publishes the given artifact item into the cache.
   *
   * @param artifact     The artifact that the item might be associated with.
   * @param item         The name of the item to publish.
   * @param artifactFile The path to the artifact.
   * @return Always null.
   * @throws ProcessFailureException If the publish fails.
   */
  @Override
  public Path publish(Artifact artifact, String item, Path artifactFile) throws ProcessFailureException {
    String cachePath = String.join("/", dir, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), item);
    Path cacheFile = Paths.get(cachePath);
    if (Files.isDirectory(cacheFile)) {
      throw new ProcessFailureException(artifact, "Your local artifact cache location is a directory [" + cacheFile.toAbsolutePath() + "]");
    }

    if (Files.isRegularFile(cacheFile)) {
      try {
        Files.delete(cacheFile);
      } catch (IOException e) {
        throw new ProcessFailureException(artifact, "Unable to delete old file in the local cache to replace [" + cacheFile.toAbsolutePath() + "]", e);
      }
    } else if (!Files.exists(cacheFile)) {
      try {
        Files.createDirectories(cacheFile.getParent());
      } catch (IOException e) {
        throw new ProcessFailureException(artifact, "Unable to create cache directory [" + cacheFile.getParent().toAbsolutePath() + "]");
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

      throw new ProcessFailureException(artifact, e);
    }

    output.debugln("Cached at [%s]", cacheFile);

    return cacheFile;
  }

  @Override
  public String toString() {
    return "Cache(" + dir + ")";
  }
}
