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

import com.jcraft.jsch.JSchException;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.net.SCP;
import org.savantbuild.dep.net.SSHOptions;
import org.savantbuild.dep.workflow.PublishWorkflow;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
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

  /**
   * Constructs the SSHProcess.
   *
   * @param options The SSHOptions, which must have a server setting.
   * @param location The location to SCP to.
   * @throws NullPointerException If any of the required options are null.
   */
  public SCPProcess(SSHOptions options, String location) throws NullPointerException {
    Objects.requireNonNull(location, "The [location] attribute is required for the [scp] workflow process");
    Objects.requireNonNull(options, "The [server] attribute is required for the [scp] workflow process");
    if (options != null) {
      Objects.requireNonNull(options.server, "The [server] attribute is required for the [scp] workflow process");
      if (options.username != null || options.password != null) {
        Objects.requireNonNull(options.username, "You must specify both the [username] and [password] attributes to turn on authentication for the [scp] workflow process.");
        Objects.requireNonNull(options.password, "You must specify both the [username] and [password] attributes to turn on authentication for the [scp] workflow process.");
      }
    }

    this.options = options;
    this.location = location;
  }

  /**
   * Not implemented yet.
   */
  @Override
  public void deleteIntegrationBuilds(Artifact artifact) throws ProcessFailureException {
    throw new ProcessFailureException("The [scp] process doesn't allow deleting of integration builds.");
  }

  /**
   * Not supported right now.
   */
  @Override
  public Path fetch(Artifact artifact, String item, PublishWorkflow publishWorkflow) throws ProcessFailureException {
    throw new ProcessFailureException("The [scp] workflow process doesn't support fetching.");
  }

  /**
   * Publishes the given artifact item into the SubVersion repository.
   *
   * @param artifact     The artifact that the item might be associated with.
   * @param item         The name of the item to publish.
   * @param artifactFile The artifact file.
   * @return Always null.
   * @throws ProcessFailureException If the publish fails.
   */
  @Override
  public Path publish(Artifact artifact, String item, Path artifactFile) throws ProcessFailureException {
    String path = String.join("/", location, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), item);
    SCP scp = new SCP(options);
    try {
      scp.upload(artifactFile, path);
    } catch (JSchException | IOException e) {
      throw new ProcessFailureException(e);
    }

    logger.info("Published via SCP to [" + options.server + ":" + options.port + location + "/" + path + "]");
    return null;
  }
}
