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

import org.savantbuild.dep.domain.ResolvableItem;
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

  protected final ItemSource itemSource;

  public CacheProcess(Output output, String dir) {
    this(output, dir, ItemSource.SAVANT);
  }

  protected CacheProcess(Output output, String dir, ItemSource itemSource) {
    this.output = output;
    this.dir = dir != null ? dir : System.getProperty("user.home") + "/.savant/cache";
    this.itemSource = itemSource;
  }

  /**
   * Checks the cache directory for the item. If it exists it is returned. If not, null is returned.
   *
   * @param item            The item being fetched.
   * @param publishWorkflow The PublishWorkflow that is used to store the item if it can be found.
   * @return The FetchResult from the cache or null if it doesn't exist.
   * @throws NegativeCacheException If there is a negative cache record of the file, meaning it doesn't exist
   *     anywhere in the world.
   */
  @Override
  public FetchResult fetch(ResolvableItem item, PublishWorkflow publishWorkflow) throws NegativeCacheException {
    Path file = _fetch(item, dir);
    return file != null ? new FetchResult(file, itemSource, item) : null;
  }

  /**
   * Publishes the given artifact item into the cache. Items are only accepted if their source matches this cache's
   * itemSource (e.g. CacheProcess only accepts SAVANT, MavenCacheProcess only accepts MAVEN).
   *
   * @param fetchResult The fetch result containing the item, file, and source.
   * @return The path to the published file, or null if the source doesn't match.
   * @throws ProcessFailureException If the publish fails.
   */
  @Override
  public Path publish(FetchResult fetchResult) throws ProcessFailureException {
    if (fetchResult.source() != itemSource) {
      return null;
    }

    ResolvableItem item = fetchResult.item();
    Path itemFile = fetchResult.file();

    String cachePath = String.join("/", dir, item.group.replace('.', '/'), item.project, item.version, item.item);
    Path cacheFile = Paths.get(cachePath);
    if (Files.isDirectory(cacheFile)) {
      throw new ProcessFailureException("Your local artifact cache location is a directory [" + cacheFile.toAbsolutePath() + "]");
    }

    if (Files.isRegularFile(cacheFile)) {
      try {
        Files.delete(cacheFile);
      } catch (IOException e) {
        throw new ProcessFailureException("Unable to delete old file in the local cache to replace [" + cacheFile.toAbsolutePath() + "]", e);
      }
    } else if (!Files.exists(cacheFile)) {
      try {
        Files.createDirectories(cacheFile.getParent());
      } catch (IOException e) {
        throw new ProcessFailureException("Unable to create cache directory [" + cacheFile.getParent().toAbsolutePath() + "]");
      }
    }

    try {
      Files.copy(itemFile, cacheFile);
    } catch (IOException e) {
      // Clean up the artifact if it was a partial copy
      if (Files.exists(cacheFile)) {
        try {
          Files.delete(cacheFile);
        } catch (IOException e1) {
          // Smother since we are already in a failure state
        }
      }

      throw new ProcessFailureException(item, e);
    }

    output.debugln("Cached at [%s]", cacheFile);

    return cacheFile;
  }

  @Override
  public String toString() {
    return "Cache(" + dir + ")";
  }

  private Path _fetch(ResolvableItem item, String cacheDir) {
    String path = String.join("/", cacheDir, item.group.replace('.', '/'), item.project, item.version, item.item);
    output.debugln("      - File [" + path + "]");
    Path file = Paths.get(path);
    if (!Files.isRegularFile(file)) {
      file = Paths.get(path + ".neg");
      if (Files.isRegularFile(file)) {
        output.debugln("      - Found negative marker");
        throw new NegativeCacheException(item);
      } else {
        output.debugln("      - Not found");
        file = null;
      }
    }

    if (file != null) {
      output.debugln("      - Found");
    }

    return file;
  }
}
