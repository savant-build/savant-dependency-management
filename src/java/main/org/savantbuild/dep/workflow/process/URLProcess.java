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
import org.savantbuild.dep.io.IOTools;
import org.savantbuild.dep.io.MD5;
import org.savantbuild.dep.io.PermanentIOException;
import org.savantbuild.dep.io.TemporaryIOException;
import org.savantbuild.dep.net.NetTools;
import org.savantbuild.dep.util.ErrorList;
import org.savantbuild.dep.workflow.PublishWorkflow;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;

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
  private final static Pattern HTML = Pattern.compile("a href=\"([^/]+?)/?\"");

  private final static Logger logger = Logger.getLogger(URLProcess.class.getName());

  private final String password;

  private final String url;

  private final String username;

  public URLProcess(String url, String username, String password) {
    this.url = url;
    this.username = username;
    this.password = password;

    ErrorList errors = new ErrorList();
    if (url == null) {
      errors.addError("The [url] attribute is required for the [url] workflow process");
    }

    if ((username != null && password == null) || (username == null && password != null)) {
      errors.addError("You must specify both the [username] and [password] attributes to turn on authentication " +
          "for the [url] workflow process.");
    }

    if (!errors.isEmpty()) {
      throw new DependencyException(errors);
    }
  }

  /**
   * Throws an exception. This isn't supported yet.
   */
  @Override
  public void deleteIntegrationBuilds(Artifact artifact) {
    throw new DependencyException("The [url] process doesn't allow publishing yet.");
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
  public Path fetch(Artifact artifact, String item, PublishWorkflow publishWorkflow)
      throws ProcessFailureException {

    URI md5URI = NetTools.build(url, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), item + ".md5");
    Path md5File = NetTools.downloadToPath(md5URI, username, password, null);
    MD5 md5 = IOTools.parseMD5(md5File);

    URI itemURI = NetTools.build(url, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), item);
    Path itemFile = NetTools.downloadToPath(itemURI, username, password, md5);
    if (itemFile == null) {
      throw new DoesNotExistException("Artifact item doesn't exist [" + itemURI + "]");
    }

    logger.info("Downloaded from [" + itemURI + "]");

    publishWorkflow.publish(artifact, item + ".md5", md5File);
    return publishWorkflow.publish(artifact, item, itemFile);
  }

  /**
   * Throws an exception. This isn't supported yet.
   */
  @Override
  public Path publish(Artifact artifact, String item, Path file) throws DependencyException {
    throw new DependencyException("The [url] process doesn't allow publishing yet.");
  }

  private Set<String> parseNames(URI uri) {
    try {
      String result = NetTools.downloadToString(uri, username, password);
      Set<String> names = new HashSet<>();
      if (result.contains("<html")) {
        Matcher matcher = HTML.matcher(result);
        while (matcher.find()) {
          try {
            names.add(URLDecoder.decode(matcher.group(1), "UTF-8"));
          } catch (UnsupportedEncodingException e) {
            throw new DependencyException("Unable to decode version URLs inside the HTML returned from the remote " +
                "Savant repository [" + uri.toString() + "]", e);
          }
        }
      } else {
        names.addAll(asList(result.split("\n")));
      }

      return names;
    } catch (DoesNotExistException e) {
      return null;
    } catch (TemporaryIOException e) {
      return null;
    } catch (PermanentIOException e) {
      throw new DependencyException(e);
    }
  }
}
