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
import org.savantbuild.dep.net.SSHOptions;
import org.savantbuild.dep.workflow.PublishWorkflow;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.SCPClient;
import ch.ethz.ssh2.Session;

/**
 * This is an implementation of the Process that uses the Ganymed to publish artifacts to a server using SSH and SCP.
 *
 * @author Brian Pontarelli
 */
public class SCPProcess implements Process {
  private final static Logger logger = Logger.getLogger(SCPProcess.class.getName());

  private final String location;

  private final SSHOptions options;

  private final String server;

  /**
   * Constructs the SSHProcess.
   *
   * @param options  The SSHOptions, which must have a server setting.
   * @param location The location to SCP to.
   * @throws NullPointerException If any of the required options are null.
   */
  public SCPProcess(String server, String location, SSHOptions options) throws NullPointerException {
    Objects.requireNonNull(server, "The [server] attribute is required for the [scp] workflow process");
    Objects.requireNonNull(location, "The [location] attribute is required for the [scp] workflow process");
    Objects.requireNonNull(options, "The [options] attribute is required for the [scp] workflow process");
    if (options != null) {
      if (options.username != null) {
        Objects.requireNonNull(options.username, "You must specify both the [username] attributes for the [scp] workflow process.");
      }
    }

    this.server = server;
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
    String path = String.join("/", location, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString());

    try {
      Connection connection = new Connection(server, options.port);
      connection.connect();
      if (options.password != null) {
        connection.authenticateWithPassword(options.username, options.password);
      } else {
        connection.authenticateWithPublicKey(options.username, options.identity, options.passphrase);
      }

      // Make the directories
      Session session = connection.openSession();
      session.execCommand("mkdir -p " + path);
      session.close();

      // SCP the file
      SCPClient client = new SCPClient(connection);
      client.put(artifactFile.toAbsolutePath().toString(), item, path, "0444");

      connection.close();
    } catch (IOException e) {
      throw new ProcessFailureException(e);
    }

    logger.info("Published via SCP to [" + server + ":" + location + "/" + path + "]");
    return null;
  }
}
