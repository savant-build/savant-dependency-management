/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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
package org.savantbuild.dep.workflow.process;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.maven.MavenDependency;
import org.savantbuild.dep.maven.MavenTools;
import org.savantbuild.dep.maven.POM;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.xml.ArtifactTools;
import org.savantbuild.domain.Version;
import org.savantbuild.net.NetTools;
import org.savantbuild.output.Output;

/**
 * This class is a workflow process that attempts to download artifacts from a Maven repository via HTTP.
 * <p>
 * Maven's URL scheme is
 * <p>
 * <b>domain</b>/<b>group</b>/<b>project</b>/<b>version</b>/<b>name</b>-<b>version</b>.<b>type</b>
 *
 * @author Brian Pontarelli
 */
public class MavenProcess extends URLProcess {
  public MavenProcess(Output output, String url, String username, String password) {
    super(output, url, username, password);
  }

  /**
   * Throws an exception. This isn't supported.
   */
  @Override
  public void deleteIntegrationBuilds(Artifact artifact) throws ProcessFailureException {
    throw new ProcessFailureException(artifact, "The [url] process doesn't support deleting integration builds.");
  }

  /**
   * Using the URL spec given, this method connects to the URL, reads the file from the URL and stores the file in the
   * local cache store. The artifact is used to determine the local cache store directory and file name.
   *
   * @param artifact        The artifact being fetched and stored
   * @param publishWorkflow The publishWorkflow to publish the artifact if found.
   * @param item            The item to fetch.
   * @return The File of the artifact after it has been published.
   */
  @Override
  public Path fetch(Artifact artifact, String item, PublishWorkflow publishWorkflow) throws ProcessFailureException {
    if (item.endsWith(".amd")) {
      // See if the Maven repo has an AMD
      Path path = super.fetch(artifact, item, publishWorkflow);
      if (path != null) {
        return path;
      }
    }

    // Fall back to a POM from the AMD (or if it was already a POM) and translate it
    if (item.endsWith(".amd") || item.endsWith(".pom")) {
      POM pom = loadPOM(artifact, publishWorkflow);
      Path amd = translatePOM(artifact, pom);
      return publishWorkflow.publish(artifact, item, amd);
    }

    return super.fetch(artifact, item, publishWorkflow);
  }

  /**
   * Throws an exception. This isn't supported.
   */
  @Override
  public Path publish(Artifact artifact, String item, Path file) throws ProcessFailureException {
    throw new ProcessFailureException(artifact, "The [url] process doesn't allow publishing.");
  }

  @Override
  public String toString() {
    return "MVN(" + url + ")";
  }

  private POM loadPOM(Artifact artifact, PublishWorkflow publishWorkflow) throws ProcessFailureException {
    // We can directly load a POM or translate an AMD to a POM
    String item = artifact.getArtifactFilePOM();
    Path path = super.fetch(artifact, item, publishWorkflow);
    if (path == null) {
      URI itemURI = NetTools.build(url, artifact.id.group.replace('.', '/'), artifact.id.project, artifact.version.toString(), artifact.getArtifactFile());
      throw new ProcessFailureException(artifact, "A POM could not be retrieve from the Maven repository at the URL [" + itemURI + "]");
    }

    POM pom = MavenTools.parsePOM(path, output);

    // Recusrively load the parent POM and any dependencies that are `import`
    if (pom.parentGroup != null && pom.parentId != null && pom.parentVersion != null) {
      Artifact parentPOM = new Artifact(new ArtifactID(pom.parentGroup, pom.parentId, pom.parentId, "pom"), new Version(pom.parentVersion), false);
      pom.parent = loadPOM(parentPOM, publishWorkflow);
    }

    List<MavenDependency> newDefs = new ArrayList<>();
    Iterator<MavenDependency> it = pom.dependenciesDefinitions.iterator();
    while (it.hasNext()) {
      MavenDependency dependency = it.next();
      if (!dependency.scope.equalsIgnoreCase("import")) {
        continue;
      }

      Artifact dep = new Artifact(new ArtifactID(dependency.group, dependency.id, dependency.id, "pom"), new Version(dependency.version), false);
      POM dependencyPOM = loadPOM(dep, publishWorkflow);
      it.remove(); // Remove the import
      newDefs.addAll(dependencyPOM.dependenciesDefinitions);
    }

    pom.dependenciesDefinitions.addAll(newDefs);

    return pom;
  }

  private Path translatePOM(Artifact artifact, POM pom) {
    try {
      ArtifactMetaData amd = new ArtifactMetaData(MavenTools.toSavantDependencies(pom), MavenTools.toSavantLicenses(pom));
      return ArtifactTools.generateXML(amd);
    } catch (IOException e) {
      throw new ProcessFailureException(artifact, e);
    }
  }
}
