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
import org.savantbuild.dep.io.FileTools;
import org.savantbuild.dep.io.IOTools;
import org.savantbuild.dep.io.MD5;
import org.savantbuild.dep.io.MD5Exception;
import org.savantbuild.dep.net.NetTools;
import org.savantbuild.dep.util.RuntimeTools;
import org.savantbuild.dep.workflow.PublishWorkflow;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * This is an implementation of the Process that uses the SVNKit SubVersion library to fetch and publish artifacts
 * from/to a SubVersion repository using SubVersion export and import commands.
 *
 * @author Brian Pontarelli
 */
public class SVNProcess implements Process {
  private final static Logger logger = Logger.getLogger(SVNProcess.class.getName());

  private final String password;

  private final String repository;

  private final String username;

  public SVNProcess(String repository, String username, String password) {
    Objects.requireNonNull(repository, "The [repository] attribute is required for the [svn] workflow process");
    if (username != null || password != null) {
      Objects.requireNonNull(username, "You must specify both the [username] and [password] attributes to turn on authentication for the [svn] workflow process.");
      Objects.requireNonNull(password, "You must specify both the [username] and [password] attributes to turn on authentication for the [svn] workflow process.");
    }

    this.repository = repository;
    this.username = username;
    this.password = password;
  }

  /**
   * Not implemented yet.
   */
  @Override
  public void deleteIntegrationBuilds(Artifact artifact) throws ProcessFailureException {
    throw new ProcessFailureException("The [svn] process doesn't allow deleting of integration builds.");
  }

  /**
   * Fetches the artifact from the SubVersion repository by performing an export to a temporary file and checking the
   * MD5 sum if it exists.
   *
   * @param artifact        The artifact to fetch and store.
   * @param item            The item to fetch.
   * @param publishWorkflow The publish workflow used to publish the artifact after it has been successfully fetched.
   * @return The File or null if it doesn't exist.
   * @throws ProcessFailureException If the SVN fetch failed.
   */
  @Override
  public Path fetch(Artifact artifact, String item, PublishWorkflow publishWorkflow) throws ProcessFailureException {
    try {
      Path md5File = FileTools.createTempPath("savant-svn-process", "export", true);
      URI md5URI = NetTools.build(repository, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), item + ".md5");
      if (!export(md5URI, md5File)) {
        return null;
      }

      MD5 md5;
      try {
        md5 = IOTools.parseMD5(md5File);
      } catch (IOException e) {
        Files.delete(md5File);
        throw new ProcessFailureException(e);
      }

      Path itemFile = FileTools.createTempPath("savant-svn-process", "export", true);
      URI itemURI = NetTools.build(repository, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), item);
      if (!export(itemURI, itemFile)) {
        return null;
      }

      MD5 itemMD5 = MD5.fromPath(itemFile);
      if (!itemMD5.equals(md5)) {
        throw new MD5Exception("Artifact item file [" + itemURI.toString() + "] doesn't match MD5");
      }

      logger.info("Downloaded from SubVersion at [" + itemURI + "]");

      md5File = publishWorkflow.publish(artifact, item + ".md5", md5File);
      try {
        itemFile = publishWorkflow.publish(artifact, item, itemFile);
      } catch (ProcessFailureException e) {
        Files.delete(md5File);
        throw new ProcessFailureException(e);
      }

      return itemFile;
    } catch (IOException | URISyntaxException | InterruptedException e) {
      throw new ProcessFailureException(e);
    }
  }

  /**
   * Publishes the given artifact item into the SubVersion repository.
   *
   * @param artifact     The artifact that the item might be associated with.
   * @param item         The name of the item to publish.
   * @param artifactFile The file that is the item.
   * @return Always null.
   * @throws ProcessFailureException If the publish fails.
   */
  @Override
  public Path publish(Artifact artifact, String item, Path artifactFile) throws ProcessFailureException {
    try {
      URI uri = NetTools.build(repository, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), item);
      if (!imprt(uri, artifactFile)) {
        throw new ProcessFailureException("Unable to publish artifact item [" + item + "] to [" + uri + "]");
      }

      logger.info("Published to SubVersion at [" + repository + "/" + uri + "]");
      return null;
    } catch (URISyntaxException | IOException | InterruptedException e) {
      throw new ProcessFailureException(e);
    }
  }

  private boolean export(URI uri, Path file) throws IOException, InterruptedException {
    if (username != null) {
      return RuntimeTools.exec("svn", "export", "--force", "--non-interactive", "--no-auth-cache", "--username", username, "--password", password, uri.toString(), file.toAbsolutePath().toString());
    }

    return RuntimeTools.exec("svn", "export", "--force", "--non-interactive", "--no-auth-cache", uri.toString(), file.toAbsolutePath().toString());
  }

  private boolean imprt(URI uri, Path file) throws IOException, InterruptedException {
    if (username != null) {
      return RuntimeTools.exec("svn", "import", "--non-interactive", "--no-auth-cache", "-m", "Published artifact", "--username", username, "--password", password, file.toAbsolutePath().toString(), uri.toString());
    }

    return RuntimeTools.exec("svn", "import", "--non-interactive", "--no-auth-cache", "-m", "Published artifact", file.toAbsolutePath().toString(), uri.toString());
  }
}
