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

import org.savantbuild.dep.io.FileTools;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Modified from previous version to use a mock subversion repository
 *
 * @author Brian Pontarelli and James Humphrey
 */
@Test(groups = "unit")
public class SubVersionTest {
  public static Path mockSvnRepositoryRoot = Paths.get("build/mock-svn-repository");

  public static Path mockSvnProject = Paths.get("build/mock-svn-project");

  public static Path mockSvnRepositoryBranches = mockSvnRepositoryRoot.resolve("branches");

  public static Path mockSvnRepositoryBranch_1_0 = mockSvnRepositoryBranches.resolve("1.0");

  public static Path mockSvnRepositoryTags = mockSvnRepositoryRoot.resolve("tags");

  public static Path mockSvnRepositoryTag_1_0_1 = mockSvnRepositoryTags.resolve("1.0.1");

  public static Path mockSvnRepositoryTrunk = mockSvnRepositoryRoot.resolve("trunk");

  @Test(enabled = true)
  public void badRepository() throws SVNException {
    String badSvnUrl = repositoryPathToString(mockSvnRepositoryRoot.resolve("bad"));
    SubVersion svn = new SubVersion(badSvnUrl);
    assertFalse(svn.isExists());
  }

  @Test(enabled = true)
  public void checkoutDir() throws IOException, SVNException {
    SubVersion svn = new SubVersion(repositoryPathToString(mockSvnRepositoryRoot));
    assertTrue(svn.isExists());
    assertTrue(svn.isDirectory());
    assertFalse(svn.isFile());

    // Export it
    Path dir = Paths.get("build/test/svn/checkout");
    FileTools.prune(dir);
    svn.doCheckout("trunk", dir);
    assertTrue(Files.isDirectory(dir));
    assertTrue(Files.isDirectory(dir.resolve(".svn")));
    FileTools.prune(dir);
  }

  @Test(enabled = true)
  public void exportDir() throws IOException, SVNException {
    SubVersion svn = new SubVersion(repositoryPathToString(mockSvnRepositoryRoot));
    assertTrue(svn.isExists());
    assertTrue(svn.isDirectory());
    assertFalse(svn.isFile());

    // Export it
    Path dir = Paths.get("build/test/svn-export");
    FileTools.prune(dir);
    svn.doExport("trunk", dir);
    assertTrue(Files.isDirectory(dir));
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
  public void importFile() throws IOException, SVNException {
    SubVersion svn = new SubVersion(repositoryPathToString(mockSvnRepositoryTrunk));
    assertTrue(svn.isExists());
    assertTrue(svn.isDirectory());
    assertFalse(svn.isFile());

    // Make a temp file
    File temp = File.createTempFile("foo", "bar");
    temp.deleteOnExit();
    Files.write(temp.toPath(), "Hello world".getBytes());

    // Import it
    long now = System.currentTimeMillis();
    svn.doImport("/svn-test/import" + now + "/file" + now, temp.toPath());
  }

  @BeforeClass
  public void initMockRepository() {
    try {
      FileTools.prune(mockSvnRepositoryRoot);
      FileTools.prune(mockSvnProject);
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
  private String repositoryPathToString(Path repositoryPath) {
    try {
      return SVNURL.fromFile(repositoryPath.toFile()).toString();
    } catch (SVNException e) {
      Assert.fail();
    }
    return null;
  }
}
