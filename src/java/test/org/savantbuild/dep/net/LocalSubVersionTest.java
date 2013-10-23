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
package org.savantbuild.net;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.savantbuild.io.FileTools;
import org.savantbuild.net.LocalSubVersion.StatusHandler;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import static org.testng.Assert.*;

/**
 * <p>
 * Modified from previous version to use a mock svn repository and project
 *
 * This class creates a
 * </p>
 *
 * @author Brian Pontarelli and James Humphrey
 */
public class LocalSubVersionTest {


  public static File mockSvnRepositoryRoot = new File("target/mock-svn-repository");
  public static File mockSvnRepositoryTrunk = new File(mockSvnRepositoryRoot, "trunk");
  public static File mockSvnRepositoryBranches = new File(mockSvnRepositoryRoot, "branches");
  public static File mockSvnRepositoryBranch_1_0 = new File(mockSvnRepositoryBranches, "1.0");
  public static File mockSvnRepositoryTags = new File(mockSvnRepositoryRoot, "tags");
  public static File mockSvnRepositoryTag_1_0_1 = new File(mockSvnRepositoryTags, "1.0.1");
  public static File mockSvnProject = new File("target/mock-svn-project");

  @BeforeClass
  public void initMockRepository() {
    try {
      FileUtils.deleteDirectory(mockSvnRepositoryRoot);
      FileUtils.deleteDirectory(mockSvnProject);
      SubVersion.createRepository(mockSvnRepositoryRoot);
      SubVersion subVersion = new SubVersion(repositoryPathToString(mockSvnRepositoryRoot));
      subVersion.mkdir(mockSvnRepositoryTrunk);
      subVersion.mkdir(mockSvnRepositoryBranches);
      subVersion.mkdir(mockSvnRepositoryBranch_1_0);
      subVersion.mkdir(mockSvnRepositoryTags);
      subVersion.mkdir(mockSvnRepositoryTag_1_0_1);
      subVersion.doCheckout("trunk", mockSvnProject);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail("You must have subversion installed on your system to execute this unit test");
    }

  }

  /**
   * Helper method to convert a file path to a SVNURL.toString()
   *
   * @param repositoryPath the repository path
   * @return string representing the svn url
   */
  public String repositoryPathToString(File repositoryPath) {
    try {
      return SVNURL.fromFile(repositoryPath).toString();
    } catch (SVNException e) {
      Assert.fail();
    }
    return null;
  }

  @Test(enabled = true)
  public void url() throws SVNException {
    String url = LocalSubVersion.getProjectURL(mockSvnProject);
    assertEquals(url, repositoryPathToString(mockSvnRepositoryTrunk));
  }

  @Test(enabled = true)
  public void base() {
    String url = LocalSubVersion.getProjectBaseURL(mockSvnProject);
    assertEquals(url, repositoryPathToString(mockSvnRepositoryRoot));
  }

  @Test(enabled = true)
  public void location() {
    assertEquals(LocalSubVersion.determineLocation(repositoryPathToString(mockSvnRepositoryTrunk)), "trunk");
    assertEquals(LocalSubVersion.determineLocation(repositoryPathToString(mockSvnRepositoryBranch_1_0)), "branch");
    assertEquals(LocalSubVersion.determineLocation(repositoryPathToString(mockSvnRepositoryTag_1_0_1)), "tag");

    try {
      assertEquals(LocalSubVersion.determineLocation(repositoryPathToString(mockSvnRepositoryRoot)), "tag");
      fail("Should have failed");
    } catch (Exception e) {
      // Expected
    }
  }

  @Test(enabled = true)
  public void branch() {
    assertEquals(LocalSubVersion.determineBranch(repositoryPathToString(mockSvnRepositoryBranch_1_0)), "1.0");
    assertNull(LocalSubVersion.determineBranch(repositoryPathToString(mockSvnRepositoryTag_1_0_1)));
  }

  @Test(enabled = true)
  public void root() {
    assertEquals(LocalSubVersion.determineRoot(repositoryPathToString(mockSvnRepositoryBranch_1_0)), repositoryPathToString(mockSvnRepositoryRoot));
    assertEquals(LocalSubVersion.determineRoot(repositoryPathToString(mockSvnRepositoryTag_1_0_1)), repositoryPathToString(mockSvnRepositoryRoot));
  }

  @Test(enabled = true)
  public void status() throws IOException {
    FileTools.write(new File(mockSvnProject, "svn-changed.txt"), "" + System.currentTimeMillis());

    final ThreadLocal<File> holder = new ThreadLocal<File>();
    LocalSubVersion.doStatus(mockSvnProject, new StatusHandler() {
      @Override
      public void handle(File file) {
        if (file.getName().equals("svn-changed.txt")) {
          holder.set(file);
        }
      }
    });

    assertNotNull(holder.get());
  }
}
