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
import org.savantbuild.dep.DependencyException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class uses the JSCH library to connect into the SSH protocol and allow remote execution of scripts. Since this
 * might use a password or a passphrase, it will attempt to leverage the JDK 1.6 Console object via reflection. However,
 * if that class doesn't exist than it will use stdin and stdout for asking and retrieving passwords.
 *
 * @author Brian Pontarelli
 */
public class SCP {
  private SSHOptions options;

  /**
   * Constructs a new SCP wrapper that will connect using the given options.
   *
   * @param options The SSH options.
   */
  public SCP(SSHOptions options) {
    this.options = options;
  }

  /**
   * Uploads the given file to the server.
   *
   * @param from The file to upload.
   * @param to   The location on the remote server to upload to.
   * @throws DependencyException If the SCP command failed or any errors were found.
   */
  public void upload(Path from, String to) throws DependencyException {
    try {
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

      ChannelExec exec = (ChannelExec) session.openChannel("exec");
      exec.setCommand("scp -p -t " + to);

      InputStream is = exec.getInputStream();
      OutputStream os = exec.getOutputStream();
      exec.connect();

      checkAck(is);

      // send "C0644 filesize filename", where filename should not include '/'
      String command = "C0644 " + Files.size(from) + " " + from.getFileName().toString() + "\n";
      os.write(command.getBytes());
      os.flush();
      checkAck(is);

      // Send contents of lfile
      Files.copy(from, os);

      // send '\0'
      os.write(0);
      os.flush();
      checkAck(is);
      os.close();

      exec.disconnect();
      session.disconnect();
    } catch (JSchException | IOException e) {
      throw new DependencyException(e);
    }
  }

  private void checkAck(InputStream is) throws IOException {
    int b = is.read();
    // b may be 0 for success,
    //          1 for error,
    //          2 for fatal error,
    //          -1
    if (b == 0) {
      return;
    }

    if (b == -1) {
      throw new IOException("Invalid SCP acknowledgement response code [-1]");
    }

    if (b == 1 || b == 2) {
      StringBuilder build = new StringBuilder();
      int c;
      do {
        c = is.read();
        build.append((char) c);
      } while (c != '\n');

      throw new IOException(build.toString());
    }
  }
}
