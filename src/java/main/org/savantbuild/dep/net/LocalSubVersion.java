/*
 * Copyright (c) 2001-2011, Inversoft, All Rights Reserved
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

import org.savantbuild.dep.DependencyException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

/**
 * This class is a wrapper around the SVNKit SubVersion client and provides a simpler interface for dealing with
 * repositories and operations.
 *
 * @author Brian Pontarelli
 */
public class LocalSubVersion {
  static {
    /*
    * For using over http:// and https://
    */
    DAVRepositoryFactory.setup();

    /*
    * For using over svn:// and svn+xxx://
    */
    SVNRepositoryFactoryImpl.setup();

    /*
    * For using over file:///
    */
    FSRepositoryFactory.setup();
  }

  /**
   * This method determines the branch name.
   *
   * @param url The svn URL.
   * @return The branch name or null if the String isn't a branch.
   */
  public static String determineBranch(String url) {
    if (url.contains("branches")) {
      return url.substring(url.indexOf("branches") + 9);
    }

    return null;
  }

  /**
   * This method determines if the given SVN URL is trunk, a tag or a branch.
   *
   * @param url The svn URL.
   * @return The string "trunk", "tag", or "branch"
   */
  public static String determineLocation(String url) {
    String scmLocation;
    if (url.contains("branches")) {
      scmLocation = "branch";
    } else if (url.contains("trunk")) {
      scmLocation = "trunk";
    } else if (url.contains("tag")) {
      scmLocation = "tag";
    } else {
      throw new DependencyException("Invalid SubVersion URL [" + url + "]. It must use the standard SubVersion trunk, tags, and branches scheme");
    }

    return scmLocation;
  }

  /**
   * This method determines the root SVN URL, minus the trunk, tag, or branch information.
   *
   * @param url The svn URL.
   * @return The root.
   */
  public static String determineRoot(String url) {
    if (url.contains("branches")) {
      url = url.substring(0, url.indexOf("branches") - 1);
    } else if (url.contains("trunk")) {
      url = url.substring(0, url.indexOf("trunk") - 1);
    } else if (url.contains("tags")) {
      url = url.substring(0, url.indexOf("tags") - 1);
    }
    return url;
  }

  /**
   * Provides status information about the local path. This only provides information about modified files.
   *
   * @param path    The local path to get the status for.
   * @param handler The handler.
   */
  public static void doStatus(File path, StatusHandler handler) {
    SVNClientManager clientManager = SVNClientManager.newInstance();
    SVNStatusClient statusClient = clientManager.getStatusClient();
    try {
      statusClient.doStatus(path, SVNRevision.WORKING, SVNDepth.INFINITY, false, false, false, false, new StatusHandlerAdapter(handler), null);
    } catch (SVNException e) {
      throw new DependencyException("Unable to get status information for the path [" + path.getAbsolutePath() + "]", e);
    } finally {
      clientManager.dispose();
    }
  }

  /**
   * @param dir The working directory to get the SVN info for.
   * @return The URL of the working directory's SVN repository minus the trunk, tags or branches part of the URL.
   */
  public static String getProjectBaseURL(File dir) {
    return determineRoot(getProjectURL(dir));
  }

  /**
   * @param dir The working directory to get the SVN info for.
   * @return The URL of the working directory's SVN repository.
   */
  public static String getProjectURL(File dir) {
    try {
      dir = dir.getCanonicalFile();
      if (!dir.isDirectory()) {
        throw new DependencyException("Not a directory [" + dir + "]");
      }

      if (!new File(dir, ".svn").isDirectory()) {
        throw new DependencyException("Not a valid SubVersion working directory [" + dir + "]");
      }

      SVNWCClient client = SVNClientManager.newInstance().getWCClient();
      SVNInfo info = client.doInfo(dir, SVNRevision.UNDEFINED);
      return info.getURL().toDecodedString();
    } catch (Exception e) {
      throw new DependencyException("Unable to get the SVN info for the directory [" + dir.getAbsolutePath() + "]", e);
    }
  }

  /**
   * Handler for SVN status operations.
   */
  public static interface StatusHandler {
    /**
     * Called for modified files.
     *
     * @param file The file.
     */
    void handle(File file);
  }

  private static class StatusHandlerAdapter implements ISVNStatusHandler {
    private final StatusHandler handler;

    public StatusHandlerAdapter(StatusHandler handler) {
      this.handler = handler;
    }

    @Override
    public void handleStatus(SVNStatus status) throws SVNException {
      handler.handle(status.getFile());
    }
  }
}
