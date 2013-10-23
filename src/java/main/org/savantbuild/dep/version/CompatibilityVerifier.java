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
package org.savantbuild.dep.version;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.DependencyLinkValue;
import org.savantbuild.dep.graph.GraphLink;
import org.savantbuild.dep.graph.GraphNode;
import org.savantbuild.dep.graph.GraphPath;
import org.savantbuild.dep.util.ErrorList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * This class is a compatibility verifier that is used to verify a set of dependencies including all of the transitive
 * dependencies. This check is done to ensure that all artifacts in the entire set are compatible and to make selections
 * about which versions of artifacts should be used over others.
 * <p/>
 * The interface that is used to determine compatibility as well as upgrades when an artifact has two compatible
 * versions is the {@link VersionComparator} interface.
 *
 * @author Brian Pontarelli
 */
public class CompatibilityVerifier {
  private final static Logger logger = Logger.getLogger(CompatibilityVerifier.class.getName());

  /**
   * This method performs a bredth first traversal of the artifact tree and verifies that the artifacts in the tree are
   * compatible. It also builds up a list of the artifacts that need to be fetched.
   *
   * @param deps       The dependencies of this project (top level).
   * @param graph      The artifact graph that describes the entire dependencies tree for the project.
   * @param groupTypes (Optional) This is a set of group types that are currently being resolved. This is used to reduce
   *                   the compatibility check.
   * @return A result that contains the list of artifacts that should be fetched based on compatibility and an ErrorList
   *         that will contain any errors that were found.
   */
  public ErrorList verifyCompatibility(Dependencies deps, DependencyGraph graph, Set<String> groupTypes) {
    ErrorList errors = new ErrorList();
    verifyCompatibilityTypes(deps, graph, groupTypes, errors);
    verifyCompatibility(deps, graph, groupTypes, errors);
    return errors;
  }

  /**
   * Given the GraphNode, this method looks at all the inbound links that have compatType values and finds the first one
   * that isn't null. At this point the compatType values should all be the same or null.
   *
   * @param node The GraphNode to look at the links for.
   * @return The compatType to use or null.
   */
  protected String determineCompatType(GraphNode<ArtifactID, DependencyLinkValue> node) {
    List<GraphLink<ArtifactID, DependencyLinkValue>> inboundLinks = node.getInboundLinksList();
    for (GraphLink<ArtifactID, DependencyLinkValue> inboundLink : inboundLinks) {
      String compatType = inboundLink.value.compatibility;
      logger.fine("Determining compatType for artifact [" + node.value + "]");

      if (compatType != null) {
        logger.fine("Found compatType [" + compatType + "]");
        return compatType;
      }
    }

    return null;
  }

  protected String makeCompatibilityString(Dependencies dependencies, DependencyGraph graph,
                                           GraphLink<ArtifactID, DependencyLinkValue> first,
                                           GraphLink<ArtifactID, DependencyLinkValue> second) {
    Set<Artifact> rootArtifacts = dependencies.getAllArtifacts();
    List<GraphPath<ArtifactID>> artifactPaths1 = makeAllPaths(rootArtifacts, graph, first.destination.value);
    List<GraphPath<ArtifactID>> artifactPaths2 = makeAllPaths(rootArtifacts, graph, second.destination.value);

    StringBuffer buf = new StringBuffer();
    buf.append("Artifact [").append(artifactString(first)).append("] not compatible with [").
        append(artifactString(second)).append("]\n");
    makePathString(buf, first, artifactPaths1);
    makePathString(buf, second, artifactPaths2);

    return buf.toString();
  }

  /**
   * Verifies the compatibility of the artifacts in the graph.
   *
   * @param deps       The project dependencies to start from.
   * @param graph      The graph to traverse to find all the artifacts and verify compatibility.
   * @param groupTypes (Optional) This is a set of group types that are currently being resolved. This is used to reduce
   *                   the compatibility check.
   * @param errors     Collects the errors.
   */
  protected void verifyCompatibility(final Dependencies deps, final DependencyGraph graph,
                                     final Set<String> groupTypes, final ErrorList errors) {
    logger.fine("Verifying compatibility of artifacts in groups with type " + groupTypes);

    Set<GraphNode<ArtifactID, DependencyLinkValue>> graphNodes = graph.nodes();
    for (GraphNode<ArtifactID, DependencyLinkValue> graphNode : graphNodes) {
      String compatType = determineCompatType(graphNode);
      VersionComparator versionComparator = VersionComparatorRegistry.lookup(compatType);

      logger.fine("Using compatibility checker of type [" + compatType + "] with checker class [" +
          versionComparator.getClass() + "]");

      List<GraphLink<ArtifactID, DependencyLinkValue>> inboundLinks = graphNode.getInboundLinksList();

      // Only process them if there might be multiple versions
      if (inboundLinks.size() <= 1) {
        logger.fine("Skipping artifact [" + graphNode.value + "] because there is only a single inbound link");
        continue;
      }

      // Process away
      GraphLink<ArtifactID, DependencyLinkValue> bestLinkSoFar = null;
      String bestVersionSoFar = null;
      boolean twoVersions = false;
      boolean errorFound = false;
      for (GraphLink<ArtifactID, DependencyLinkValue> inboundLink : inboundLinks) {
        // If the group type is not in the list, skip this link
        if (groupTypes != null && groupTypes.size() > 0 && !groupTypes.contains(inboundLink.value.type)) {
          continue;
        }

        logger.fine("Checking [" + graphNode.value + "] for compatibility");

        String version = inboundLink.value.dependencyVersion;
        logger.fine("Checking version [" + version + "] of artifact [" + graphNode.value + "]");

        if (bestVersionSoFar == null) {
          bestVersionSoFar = version;
          bestLinkSoFar = inboundLink;
          logger.fine("First version for artifact");
          continue;
        } else if (version.equals(bestVersionSoFar)) {
          logger.fine("Identical version for artifact");
          continue;
        }

        twoVersions = true;
        logger.fine("Comparing versions [" + bestVersionSoFar + "] and [" + version + "] of artifact [" +
            graphNode.value + "]");

        String result = versionComparator.determineBestVersion(bestVersionSoFar, version);
        if (result == null) {
          // This means there was an error
          logger.fine("Making error string");
          String error = makeCompatibilityString(deps, graph, bestLinkSoFar, inboundLink);
          errors.addError(error);
          errorFound = true;
        } else if (result.equals(version)) {
          bestVersionSoFar = version;
          bestLinkSoFar = inboundLink;
        }
      }

      // Update all the links to the best version
      if (twoVersions && !errorFound) {
        logger.fine("Artifact [" + graphNode.value + "] had multiple versions and no errors");

        inboundLinks = graphNode.getInboundLinksList();
        for (GraphLink<ArtifactID, DependencyLinkValue> inboundLink : inboundLinks) {
          DependencyLinkValue link = inboundLink.value;
          String dependentVersion = link.dependentVersion;
          String type = link.type;

          DependencyLinkValue newLink = new DependencyLinkValue(dependentVersion, bestLinkSoFar.value.dependencyVersion, type, compatType);
          graph.removeLink(inboundLink.origin, inboundLink.destination, link);
          logger.fine("Breaking bad link from [" + inboundLink.origin.value + "] to [" +
              inboundLink.destination.value + "] version [" + link.dependencyVersion + "]");

          graph.addLink(inboundLink.origin, inboundLink.destination, newLink);
          logger.fine("Add better link from [" + inboundLink.origin.value + "] to [" +
              inboundLink.destination.value + "] version [" + bestVersionSoFar + "]");
        }

        logger.fine("Removing outbound links for other versions");
        List<GraphLink<ArtifactID, DependencyLinkValue>> outboundLinks = graphNode.getOutboundLinksList();
        for (GraphLink<ArtifactID, DependencyLinkValue> outboundLink : outboundLinks) {
          if (!outboundLink.value.dependentVersion.equals(bestVersionSoFar)) {
            logger.fine("Removing link from [" + outboundLink.origin.value + "] to [" +
                outboundLink.destination.value + "] because it was for version [" +
                outboundLink.value.dependentVersion + "] which is older than the " +
                "best version found of [" + bestVersionSoFar + "]");

            graph.removeLink(outboundLink.origin, outboundLink.destination, outboundLink.value);
          }
        }
      }

    }
  }

  /**
   * Verifies all of the artifacts that are identical but have different versions have the same compatibility setting.
   *
   * @param deps       The dependencies of this project (top level).
   * @param graph      The DependencyGraph.
   * @param groupTypes (Optional) This is a set of group types that are currently being resolved. This is used to reduce
   *                   the compatibility check.
   * @param errors     The ErrorList to add any errors to.
   */
  protected void verifyCompatibilityTypes(final Dependencies deps, final DependencyGraph graph,
                                          final Set<String> groupTypes, final ErrorList errors) {
    logger.fine("Verifying compatTypes for artifacts");

    Set<GraphNode<ArtifactID, DependencyLinkValue>> graphNodes = graph.nodes();
    for (GraphNode<ArtifactID, DependencyLinkValue> graphNode : graphNodes) {
      List<GraphLink<ArtifactID, DependencyLinkValue>> graphLinks = graphNode.getInboundLinksList();

      GraphLink<ArtifactID, DependencyLinkValue> linkToCompare = null;
      for (GraphLink<ArtifactID, DependencyLinkValue> graphLink : graphLinks) {
        // If the group type is not in the list, skip this link
        if (groupTypes != null && groupTypes.size() > 0 && !groupTypes.contains(graphLink.value.type)) {
          continue;
        }

        String current = graphLink.value.compatibility;
        if (current != null && linkToCompare == null) {
          linkToCompare = graphLink;
        } else if (current != null && !current.equals(linkToCompare.value.compatibility)) {
          String error = "Artifact [" + graphLink.origin.value + "] has two different " +
              "compatibility type properties in different locations. One is [" + current +
              "] and the other is [" + linkToCompare.value.compatibility +
              "]. Below are the pathes to the two locations\n" +
              makeCompatibilityString(deps, graph, linkToCompare, graphLink);
          errors.addError(error);
        }
      }
    }
  }

  private String artifactString(GraphLink<ArtifactID, DependencyLinkValue> link) {
    ArtifactID id = link.destination.value;
    return id.group + "|" + id.project + "|" + id.name + "-" + link.value.dependencyVersion + "." + id.type;
  }

  private List<GraphPath<ArtifactID>> makeAllPaths(Set<Artifact> rootArtifacts, DependencyGraph graph,
                                                   ArtifactID id) {
    List<GraphPath<ArtifactID>> artifactPaths = new ArrayList<>();
    for (Artifact rootArtifact : rootArtifacts) {
      List<GraphPath<ArtifactID>> paths = graph.paths(rootArtifact.id, id);
      if (paths != null) {
        artifactPaths.addAll(paths);
      }

      logger.fine("Calculating path from [" + rootArtifact + "] to [" + id + "]");
    }

    return artifactPaths;
  }

  private void makePathString(StringBuffer buf, GraphLink<ArtifactID, DependencyLinkValue> link,
                              List<GraphPath<ArtifactID>> paths) {
    buf.append("\tPaths to artifact [").append(artifactString(link)).append("] are:\n");
    if (paths.size() == 0) {
      buf.append("\t\tInside this project.\n");
    } else {
      for (GraphPath<ArtifactID> path : paths) {
        buf.append("\t\t");

        List<ArtifactID> artifactPath = path.getPath();
        for (int j = 0; j < artifactPath.size(); j++) {
          ArtifactID artInPath = artifactPath.get(j);
          buf.append("[").append(artInPath).append("]");
          if (j + 1 < artifactPath.size()) {
            buf.append(" -> ");
          }
        }

        buf.append("\n");
      }
    }
  }
}
