/*
 * Copyright (c) 2001-2006, Inversoft, All Rights Reserved
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
package org.savantbuild.dep.net;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.savantbuild.dep.io.FileTools;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import static org.testng.Assert.*;

/**
 * <p>
 * Modified from previous version to use a mock subversion repository
 * </p>
 *
 * @author Brian Pontarelli and James Humphrey
 */
public class SubVersionTest {


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
  public void badRepository() {
    File badRepositoryPath = new File(mockSvnRepositoryRoot, "bad");
    String badSvnUrl = repositoryPathToString(badRepositoryPath);
    SubVersion svn = new SubVersion(badSvnUrl);
    assertFalse(svn.isExists());
  }

  @Test(enabled = true)
  public void importFile() throws IOException {
    SubVersion svn = new SubVersion(repositoryPathToString(mockSvnRepositoryTrunk));
    assertTrue(svn.isExists());
    assertTrue(svn.isDirectory());
    assertFalse(svn.isFile());

    // Make a temp file
    File temp = File.createTempFile("foo", "bar");
    temp.deleteOnExit();
    FileTools.write(temp, "Hello world");

    // Import it
    long now = System.currentTimeMillis();
    svn.doImport("/svn-test/import" + now + "/file" + now, temp);
  }

  /**
   * todo this needs to be fixed.  disabled testing for now
   *
   * @throws java.io.IOException on exception
   */
  @Test(enabled = true)
  public void exportNested() throws IOException {
    try {
      throw new RuntimeException();
    } catch (RuntimeException e) {
      System.out.println("*****RE-ENABLE THIS*****\n\tSubVersionTest.exportNested needs fixing.  " +
        "Because we're using mock repositories, we'll have to use SVNKit to add files to test nested exports\n*****RE-ENABLE THIS*****");
    }
//    SubVersion svn = new SubVersion(repositoryPathToString(mockSvnRepositoryTrunk));
//    assertTrue(svn.isExists());
//    assertTrue(svn.isDirectory());
//    assertFalse(svn.isFile());
//
//    // Make a temp file
//    File temp = File.createTempFile("foo", "bar");
//    temp.deleteOnExit();
//
//    // Export it
//    svn.doExport("nested/file", temp);
//    assertTrue(temp.exists());
//    String content = FileTools.read(temp);
//    assertEquals("Testing nested export", content.trim());
  }

  @Test(enabled = true)
  public void exportDir() throws IOException {
    SubVersion svn = new SubVersion(repositoryPathToString(mockSvnRepositoryRoot));
    assertTrue(svn.isExists());
    assertTrue(svn.isDirectory());
    assertFalse(svn.isFile());

    // Export it
    File dir = new File("target/test/svn-export");
    FileTools.prune(dir);
    svn.doExport("trunk", dir);
    assertTrue(dir.isDirectory());
  }

  @Test(enabled = true)
  public void checkoutDir() throws IOException {
    SubVersion svn = new SubVersion(repositoryPathToString(mockSvnRepositoryRoot));
    assertTrue(svn.isExists());
    assertTrue(svn.isDirectory());
    assertFalse(svn.isFile());

    // Export it
    File dir = new File("target/test/svn/checkout");
    FileTools.prune(dir);
    svn.doCheckout("trunk", dir);
    assertTrue(dir.isDirectory());
    assertTrue(new File(dir, ".svn").isDirectory());
    FileTools.prune(dir);
  }
}
