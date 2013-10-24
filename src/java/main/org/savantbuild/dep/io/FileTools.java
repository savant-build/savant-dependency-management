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
package org.savantbuild.dep.io;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * This class provides File utility methods.
 *
 * @author Brian Pontarelli
 */
public class FileTools {
  /**
   * Calculates the MD5 sum for the given Path.
   *
   * @param path The path to MD5.
   * @return The MD5 sum and never null.
   * @throws IOException If the file could not be MD5 summed.
   */
  public static MD5 md5(Path path) throws IOException {
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Invalid file to MD5 [" + path.toAbsolutePath() + "]");
    }

    return MD5.fromBytes(Files.readAllBytes(path), path.getFileName().toString());
  }

  /**
   * Prunes the given path. If the path is a directory, this deletes everything underneath it, but does not traverse
   * across symbolic links, it simply deletes the link. If the path is a file, it is deleted. If the path is a symbolic
   * link, it is unlinked.
   *
   * @param path The path to delete.
   */
  public static void prune(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }

    if (Files.isSymbolicLink(path)) {
      Files.delete(path);
      return;
    }

    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Unzips the given JAR file.
   *
   * @param file The JAR file to unzip.
   * @param dir  The directory to unzip to.
   * @throws IOException If the unzip fails.
   */
  public static void unzip(Path file, Path dir) throws IOException {
    try (JarInputStream jis = new JarInputStream(Files.newInputStream(file))) {
      JarEntry entry = jis.getNextJarEntry();
      while (entry != null) {
        if (!entry.isDirectory()) {
          Path outFile = dir.resolve(entry.getName());
          Files.createDirectories(outFile.getParent());
          Files.copy(jis, outFile);
        }

        entry = jis.getNextJarEntry();
      }
    }
  }
}
