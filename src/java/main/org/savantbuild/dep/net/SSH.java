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
package org.savantbuild.dep.net;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class uses teh JSCH library to connect into the SSH protocol and allow remote execution of scripts. Since this
 * might use a password or a passphrase, it will attempt to leverage the JDK 1.6 Console object via reflection. However,
 * if that class doesn't exist than it will use stdin and stdout for asking and retrieving passwords.
 *
 * @author Brian Pontarelli
 */
public class SSH {
  private SSHOptions options;

  /**
   * Constructs a new SSH wrapper that will connect using the given SSH options.
   *
   * @param options The SSH options.
   */
  public SSH(SSHOptions options) {
    this.options = options;
  }

  /**
   * Executes the given command via SSH.
   *
   * @param command The command to execute.
   * @return The result from the remote server.
   */
  public String execute(String command) throws JSchException {
    JSch jsch = new JSch();

    // Add the identity if it exists
    if (options.identity != null && new File(options.identity).isFile()) {
      jsch.addIdentity(options.identity);
    }

    // Add the known hosts if it exists
    if (options.knownHosts != null && new File(options.knownHosts).isFile()) {
      jsch.setKnownHosts(options.knownHosts);
    }

    Session session = jsch.getSession(options.username, options.server, options.port);
    session.setUserInfo(new BaseUserInfo(options.password, options.passphrase, options.trustUnknownHosts));
    session.connect();

    final ChannelExec exec = (ChannelExec) session.openChannel("exec");
    exec.setCommand(command);

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    exec.setOutputStream(output);
    exec.setExtOutputStream(output);

    // Setup the error stream
    ByteArrayOutputStream error = new ByteArrayOutputStream();
    exec.setErrStream(error);

    exec.connect();

    // wait for it to finish
    final AtomicBoolean finished = new AtomicBoolean(false);
    Thread thread =
        new Thread() {
          public void run() {
            while (!exec.isEOF()) {
              try {
                if (finished.get()) {
                  return;
                }

                sleep(500);
              } catch (Exception e) {
                // ignored
              }
            }
          }
        };

    thread.start();
    try {
      thread.join(0);
    } catch (InterruptedException e) {
      throw new JSchException("Thread was interrupted", e);
    }

    finished.set(true);

    if (thread.isAlive()) {
      // ran out of time
      throw new JSchException("SSH command [" + command + "] timed out.");
    }

    int code = exec.getExitStatus();
    if (code != 0) {
      throw new JSchException("Unable to execute SSH command [" + command + "] due to [ " + error.toString() + "]");
    }

    return output.toString();
  }
}
