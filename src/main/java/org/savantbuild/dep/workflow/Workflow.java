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
package org.savantbuild.dep.workflow;

import org.savantbuild.dep.domain.AbstractArtifact;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.VersionException;
import org.savantbuild.security.MD5Exception;
import org.savantbuild.dep.workflow.process.NegativeCacheException;
import org.savantbuild.dep.workflow.process.ProcessFailureException;
import org.savantbuild.dep.xml.ArtifactTools;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;

/**
 * This class models a grouping of a fetch and publish workflow.
 *
 * @author Brian Pontarelli
 */
public class Workflow {
  public final FetchWorkflow fetchWorkflow;

  public final PublishWorkflow publishWorkflow;

  public Workflow(FetchWorkflow fetchWorkflow, PublishWorkflow publishWorkflow) {
    this.fetchWorkflow = fetchWorkflow;
    this.publishWorkflow = publishWorkflow;
  }

  /**
   * Fetches the artifact itself. Every artifact in a Savant dependency graph is required to exist. Therefore, Savant
   * never negative caches artifact files and this method will always return the artifact file or throw an
   * ArtifactMissingException.
   *
   * @param artifact The artifact to fetch.
   * @return The Path of the artifact and never null.
   * @throws ArtifactMissingException If the artifact could not be found.
   * @throws ProcessFailureException  If any of the processes encountered a failure while attempting to fetch the
   *                                  artifact.
   * @throws MD5Exception             If the item's MD5 file did not match the item.
   */
  public Path fetchArtifact(AbstractArtifact artifact)
      throws ArtifactMissingException, ProcessFailureException, MD5Exception {
    Path file = fetchWorkflow.fetchItem(artifact, artifact.getArtifactFile(), publishWorkflow);
    if (file == null) {
      throw new ArtifactMissingException(artifact);
    }

    return file;
  }

  /**
   * Fetches the artifact meta data. Every artifact in Savant is required to have an AMD file. Otherwise, it is
   * considered a missing artifact entirely. Therefore, Savant never negative caches AMD files and this method will
   * always return an AMD file or throw an ArtifactMetaDataMissingException.
   *
   * @param artifact The artifact to fetch the meta data for.
   * @return The ArtifactMetaData object and never null.
   * @throws ArtifactMetaDataMissingException
   *                                 If the AMD file could not be found.
   * @throws ProcessFailureException If any of the processes encountered a failure while attempting to fetch the AMD
   *                                 file.
   * @throws MD5Exception            If the item's MD5 file did not match the item.
   */
  public ArtifactMetaData fetchMetaData(AbstractArtifact artifact)
      throws ArtifactMetaDataMissingException, ProcessFailureException, MD5Exception {
    Path file = fetchWorkflow.fetchItem(artifact, artifact.getArtifactMetaDataFile(), publishWorkflow);
    if (file == null) {
      throw new ArtifactMetaDataMissingException(artifact);
    }

    try {
      return ArtifactTools.parseArtifactMetaData(file);
    } catch (IllegalArgumentException | NullPointerException | SAXException | ParserConfigurationException | IOException | VersionException e) {
      throw new ProcessFailureException(artifact, e);
    }
  }

  /**
   * Fetches the source of the artifact. If a source file is missing, this method stores a negative file in the cache so
   * that an attempt to download the source file isn't made each time. This is required so that offline work can be done
   * by only hitting the local cache of dependencies.
   *
   * @param artifact The artifact to fetch the source for.
   * @return The Path of the source or null if it doesn't exist.
   * @throws ProcessFailureException If any of the processes encountered a failure while attempting to fetch the source
   *                                 file.
   * @throws MD5Exception            If the item's MD5 file did not match the item.
   */
  public Path fetchSource(AbstractArtifact artifact) throws ProcessFailureException, MD5Exception {
    try {
      Path file = fetchWorkflow.fetchItem(artifact, artifact.getArtifactSourceFile(), publishWorkflow);
      if (file == null) {
        publishWorkflow.publishNegative(artifact, artifact.getArtifactSourceFile());
      }

      return file;
    } catch (NegativeCacheException e) {
      // This is a short-circuit exit from the workflow. It is only thrown by the CacheProcess and indicates that the
      // search for the source JAR should stop immediately.
      return null;
    }
  }
}
