/*
 * Copyright (c) 2001-2013, Inversoft, All Rights Reserved
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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Tests the artifact domain object.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class ArtifactTest {
  @Test
  public void construct() {
    assertEquals(new Artifact("group:name:2.0"), new Artifact(new ArtifactID("group", "name", "name", "jar"), new Version("2.0")));
    assertEquals(new Artifact("group:name:2.0:zip"), new Artifact(new ArtifactID("group", "name", "name", "zip"), new Version("2.0")));
    assertEquals(new Artifact("group:project:name:2.0:zip"), new Artifact(new ArtifactID("group", "project", "name", "zip"), new Version("2.0")));
  }

  @Test
  public void syntheticMethods() {
    assertEquals(new Artifact("group:name:2.0").getArtifactFile(), "name-2.0.jar");
    assertEquals(new Artifact("group:name:2.0").getArtifactMetaDataFile(), "name-2.0.jar.amd");
    assertEquals(new Artifact("group:name:2.0").getArtifactSourceFile(), "name-2.0-src.jar");
  }
}
