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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.workflow.process.Process;
import org.savantbuild.dep.workflow.process.ProcessFailureException;

/**
 * This is the interface that defines how artifacts are published to different locations during resolution. Publishing
 * is the act of storing the artifact for later use. In general the publishing corresponds one-to-one with the local
 * cache store locations that are used as part of the {@link FetchWorkflow}, but this is in no way required.
 *
 * @author Brian Pontarelli
 */
public class PublishWorkflow {
  public final List<Process> processes = new ArrayList<>();

  public PublishWorkflow(Process... processes) {
    Collections.addAll(this.processes, processes);
  }

  /**
   * Deletes all of the files that contain integration build versions.
   *
   * @param artifact The artifact information used to publish.
   */
  public void deleteIntegrationBuilds(Artifact artifact) {
    for (Process process : processes) {
      process.deleteIntegrationBuilds(artifact);
    }
  }

  /**
   * @return The process list.
   */
  public List<Process> getProcesses() {
    return processes;
  }

  /**
   * Publishes the item using the processes in this workflow.
   *
   * @param artifact The artifact if needed.
   * @param item     The name of the item being published.
   * @param file     The file that is the artifact contents.
   * @return A file that can be used to reference the artifact for paths and other constructs.
   * @throws ProcessFailureException If the artifact could not be published for any reason.
   */
  public Path publish(Artifact artifact, String item, Path file) throws ProcessFailureException {
    Path result = null;
    for (Process process : processes) {
      Path temp = process.publish(artifact, item, file);
      if (result == null) {
        result = temp;
      }
    }

    return result;
  }

  /**
   * Publishes a negative file for the artifact item. This file is empty, but signals Savant not to attempt to fetch
   * that specific item again, since it doesn't exist.
   *
   * @param artifact The artifact information used to publish.
   * @param item     The item that the negative is being published for.
   */
  public void publishNegative(Artifact artifact, String item) {
    Path itemFile;
    try {
      File tempFile = File.createTempFile("savant-item", "neg");
      tempFile.deleteOnExit();
      itemFile = tempFile.toPath();
    } catch (IOException e) {
      // This is okay, because negatives are only for performance and if we can't create one, we'll just
      // head out and try and fetch it again next time.
      return;
    }

    for (Process process : processes) {
      try {
        process.publish(artifact, item + ".neg", itemFile);
      } catch (ProcessFailureException e) {
        // Continue since this is okay.
      }
    }
  }
}
