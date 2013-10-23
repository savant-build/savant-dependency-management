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
package org.savantbuild.dep;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Publication;
import org.savantbuild.dep.io.MD5;
import org.savantbuild.dep.version.ArtifactVersionTools;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.xml.ArtifactTools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.Arrays.asList;

/**
 * This is the default implementation of the publisher. This uses the domain objects given to publish the artifacts.
 *
 * @author Brian Pontarelli
 */
public class DefaultDependencyPublisher implements DependencyPublisher {
  private final static Logger logger = Logger.getLogger(DefaultDependencyPublisher.class.getName());

  /**
   * {@inheritDoc}
   */
  @Override
  public Path publish(Publication publication, PublishWorkflow workflow, boolean integration, DependencyListener... listeners) {
    Map<Publication, Path> results = publish(asList(publication), workflow, integration, listeners);
    return results.get(publication);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<Publication, Path> publish(Iterable<Publication> publications, PublishWorkflow workflow, boolean integration, DependencyListener... listeners) {
    if (workflow == null) {
      throw new DependencyException("No PublishWorkflow ws given in order to publish the projects artifacts (publications). " +
        "Ensure that you have properly configured a publish workflow in the ~/.savant/workflows.savant file or that " +
        "you are using the default publish workflows.");
    }

    if (publications == null || !publications.iterator().hasNext()) {
      throw new DependencyException("No artifacts (publications) were given to publish. This could be because you haven't " +
        "defined any or that you have called the Publisher incorrect.");
    }

    logger.info("Publishing the project's artifacts (publications)");

    Map<Publication, Path> results = new HashMap<>();
    for (Publication publication : publications) {
      Dependencies deps = publication.dependencies;

      // Create an artifact that will be published.
//      Artifact forPath;
//      Artifact forName;
//      if (integration) {
//        forPath = new Artifact(project.getGroup(), project.getName(), publication.getName(), project.getVersion() + "-{integration}", publication.getType());
//        forName = new Artifact(project.getGroup(), project.getName(), publication.getName(), integrationVersion, publication.getType());
//      } else {
//        forPath = new Artifact(project.getGroup(), project.getName(), publication.getName(), project.getVersion(), publication.getType());
//        forName = forPath;
//      }

      // Clean old integration builds files. This will include the source JARs, AMD files, artifacts, MD5 files
      // and any old neg files. This will not include any extra items.
      if (!integration) {
        workflow.deleteIntegrationBuilds(publication.artifact);
      }

      // Publish the artifact itself
      if (!Files.exists(publication.file) || Files.isDirectory(publication.file)) {
        throw new DependencyException("Invalid file defined in a publication element [" + publication.file.toAbsolutePath() + "]");
      }

      // Publish the artifact
      Path publishedFile = workflow.publish(publication.artifact, publication.artifact.getArtifactFile(), publication.file);
      results.put(publication, publishedFile);
      md5(workflow, publication.artifact, publication.artifact.getArtifactFile(), publication.file);

      // Publish the MetaData
      ArtifactMetaData amd = new ArtifactMetaData(deps, publication.artifact.compatibility);
      Path amdFile = ArtifactTools.generateXML(amd);
      workflow.publish(publication.artifact, publication.artifact.getArtifactFile(), amdFile);
      md5(workflow, publication.artifact, publication.artifact.getArtifactMetaDataFile(), amdFile);

      // Publish the source JAR if it exists next to the artifact file
      if (publication.sourceFile != null && Files.isRegularFile(publication.sourceFile)) {
        workflow.publish(publication.artifact, publication.artifact.getArtifactSourceFile(), publication.sourceFile);
        md5(workflow, publication.artifact, publication.artifact.getArtifactSourceFile(), publication.sourceFile);
      }

      for (DependencyListener listener : listeners) {
        listener.artifactPublished(publication.artifact);
      }
    }

    return results;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasIntegrations(Dependencies dependencies) {
    Set<Artifact> artifacts = dependencies.getAllArtifacts();
    for (Artifact artifact : artifacts) {
      if (artifact.version.endsWith(ArtifactVersionTools.INTEGRATION)) {
        return true;
      }
    }

    return false;
  }

  private void md5(PublishWorkflow handler, Artifact artifact, String item, Path file) {
    Path md5File = null;
    try {
      File tempFile = File.createTempFile("savant", "md5");
      md5File = tempFile.toPath();
      MD5 md5 = MD5.fromBytes(Files.readAllBytes(file), item);
      Files.write(md5File, md5.bytes);

      handler.publish(artifact, item + ".md5", tempFile.toPath());
    } catch (IOException e) {
      throw new DependencyException("Unable to generate MD5 checksum for the publication [" + artifact + "]", e);
    } finally {
      if (md5File != null) {
        try {
          Files.delete(md5File);
        } catch (IOException e) {
          // Ignore since there is nothing we can do about it
        }
      }
    }
  }
}
