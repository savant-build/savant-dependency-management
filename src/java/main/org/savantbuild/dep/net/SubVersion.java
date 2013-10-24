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

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class is a wrapper around the SVNKit SubVersion client and provides a simpler interface for dealing with
 * repositories and operations.
 *
 * @author Brian Pontarelli
 */
public class SubVersion implements Closeable {
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

  private SVNClientManager clientManager;

  private SVNNodeKind nodeKind;

  private SVNRepository repository;

  private SVNURL svnURL;

  /**
   * Constructs a SubVersion wrapper around the SVNKit SubVersion client. This uses the repository URL and credentials
   * that have been cached by other clients or anonymous authentication.
   *
   * @param repository The SubVersion repository URL.
   * @throws DependencyException If the creation of the SubVersion client failed for any reason.
   */
  public SubVersion(String repository) throws SVNException {
    this(repository, null, null);
  }

  /**
   * Constructs a SubVersion wrapper around the SVNKit SubVersion client. This uses the repository URL and credentials
   * given (if supplied).
   *
   * @param repository The SubVersion repository URL.
   * @param username   (Optional) The username used to connect to the repository.
   * @param password   (Optional) The password used to connect to the repository.
   * @throws SVNException If the creation of the SubVersion client failed for any reason.
   */
  public SubVersion(String repository, String username, String password) throws SVNException {
    DefaultSVNOptions options = SVNWCUtil.createDefaultOptions(true);
    options.setAuthStorageEnabled(false);
    this.svnURL = SVNURL.parseURIEncoded(repository);
    this.clientManager = SVNClientManager.newInstance(options, username, password);

    // Check the URL is correct
    this.repository = clientManager.createRepository(svnURL, false);
    this.nodeKind = this.repository.checkPath("", -1);
  }

  /**
   * Creates a svn repository
   *
   * @param path the path to the repository
   * @return SVNURL
   */
  public static SVNURL createRepository(Path path) throws SVNException {
    SVNClientManager clientManager = SVNClientManager.newInstance();
    SVNAdminClient adminClient = clientManager.getAdminClient();
    return adminClient.doCreateRepository(path.toFile(), null, true, true);
  }

  /**
   * Cleans up all the resources used by the SVNKit SubVersion client.
   */
  public void close() {
    this.clientManager.dispose();
    this.repository.closeSession();
  }

  /**
   * Checks out the file at the given repository path into the given file. This verifies that the path is actually a
   * valid file in the repository.
   * <p/>
   * If the path in the repository points to a directory, this recursively checks out that directory to the given
   * location. If it points to a file, it checks out the single file to the given file.
   *
   * @param path The path must be relative to the repository location given in the constructor. This path must reference
   *             a valid repository file or directory.
   * @param file The file or directory where the checkout is placed.
   * @throws SVNException If the checkout fails or the SVN URL doesn't exist and could not be checked out.
   * @throws IOException  If the file is not a directory or there were problems writing to the file system.
   */
  public void doCheckout(String path, Path file) throws SVNException, IOException {
    if (Files.isRegularFile(file)) {
      throw new IOException("Unable to checkout path [" + path + "] in repository [" + this.svnURL.toString() +
          "] to the location [" + file.toAbsolutePath() + "] because the file given is a plain file and should be a directory.");
    }

    // Create the directories if necessary
    Path parent = file.getParent();
    if (!Files.isDirectory(parent)) {
      Files.createDirectories(parent);
    }

    // Get the file
    SVNUpdateClient client = clientManager.getUpdateClient();
    client.doCheckout(svnURL.appendPath(path, true), file.toFile(), SVNRevision.HEAD, SVNRevision.HEAD, SVNDepth.INFINITY, false);
  }

  /**
   * Performs a copy within the current repository of this SubVersion object from src to dest. This is primarily used
   * for branching and tagging from trunk or branches. Therefore, you should probably construct the SubVersion to be the
   * root of the project (above trunk).
   *
   * @param src     The source path in the repository.
   * @param dest    The destination path in the repository.
   * @param message A commit message.
   */
  public void doCopy(String src, String dest, String message) throws SVNException {
    SVNCopyClient copyClient = clientManager.getCopyClient();
    SVNURL srcURL = svnURL.appendPath(src, true);
    SVNURL destURL = svnURL.appendPath(dest, true);
    copyClient.doCopy(new SVNCopySource[]{new SVNCopySource(SVNRevision.UNDEFINED, SVNRevision.UNDEFINED, srcURL)},
        destURL, false, true, true, message, null);
  }

  /**
   * Exports the file at the given repository path into the given file. This verifies that the path is actually a valid
   * file in the repository.
   * <p/>
   * If the path in the repository points to a directory, this recursively exports that directory to the given location.
   * If it points to a file, it exports the single file to the given file.
   *
   * @param path The path must be relative to the repository location given in the constructor. This path must reference
   *             a valid repository file or directory.
   * @param file The file or directory where the export is placed.
   * @throws SVNException If the path is not a file or directory or there is a mismatch between the path and file
   *                      given.
   * @throws IOException  If there was an error writing to the file system.
   */
  public void doExport(String path, Path file) throws SVNException, IOException {
    SVNNodeKind nodeKind = this.repository.checkPath(path, -1);

    if (nodeKind == SVNNodeKind.FILE) {
      if (Files.isDirectory(file)) {
        throw new IOException("Unable to export path [" + path + "] in repository [" + this.svnURL.toString() +
            "] to the location [" + file.toAbsolutePath() + "] because the file given is a directory and should be a plain file.");
      }

      // Create the directories if necessary
      if (Files.isDirectory(file.getParent())) {
        Files.createDirectories(file.getParent());
      }

      // Create the export target file
      if (!Files.isRegularFile(file)) {
        Files.createFile(file);
      }

      // Get the file
      SVNUpdateClient client = clientManager.getUpdateClient();
      client.doExport(svnURL.appendPath(path, true), file.toFile(), SVNRevision.HEAD, SVNRevision.HEAD, "\n", true, SVNDepth.INFINITY);
    } else {
      if (Files.isRegularFile(file)) {
        throw new IOException("Unable to export path [" + path + "] in repository [" + this.svnURL.toString() +
            "] to the location [" + file.toAbsolutePath() + "] because the file given is a plain file and should be a directory.");
      }

      // Create the directories if necessary
      if (!Files.isDirectory(file.getParent())) {
        Files.createDirectories(file.getParent());
      }

      // Get the file
      SVNUpdateClient client = clientManager.getUpdateClient();
      client.doExport(svnURL.appendPath(path, true), file.toFile(), SVNRevision.HEAD, SVNRevision.HEAD, "\n", true, SVNDepth.INFINITY);
    }
  }

  /**
   * Imports the given file into the given path that is appended to the repository URL.
   *
   * @param path The path which can be relative or absolute but is appended to the repository URL setup in the
   *             constructor.
   * @param file The file to import.
   */
  public void doImport(String path, Path file) throws SVNException {
    SVNCommitClient client = clientManager.getCommitClient();
    SVNURL url = svnURL.appendPath(path, false);
    client.doImport(file.toFile(), url, "Savant publish", null, true, true, SVNDepth.INFINITY);
  }

  /**
   * @return True if the repository URL points to a directory.
   */
  public boolean isDirectory() {
    return nodeKind == SVNNodeKind.DIR;
  }

  /**
   * @return True if the repository URL given in the constructor exists on the server.
   */
  public boolean isExists() {
    return nodeKind != SVNNodeKind.NONE;
  }

  /**
   * @return True if the repository URL points to a file.
   */
  public boolean isFile() {
    return nodeKind == SVNNodeKind.NONE;
  }

  /**
   * Makes a svn directory
   *
   * @param repositoryPath the repository path
   * @return SVNCommitInfo
   */
  public SVNCommitInfo mkdir(File repositoryPath) throws SVNException {
    SVNClientManager clientManager = SVNClientManager.newInstance();
    SVNCommitClient commitClient = clientManager.getCommitClient();
    SVNURL[] svnurl = new SVNURL[]{SVNURL.fromFile(repositoryPath)};
    return commitClient.doMkDir(svnurl, "Creating svn directory: " + repositoryPath.getAbsolutePath());
  }
}
