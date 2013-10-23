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

import org.savantbuild.dep.DependencyException;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.io.DoesNotExistException;
import org.savantbuild.dep.io.FileTools;
import org.savantbuild.dep.io.IOTools;
import org.savantbuild.dep.io.MD5;
import org.savantbuild.dep.io.MD5Exception;
import org.savantbuild.dep.io.PermanentIOException;
import org.savantbuild.dep.io.TemporaryIOException;
import org.savantbuild.dep.net.NetTools;
import org.savantbuild.dep.net.SubVersion;
import org.savantbuild.dep.util.ErrorList;
import org.savantbuild.dep.workflow.PublishWorkflow;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * This is an implementation of the Process that uses the SVNKit SubVersion library to fetch and publish
 * artifacts from/to a SubVersion repository using SubVersion export and import commands.
 *
 * @author Brian Pontarelli
 */
public class SVNProcess implements Process {
  private final static Logger logger = Logger.getLogger(SVNProcess.class.getName());

  private final String password;

  private final String repository;

  private final String username;

  public SVNProcess(String repository, String username, String password) {
    this.repository = repository;
    this.username = username;
    this.password = password;

    ErrorList errors = new ErrorList();
    if (repository == null) {
      errors.addError("The [repository] attribute is required for the [svn] workflow process");
    }

    if ((username != null && password == null) || (username == null && password != null)) {
      errors.addError("You must specify both the [username] and [password] attributes to turn on authentication " +
          "for the [svn] workflow process.");
    }

    if (!errors.isEmpty()) {
      throw new DependencyException(errors);
    }
  }

  /**
   * Not implemented yet.
   */
  @Override
  public boolean delete(Artifact artifact, String item) throws DependencyException {
    throw new DependencyException("The [svn] process doesn't allow deleting yet.");
  }

  /**
   * Not implemented yet.
   */
  @Override
  public void deleteIntegrationBuilds(Artifact artifact) {
    throw new DependencyException("The [svn] process doesn't allow deleting of integration builds yet.");
  }

  /**
   * I'm totally punting on SVN fetching of integration and latest versions. This could be a long and messy method that
   * is hard to test. I'll implement this later.
   *
   * @param artifact The artifact.
   * @return Always null.
   */
  public String determineVersion(Artifact artifact) {
    return null;
  }

  /**
   * Fetches the artifact from the SubVersion repository by performing an export to a temporary file and checking the
   * MD5 sum if it exists.
   *
   * @param artifact               The artifact to fetch and store.
   * @param item                   The item to fetch.
   * @param publishWorkflow The publish workflow used to publish the artifact after it has been successfully
   *                               fetched.
   * @return The File if downloaded and stored.
   * @throws DependencyException   If there was an unrecoverable error during SVN fetch.
   * @throws DoesNotExistException If the file doesn't exist in the SVN repository.
   */
  @Override
  public Path fetch(Artifact artifact, String item, PublishWorkflow publishWorkflow)
      throws TemporaryIOException, PermanentIOException, DoesNotExistException {
    URI md5URI = NetTools.build(artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version, item + ".md5");
    Path md5File = export(md5URI, null);
    MD5 md5 = IOTools.parseMD5(md5File);

    URI itemURI = NetTools.build(repository, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version, item);
    Path itemFile = export(itemURI, md5);
    if (itemFile == null) {
      throw new DoesNotExistException();
    }

    logger.info("Downloaded from SubVersion at [" + itemURI + "]");

    publishWorkflow.publish(artifact, item + ".md5", md5File);
    return publishWorkflow.publish(artifact, item, itemFile);
  }

  /**
   * Publishes the given artifact item into the SubVersion repository.
   *
   * @param artifact     The artifact that the item might be associated with.
   * @param item         The name of the item to publish.
   * @param artifactFile The file that is the item.
   * @return Always null.
   * @throws DependencyException If the publish fails.
   */
  @Override
  public Path publish(Artifact artifact, String item, Path artifactFile) throws DependencyException {
    try (SubVersion svn = new SubVersion(repository, username, password)) {
      if (!svn.isExists()) {
        throw new DependencyException("Repository URL [" + repository + "] doesn't exist on the SubVersion server");
      } else if (svn.isFile()) {
        throw new DependencyException("Repository URL [" + repository + "] points to a file and must point to a directory");
      }

      URI uri = NetTools.build(artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version, item);
      svn.doImport(uri.toString(), artifactFile);
      logger.info("Published to SubVersion at [" + repository + "/" + uri + "]");
      return null;
    }
  }

  private Path export(final URI uri, final MD5 md5) {
    return IOTools.protectIO(() -> {
      File file = File.createTempFile("savant-svn-process", "export");
      file.deleteOnExit();

      Path path = file.toPath();
      try (SubVersion svn = new SubVersion(repository, username, password)) {
        if (!svn.isExists()) {
          throw new DependencyException("Repository [" + repository + "] doesn't exist on the SubVersion server");
        } else if (svn.isFile()) {
          throw new DependencyException("Repository [" + repository + "] points to a file and must point to a directory");
        }

        if (!svn.doExport(uri.toString(), path)) {
          throw new DoesNotExistException();
        }

        if (md5 != null && md5.bytes != null) {
          MD5 exportedMD5 = FileTools.md5(path);
          if (!Arrays.equals(exportedMD5.bytes, md5.bytes)) {
            throw new MD5Exception("MD5 mismatch.");
          }
        }

        return path;
      }
    });
  }
}
