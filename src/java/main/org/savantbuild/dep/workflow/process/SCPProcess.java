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
import org.savantbuild.dep.io.IOTools;
import org.savantbuild.dep.net.SCP;
import org.savantbuild.dep.net.SSHOptions;
import org.savantbuild.dep.util.ErrorList;
import org.savantbuild.dep.workflow.PublishWorkflow;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * This is an implementation of the Process that uses the JSCH to publish artifacts to a server using SSH and
 * SCP.
 *
 * @author Brian Pontarelli
 */
public class SCPProcess implements Process {
  private final static Logger logger = Logger.getLogger(SCPProcess.class.getName());

  private final String location;

  private final SSHOptions options;

  public SCPProcess(SSHOptions options, String location) {
    this.options = options;
    this.location = location;

    ErrorList errors = new ErrorList();
    if (this.options.server == null) {
      errors.addError("The [server] attribute is required for the [scp] workflow process");
    }

    if (location == null) {
      errors.addError("The [location] attribute is required for the [scp] workflow process");
    }

    if ((this.options.username != null && this.options.password == null) || (this.options.username == null && this.options.password != null)) {
      errors.addError("You must specify both the [username] and [password] attributes to turn on authentication " +
          "for the [scp] workflow process.");
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
    throw new DependencyException("The [scp] process doesn't allow deleting yet.");
  }

  /**
   * Not implemented yet.
   */
  @Override
  public void deleteIntegrationBuilds(Artifact artifact) {
    throw new DependencyException("The [scp] process doesn't allow deleting of integration builds yet.");
  }

  /**
   * Not supported right now.
   */
  public String determineVersion(Artifact artifact) {
    throw new DependencyException("The [scp] workflow process doesn't support fetching at this time.");
  }

  /**
   * Not supported right now.
   */
  @Override
  public Path fetch(Artifact artifact, String item, PublishWorkflow publishWorkflow) {
    throw new DependencyException("The [scp] workflow process doesn't support fetching at this time.");
  }

  /**
   * Publishes the given artifact item into the SubVersion repository.
   *
   * @param artifact     The artifact that the item might be associated with.
   * @param item         The name of the item to publish.
   * @param artifactFile The artifact file.
   * @return Always null.
   * @throws DependencyException If the publish fails.
   */
  @Override
  public Path publish(Artifact artifact, String item, Path artifactFile) throws DependencyException {
    String path = String.join("/", location, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version, item);
    upload(path, artifactFile);
    logger.info("Published via SCP to [" + options.server + ":" + options.port + location + "/" + path + "]");
    return null;
  }

  private void upload(String path, Path file) {
    IOTools.protectIO(() -> {
      SCP scp = new SCP(options);
      scp.upload(file, path);
      return null;
    });
  }
}
