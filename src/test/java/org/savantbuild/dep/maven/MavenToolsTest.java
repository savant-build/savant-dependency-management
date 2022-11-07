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
package org.savantbuild.dep.maven;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import org.savantbuild.dep.BaseUnitTest;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.output.SystemOutOutput;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Tests the Maven malarky.
 *
 * @author Brian Pontarelli
 */
public class MavenToolsTest extends BaseUnitTest {
  @Test
  public void parse() {
    POM pom = MavenTools.parsePOM(Paths.get("../savant-dependency-management/src/test/resources/groovy-4.0.5.pom"), new SystemOutOutput(true));
    assertEquals(pom.group, "org.apache.groovy");
    assertEquals(pom.id, "groovy");
    assertEquals(pom.name, "Apache Groovy");
    assertEquals(pom.version, "4.0.5");
    assertNull(pom.parentGroup);
    assertNull(pom.parentId);
    assertNull(pom.parentVersion);
    assertEquals(pom.dependencies, Arrays.asList(
            new MavenDependency("org.codehaus.gpars", "gpars", "1.2.1", "compile", true, Collections.singletonList(new MavenExclusion("org.codehaus.groovy", "groovy-all"))),
            new MavenDependency("org.apache.ivy", "ivy", "2.5.0", "compile", true, Collections.singletonList(new MavenExclusion("*", "*"))),
            new MavenDependency("com.thoughtworks.xstream", "xstream", "1.4.19", "compile", true,
                Arrays.asList(new MavenExclusion("junit", "junit"), new MavenExclusion("xpp3", "xpp3_min"), new MavenExclusion("xmlpull", "xmlpull"), new MavenExclusion("jmock", "jmock"))
            ),
            new MavenDependency("org.fusesource.jansi", "jansi", "2.4.0", "compile", true, Collections.emptyList())
        )
    );
    assertEquals(pom.licenses, Collections.singletonList(new MavenLicense("repo", "The Apache Software License, Version 2.0", "http://www.apache.org/licenses/LICENSE-2.0.txt")));

    // Everything is optional
    Dependencies dependencies = MavenTools.toSavantDependencies(pom, Collections.emptyMap());
    assertEquals(dependencies, new Dependencies());
  }
}
