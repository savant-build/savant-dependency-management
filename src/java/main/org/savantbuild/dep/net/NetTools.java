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

import org.savantbuild.dep.io.IOTools;
import org.savantbuild.dep.io.MD5;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.Base64;

/**
 * This class provides toolkit methods for helping work with URLs and URIs and other network classes.
 *
 * @author Brian Pontarelli
 */
public class NetTools {
  /**
   * Builds a URI from the given parts. These are concatenated together with slashes, depending on the endings of each.
   *
   * @param parts The parts
   * @return The URI.
   */
  public static URI build(String... parts) throws URISyntaxException {
    StringBuilder build = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      boolean endSlash = build.charAt(build.length() - 1) == '/';
      boolean startSlash = parts[i].startsWith("/");
      if (!endSlash && !startSlash) {
        build.append("/");
      }

      String part = parts[i];
      if (endSlash && startSlash) {
        part = parts[i].substring(1);
      }

      boolean first = true;
      String[] splits = part.split("/");
      for (String split : splits) {
        if (!first) {
          build.append("/");
        } else {
          first = false;
        }

        try {
          build.append(URLEncoder.encode(split, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
          // Not really possible
          throw new IllegalStateException("The JVM doesn't have UTF-8", e);
        }
      }
    }

    return new URI(build.toString());
  }

  /**
   * Downloads the resource given.
   *
   * @param uri      The resource.
   * @param username (Optional) The username that might be used to connect to the resource.
   * @param password (Optional) The password that might be used to connect to the resource.
   * @param md5      (Optional) The MD5 of the resource (to verify).
   * @return A temp file that stores the resource or null if the given URI doesn't exist.
   * @throws IOException If the resource could not be downloaded.
   */
  public static Path downloadToPath(URI uri, String username, String password, MD5 md5) throws IOException {
    URLConnection uc = uri.toURL().openConnection();
    if (uc instanceof HttpURLConnection) {
      HttpURLConnection huc = (HttpURLConnection) uc;
      if (username != null) {
        String credentials = username + ":" + password;
        huc.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()));
      }

      huc.setInstanceFollowRedirects(true);
    }

    if (uc instanceof HttpsURLConnection) {
      HttpsURLConnection huc = (HttpsURLConnection) uc;
      huc.setHostnameVerifier((s, sslSession) -> true);
    }

    uc.connect();

    if (uc instanceof HttpURLConnection) {
      HttpURLConnection huc = (HttpURLConnection) uc;
      int result = huc.getResponseCode();
      if (result != 200 && result != 404 && result != 410) {
        throw new IOException("HTTP sent an unexpected response code [" + result + "]");
      } else if (result == 404 || result == 410) {
        return null;
      }
    }

    File file = File.createTempFile("savant-net-tools", "download");
    file.deleteOnExit();

    try (InputStream is = uc.getInputStream(); FileOutputStream os = new FileOutputStream(file)) {
      IOTools.write(is, os, md5);
      os.flush();
    }

    return file.toPath();
  }
}
