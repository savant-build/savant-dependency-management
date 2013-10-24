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
import org.savantbuild.dep.net.SubVersion;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
      URI md5URI = NetTools.build(artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), item + ".md5");
      Path md5File = export(md5URI, null);
      MD5 md5;
      try {
        md5 = IOTools.parseMD5(md5File);
      } catch (IOException e) {
        Files.delete(md5File);
        throw new ProcessFailureException(e);
      }

      URI itemURI = NetTools.build(repository, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), item);
      Path itemFile = export(itemURI, md5);
      if (itemFile == null) {
        return null;
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
    } catch (IOException | URISyntaxException e) {
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
    try (SubVersion svn = new SubVersion(repository, username, password)) {
      if (!svn.isExists()) {
        throw new ProcessFailureException("Repository URL [" + repository + "] doesn't exist on the SubVersion server");
      } else if (svn.isFile()) {
        throw new ProcessFailureException("Repository URL [" + repository + "] points to a file and must point to a directory");
      }

      URI uri = NetTools.build(artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), item);
      svn.doImport(uri.toString(), artifactFile);
      logger.info("Published to SubVersion at [" + repository + "/" + uri + "]");
      return null;
    } catch (SVNException | URISyntaxException e) {
      throw new ProcessFailureException(e);
    }
  }

  private Path export(final URI uri, final MD5 md5) throws IOException {
    File file = File.createTempFile("savant-svn-process", "export");
    file.deleteOnExit();

    Path path = file.toPath();
    try (SubVersion svn = new SubVersion(repository, username, password)) {
      if (!svn.isExists()) {
        throw new IOException("Repository [" + repository + "] doesn't exist on the SubVersion server");
      } else if (svn.isFile()) {
        throw new IOException("Repository [" + repository + "] points to a file and must point to a directory");
      }

      svn.doExport(uri.toString(), path);

      if (md5 != null && md5.bytes != null) {
        MD5 exportedMD5 = FileTools.md5(path);
        if (!Arrays.equals(exportedMD5.bytes, md5.bytes)) {
          throw new MD5Exception("MD5 mismatch.");
        }
      }

      return path;
    } catch (SVNException e) {
      // These should indicate that the URL was not valid and the exported file doesn't exist
      if (e.getErrorMessage().getErrorCode() == SVNErrorCode.BAD_URL || e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_ILLEGAL_URL) {
        return null;
      }

      throw new IOException(e);
    }
  }
}
