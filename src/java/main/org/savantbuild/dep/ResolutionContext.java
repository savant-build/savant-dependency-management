/*
 * Copyright (c) 2008, Inversoft, All Rights Reserved.
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
package org.savantbuild.dep;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.savantbuild.dep.domain.Artifact;

/**
 * <p>
 * This class is the context that is passed around during a single
 * resolution in order to keep track of the state of the resolution.
 * This is very important for keeping track of what items have been
 * missing in order to possibly go back and store negatives for those
 * items.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class ResolutionContext {
  private final Map<Artifact, Set<String>> missingItems = new HashMap<Artifact, Set<String>>();
  private final Map<Artifact, File> files = new HashMap<Artifact, File>();

  /**
   * Adds a dependency item that could not be resolved.
   *
   * @param artifact The artifact for which the item (could be the artifact itself) could not be resolved.
   * @param item     The item.
   */
  public void addMissingItem(Artifact artifact, String item) {
    Set<String> items = missingItems.get(artifact);
    if (items == null) {
      items = new HashSet<String>();
      missingItems.put(artifact, items);
    }

    items.add(item);
  }

  /**
   * @return The Map of missing items for artifacts. This Map is live.
   */
  public Map<Artifact, Set<String>> getMissingItems() {
    return missingItems;
  }

  /**
   * Associates the given artifact with the given local file.
   *
   * @param artifact The artifact.
   * @param file     The file.
   */
  public void addArtifactFile(Artifact artifact, File file) {
    files.put(artifact, file);
  }

  /**
   * @return The Map of artifact files.
   */
  public Map<Artifact, File> getArtifactFiles() {
    return files;
  }
}
