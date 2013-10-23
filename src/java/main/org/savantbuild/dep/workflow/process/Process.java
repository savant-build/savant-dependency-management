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

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.workflow.PublishWorkflow;

import java.nio.file.Path;

/**
 * This interface defines a workflow process that can be used for either publishing or for fetching.
 *
 * @author Brian Pontarelli
 */
public interface Process {
  /**
   * Deletes the integration builds.
   *
   * @param artifact The artifact. This artifacts version is the next integration build version.
   */
  void deleteIntegrationBuilds(Artifact artifact);

  /**
   * Attempts to fetch the given item. The item is normally associated with the artifact, but might be associated with a
   * group or project. This method can use the artifact for logging or other purposes, but should use the item String
   * for fetching only.
   * <p/>
   * If the item is found, it should be published by calling the {@link PublishWorkflow}.
   *
   * @param artifact        The artifact that the item is associated with.
   * @param item            The name of the item being fetched. This item name should NOT include the path information.
   *                        This will be handled by the processes so that flattened namespacing and other types of
   *                        handling can be performed. This item should only be the name of the item being fetched. For
   *                        example, if the artifact MD5 file is being fetched this would look like this:
   *                        common-collections-2.1.jar.md5.
   * @param publishWorkflow The PublishWorkflow that is used to store the item if it can be found.
   * @return The Path to the item on the local disk or null if the item does not exist and there were no failures.
   * @throws ProcessFailureException If the process failed when fetching the artifact.
   */
  Path fetch(Artifact artifact, String item, PublishWorkflow publishWorkflow) throws ProcessFailureException;

  /**
   * Attempts to publish the given item. The item is normally associated with the artifact, but might be associated with
   * a group or project. This method can use the artifact for logging or other purposes, but should use the item String
   * for publishing only.
   * <p/>
   * If the item is published in a manner that a file can be returned, that file should be returned as it might be used
   * to create paths or other constructs.
   *
   * @param artifact     The artifact that the item might be associated with.
   * @param item         The name of the item to publish.
   * @param artifactFile The path to the artifact stored on disk (which could be a temporary file that it was downloaded
   *                     to).
   * @return The file if the publish process stored the given file locally (local cache for example). Otherwise, this
   *         should return null.
   * @throws ProcessFailureException If there was any issue publishing.
   */
  Path publish(Artifact artifact, String item, Path artifactFile) throws ProcessFailureException;
}
