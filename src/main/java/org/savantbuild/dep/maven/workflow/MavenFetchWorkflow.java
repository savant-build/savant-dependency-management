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
package org.savantbuild.dep.maven.workflow;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.workflow.process.Process;
import org.savantbuild.dep.workflow.process.ProcessFailureException;
import org.savantbuild.output.Output;
import org.savantbuild.security.MD5Exception;

/**
 * This class is the workflow that is used when attempting to fetch artifacts.
 *
 * @author Brian Pontarelli
 */
public class MavenFetchWorkflow {
  public final List<Process> processes = new ArrayList<>();

  private final Output output;

  public MavenFetchWorkflow(Output output, Process... processes) {
    this.output = output;
    Collections.addAll(this.processes, processes);
  }

  /**
   * This loops over all the processes until the item is found or not. Each process must call to the PublishWorkflow if
   * it finds the artifact and the publish workflow must be able to return a File that can be used for future
   * reference.
   *
   * @param artifact        The artifact if needed.
   * @param item            The name of the item being fetched. This item name should NOT include the path information.
   *                        This will be handled by the processes so that flattened namespacing and other types of
   *                        handling can be performed. This item should only be the name of the item being fetched. For
   *                        example, if the artifact MD5 file is being fetched this would look like this:
   *                        common-collections-2.1.jar.md5.
   * @param publishWorkflow The PublishWorkflow that is used to store the item if it can be found.
   * @return A file that contains the item contents or null if the item was not found.
   * @throws ProcessFailureException If any of the processes failed while attempting to fetch the artifact.
   * @throws MD5Exception If the item's MD5 file did not match the item.
   */
  public Path fetchItem(Artifact artifact, String item, PublishWorkflow publishWorkflow)
      throws ProcessFailureException, MD5Exception {
    output.debugln("Running processes %s to fetch [%s]", processes, item);
    return processes.stream()
                    .map((process) -> process.fetch(artifact, item, publishWorkflow))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
  }
}
