/*
 * Copyright (c) 2008, Inversoft, All Rights Reserved.
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
package org.savantbuild.dep.version;

import org.savantbuild.dep.DependencyException;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Version;
import org.savantbuild.dep.util.ErrorList;
import org.savantbuild.dep.workflow.FetchWorkflow;
import org.savantbuild.dep.workflow.Workflow;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * This class is a toolkit with helper methods for working with artifacts versions.
 *
 * @author Brian Pontarelli
 */
public class ArtifactVersionTools {
  public static final String INTEGRATION = "-{integration}";

  public static final String LATEST = "{latest}";

  /**
   * Strips the integration version from the artifact to produce a base version.
   *
   * @param artifact The artifact.
   * @return The base version.
   * @throws DependencyException If the artifact isn't an integration version.
   */
  public static String baseVersion(Artifact artifact) {
    String version = artifact.version;
    if (!version.endsWith(INTEGRATION)) {
      throw new DependencyException("The version [" + version + "] is not an integration build version");
    }

    return version.substring(0, version.length() - INTEGRATION.length());
  }

  /**
   * Using the list of names, this determines the best integration version.
   *
   * @param artifact The artifact whose version is an integration version.
   * @param names    The list of artifact names.
   * @return The best version or null if there aren't any integration builds in the list of names.
   */
  public static String bestIntegration(Artifact artifact, Set<String> names) {
    String baseVersion = baseVersion(artifact);
    String prefix = artifact.id.name + "-" + baseVersion + "-IB";
    OptionalLong max = names.stream()
                            .filter((name) -> name.startsWith(prefix))
                            .map((name) -> {
                              int index = name.indexOf(".", prefix.length());
                              return index >= 0 ? name.substring(prefix.length(), index) : name.substring(prefix.length());
                            })
                            .mapToLong(Long::parseLong)
                            .reduce(Long::max);

    if (!max.isPresent()) {
      return null;
    }

    return baseVersion + "-IB" + max.getAsLong();
  }

  /**
   * Determines the latest version from the list of names given. This list is normally the list of directories under the
   * project directory.
   *
   * @param names The list of version directory names.
   * @return The best version.
   */
  public static String latest(Set<String> names) {
    Optional<Version> best = names.stream()
                                  .map(Version::new)
                                  .max(Version::compareTo);

    return best.isPresent() ? best.get().toString() : null;
  }

  /**
   * Resolve all of the integration build versions for each artifact in the dependencies. This is not transitive.
   *
   * @param dependencies    The dependencies to update.
   * @param workflow The workflow used to resolve the integration build versions.
   */
  public static void resolve(Dependencies dependencies, Workflow workflow) {
    FetchWorkflow fw = workflow.getFetchWorkflow();

    ErrorList errors = new ErrorList();
    Map<String, DependencyGroup> artifactGroups = dependencies.groups;
    artifactGroups.forEach((type, group) -> group.dependencies
         .stream()
         .filter((artifact) -> artifact.isIntegrationBuild() || artifact.isLatestBuild())
         .forEach((artifact) -> {
           String version = fw.determineVersion(artifact);
           if (artifact.isLatestBuild()) {
             if (version == null) {
               errors.addError("Artifact [" + artifact + "] is set to the latest version, but no versions exists");
             } else {
               artifact.version = version;
             }
           } else {
             if (version == null) {
               errors.addError("Artifact [" + artifact + "] is set to use an integration or latest build, but no builds exists");
             } else {
               artifact.integrationVersion = version;
             }
           }
         }));

    if (!errors.isEmpty()) {
      throw new DependencyException(errors);
    }
  }
}
