/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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
package org.savantbuild.dep.workflow.process;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.savantbuild.dep.PathTools;
import org.savantbuild.dep.domain.ResolvableItem;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.lang.RuntimeTools;
import org.savantbuild.lang.RuntimeTools.ProcessResult;
import org.savantbuild.net.NetTools;
import org.savantbuild.output.Output;
import org.savantbuild.security.MD5;
import org.savantbuild.security.MD5Exception;

/**
 * This is an implementation of the Process that uses the SVNKit SubVersion library to fetch and publish artifacts
 * from/to a SubVersion repository using SubVersion export and import commands.
 *
 * @author Brian Pontarelli
 */
public class SVNProcess implements Process {
  public final Output output;

  public final String password;

  public final String repository;

  public final String username;

  public SVNProcess(Output output, String repository, String username, String password) {
    this.output = output;
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
   * Fetches the artifact from the SubVersion repository by performing an export to a temporary file and checking the
   * MD5 sum if it exists.
   *
   * @param item            The item to fetch.
   * @param publishWorkflow The publish workflow used to publish the artifact after it has been successfully fetched.
   * @return The File or null if it doesn't exist.
   * @throws ProcessFailureException If the SVN fetch failed.
   */
  @Override
  public Path fetch(ResolvableItem item, PublishWorkflow publishWorkflow)
      throws ProcessFailureException {
    try {
      Path md5File = PathTools.createTempPath("savant-svn-process", "export", true);
      URI md5URI = NetTools.build(repository, item.group.replace('.', '/'), item.project, item.version, item.item + ".md5");
      if (!export(md5URI, md5File)) {
        return null;
      }

      MD5 md5;
      try {
        md5 = MD5.load(md5File);
      } catch (IOException e) {
        Files.delete(md5File);
        throw new ProcessFailureException(item, e);
      }

      Path itemFile = PathTools.createTempPath("savant-svn-process", "export", true);
      URI itemURI = NetTools.build(repository, item.group.replace('.', '/'), item.project, item.version, item.item);
      output.debugln("      - Download [" + itemURI + "]");
      if (!export(itemURI, itemFile)) {
        output.debugln("      - Not found [" + itemURI + "]");
        return null;
      }

      MD5 itemMD5 = MD5.forPath(itemFile);
      if (!itemMD5.equals(md5)) {
        throw new MD5Exception("MD5 mismatch when fetching item from [" + itemURI + "]");
      }

      output.infoln("Downloaded from SubVersion at [%s]", itemURI);

      ResolvableItem md5Item = new ResolvableItem(item, item.item + ".md5");
      md5File = publishWorkflow.publish(md5Item, md5File);
      try {
        itemFile = publishWorkflow.publish(item, itemFile);
      } catch (ProcessFailureException e) {
        Files.delete(md5File);
        throw new ProcessFailureException(item, e);
      }

      return itemFile;
    } catch (IOException | InterruptedException e) {
      throw new ProcessFailureException(item, e);
    }
  }

  /**
   * Publishes the given artifact item into the SubVersion repository.
   *
   * @param item     The item to publish.
   * @param itemFile The file that is the item.
   * @return Always null.
   * @throws ProcessFailureException If the publish fails.
   */
  @Override
  public Path publish(ResolvableItem item, Path itemFile) throws ProcessFailureException {
    try {
      URI uri = NetTools.build(repository, item.group.replace('.', '/'), item.project, item.version, item.item);
      if (!imprt(uri, itemFile)) {
        throw new ProcessFailureException("Unable to publish artifact item [" + item + "] to [" + uri + "]");
      }

      output.infoln("Published to SubVersion at [%s]", uri);
      return null;
    } catch (IOException | InterruptedException e) {
      throw new ProcessFailureException(item, e);
    }
  }

  @Override
  public String toString() {
    return "SVN(" + repository + ")";
  }

  private boolean export(URI uri, Path file) throws IOException, InterruptedException {
    ProcessResult result;
    if (username != null) {
      result = RuntimeTools.exec("svn", "export", "--force", "--non-interactive", "--no-auth-cache", "--username", username, "--password", password, uri.toString(), file.toAbsolutePath().toString());
    } else {
      result = RuntimeTools.exec("svn", "export", "--force", "--non-interactive", "--no-auth-cache", uri.toString(), file.toAbsolutePath().toString());
    }

    output.debugln(result.output);

    return result.exitCode == 0;
  }

  private boolean imprt(URI uri, Path file) throws IOException, InterruptedException {
    ProcessResult result;
    if (username != null) {
      result = RuntimeTools.exec("svn", "import", "--non-interactive", "--no-auth-cache", "-m", "Published artifact", "--username", username, "--password", password, file.toAbsolutePath().toString(), uri.toString());
    } else {
      result = RuntimeTools.exec("svn", "import", "--non-interactive", "--no-auth-cache", "-m", "Published artifact", file.toAbsolutePath().toString(), uri.toString());
    }

    output.debugln(result.output);

    return result.exitCode == 0;
  }
}
