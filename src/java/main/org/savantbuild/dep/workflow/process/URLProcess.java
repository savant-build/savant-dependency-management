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

import org.savantbuild.dep.domain.AbstractArtifact;
import org.savantbuild.dep.io.IOTools;
import org.savantbuild.dep.io.MD5;
import org.savantbuild.dep.io.MD5Exception;
import org.savantbuild.dep.net.NetTools;
import org.savantbuild.dep.workflow.PublishWorkflow;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * This class is a workflow process that attempts to download artifacts from the internet using the Savant scheme via
 * HTTP.
 * <p/>
 * Savant's URL scheme is
 * <p/>
 * <b>domain</b>/<b>group</b>/<b>project</b>/<b>version</b>/<b>name</b>-<b>version</b>.<b>type</b>
 *
 * @author Brian Pontarelli
 */
public class URLProcess implements Process {
  private final static Logger logger = Logger.getLogger(URLProcess.class.getName());

  private final String password;

  private final String url;

  private final String username;

  public URLProcess(String url, String username, String password) {
    Objects.requireNonNull(url, "The [url] attribute is required for the [url] workflow process");
    if (username != null || password != null) {
      Objects.requireNonNull(username, "You must specify both the [username] and [password] attributes to turn on authentication for the [url] workflow process.");
      Objects.requireNonNull(password, "You must specify both the [username] and [password] attributes to turn on authentication for the [url] workflow process.");
    }

    this.url = url;
    this.username = username;
    this.password = password;
  }

  /**
   * Throws an exception. This isn't supported yet.
   */
  @Override
  public void deleteIntegrationBuilds(AbstractArtifact artifact) throws ProcessFailureException {
    throw new ProcessFailureException(artifact, "The [url] process doesn't support deleting integration builds.");
  }

  /**
   * Using the URL spec given, this method connects to the URL, reads the file from the URL and stores the file in the
   * local cache store. The artifact is used to determine the local cache store directory and file name.
   *
   * @param artifact        The artifact being fetched and stored
   * @param publishWorkflow The publishWorkflow to publish the artifact if found.
   * @param item            The item to fetch.
   * @return The File of the artifact after it has been published.
   */
  @Override
  public Path fetch(AbstractArtifact artifact, String item, PublishWorkflow publishWorkflow) throws ProcessFailureException {
    try {
      URI md5URI = NetTools.build(url, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), item + ".md5");
      Path md5File = NetTools.downloadToPath(md5URI, username, password, null);
      if (md5File == null) {
        return null;
      }

      MD5 md5;
      try {
        md5 = IOTools.parseMD5(md5File);
      } catch (IOException e) {
        Files.delete(md5File);
        throw new ProcessFailureException(artifact, e);
      }

      URI itemURI = NetTools.build(url, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), item);
      Path itemFile;
      try {
        itemFile = NetTools.downloadToPath(itemURI, username, password, md5);
      } catch (MD5Exception e) {
        throw new MD5Exception("MD5 mismatch when downloading item from [" + itemURI.toString() + "]");
      }

      if (itemFile != null) {
        logger.info("Downloaded from [" + itemURI + "]");
        md5File = publishWorkflow.publish(artifact, item + ".md5", md5File);
        try {
          itemFile = publishWorkflow.publish(artifact, item, itemFile);
        } catch (ProcessFailureException e) {
          Files.delete(md5File);
          throw new ProcessFailureException(artifact, e);
        }
      }

      return itemFile;
    } catch (FileNotFoundException e) {
      // Special case for file:// URLs
      return null;
    } catch (IOException | URISyntaxException e) {
      throw new ProcessFailureException(artifact, e);
    }
  }

  /**
   * Throws an exception. This isn't supported yet.
   */
  @Override
  public Path publish(AbstractArtifact artifact, String item, Path file) throws ProcessFailureException {
    throw new ProcessFailureException(artifact, "The [url] process doesn't allow publishing.");
  }
}
