/*
 * Copyright (c) 2022, Inversoft Inc., All Rights Reserved
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
package org.savantbuild.dep.domain;

/**
 * A resolvable item for an artifact. This might be the metadata, JAR, etc.
 *
 * @author Brian Pontarelli
 */
public class ResolvableItem {
  public final String group;

  public final String item;

  public final String name;

  public final String project;

  public final String version;

  public ResolvableItem(String group, String project, String name, String version, String item) {
    this.group = group;
    this.project = project;
    this.name = name;
    this.version = version;
    this.item = item;
  }

  public ResolvableItem(ResolvableItem other, String item) {
    this.group = other.group;
    this.item = item;
    this.name = other.name;
    this.project = other.project;
    this.version = other.version;
  }

  @Override
  public String toString() {
    return group + ":" + project + ":" + name + ":" + version + ":" + item;
  }
}
