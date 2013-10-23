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

import com.jcraft.jsch.UserInfo;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * This class is a simple JSCH UserInfo implementation that uses the JDK 6.0 Console object or stdin to read
 * information.
 *
 * @author Brian Pontarelli
 */
public class BaseUserInfo implements UserInfo {
  private String passphrase;

  private String password;

  private boolean trust;

  public BaseUserInfo(String password, String passphrase, boolean trust) {
    this.password = password;
    this.passphrase = passphrase;
    this.trust = trust;
  }

  public String getPassphrase() {
    return passphrase;
  }

  public String getPassword() {
    return password;
  }

  public boolean promptPassphrase(String string) {
    if (passphrase != null) {
      return true;
    }

    System.out.print(string);
    System.out.print(" ");
    passphrase = readLine(true);
    return true;
  }

  public boolean promptPassword(String string) {
    if (password != null) {
      return true;
    }

    System.out.print(string);
    System.out.print(" ");
    password = readLine(true);
    return true;
  }

  public boolean promptYesNo(String string) {
    if (trust) {
      return true;
    }

    System.out.print(string);
    System.out.print(" ");
    String answer = readLine(false);
    return answer.equalsIgnoreCase("yes") || answer.equalsIgnoreCase("y");
  }

  public void showMessage(String string) {
    System.out.println(string);
  }

  private String readLine(boolean hidden) {
    if (!hidden) {
      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
      try {
        return br.readLine();
      } catch (IOException e) {
        return null;
      }
    }

    Console console = System.console();
    if (console == null) {
      throw new IllegalStateException("The JVM doesn't have a console and SSH requires user input");
    }

    return new String(console.readPassword());
  }
}
