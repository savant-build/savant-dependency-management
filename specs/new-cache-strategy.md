# Savant Dependency Management: New Cache Strategy

## Specification Document

**Date:** 2026-02-11
**Status:** DRAFT
**Branch:** `voidmain/cache-performance`
**Affected Repos:** `savant-dependency-management`

---

## 1. Problem Statement

### 1.1 Current Architecture

Savant currently maintains a **multi-level cache hierarchy** for dependency resolution:

| Level | Location | Contents | Scope |
|-------|----------|----------|-------|
| 1. Project cache | `.savant/cache/` (relative to project) | JARs, AMD files, POMs, MD5s, sources, `.neg` markers | Per-project |
| 2. Integration cache | `~/.savant/cache/` | Integration build artifacts | Global (user) |
| 3. Maven cache | `~/.m2/repository/` | JARs, POMs, MD5s (Maven layout) | Global (user) |
| 4. Remote repos | Savant repo + Maven Central | Everything | Network |

The **standard workflow** (configured via `WorkflowDelegate.standard()` in savant-core) is:

```
Fetch:   CacheProcess(.savant/cache) -> MavenCacheProcess(~/.m2) -> URLProcess(savantbuild.org) -> MavenProcess(maven central)
Publish: CacheProcess(.savant/cache) -> MavenCacheProcess(~/m2)
```

When a dependency is first resolved:
1. Savant checks `.savant/cache` -- miss
2. Savant checks `~/.m2/repository` -- miss
3. Savant downloads from Maven Central (POM + JAR + MD5)
4. The POM is translated to a Savant AMD file (adding license info, semver mapping, dependency groups)
5. **All artifacts (JAR, POM, AMD, MD5, sources) are copied into BOTH `.savant/cache` AND `~/.m2/repository`**

### 1.2 Problems with the Current Approach

1. **Disk duplication**: Every project maintains a full copy of all its dependency JARs in `.savant/cache`. A project with 100 dependencies may have 200+ MB of JARs duplicated from `~/.m2/repository`. Across N projects, this is N * 200 MB.

2. **Project directory bloat**: The `.savant/cache` directory pollutes the project directory with large binary files. Even though it's gitignored, it impacts disk usage, backup tools, and IDE indexing.

3. **Redundancy with Maven cache**: Since most Java dependencies come from Maven repositories, the JARs already exist in `~/.m2/repository`. Copying them into `.savant/cache` provides no additional value beyond a minor first-hit locality optimization.

4. **Friction for new users**: Developers coming from Maven/Gradle ecosystems already have `~/.m2/repository` populated. Savant duplicates those artifacts into its own cache rather than leveraging what's already there.

5. **Cache coherence complexity**: Having the same artifact in two locations (.savant/cache and ~/.m2) creates the potential for stale or inconsistent copies.

### 1.3 What's Actually Valuable in `.savant/cache`

The **only Savant-specific artifact** is the `.amd` file (Artifact Meta Data). This is the file Savant generates when translating a Maven POM into Savant's format. It captures:

- **Semantic version mappings** (e.g., `4.1.65.Final` -> `4.1.65`)
- **License information** (SPDX identifiers)
- **Dependency groups** with export/transitive semantics
- **Exclusions** in Savant's format (not Maven's optional/exclusion model)

However, this information can be derived at build time by parsing the POM directly and applying the semantic version mappings and license information configured in `build.savant`. Caching the AMD file is an optimization that avoids re-parsing, but is not strictly necessary.

Everything else in `.savant/cache` (JARs, POMs, source JARs, MD5s) is a duplicate of what exists or can be fetched into `~/.m2/repository`.

---

## 2. Proposed Solution

### 2.1 Design Principles

1. **Two global caches for binaries, split by source**:
   - `~/.m2/repository` stores JARs, POMs, source JARs, and MD5 files for **Maven-sourced** artifacts. This is shared across all projects and all build tools (Maven, Gradle, Savant).
   - `~/.savant/cache` stores JARs, AMDs, source JARs, and MD5 files for **Savant-sourced** artifacts (those fetched from Savant repositories like `repository.savantbuild.org`). This is also where integration build artifacts are stored.

2. **No project-level cache**: The project-level `.savant/cache` directory is eliminated entirely. Maven POMs are parsed directly into in-memory `ArtifactMetaData` at build time, applying semantic version mappings and license information from `build.savant`. Savant-sourced AMDs are cached globally in `~/.savant/cache`.

3. **All artifacts resolved from global caches on every build**: Savant resolves artifact paths from `~/.m2/repository` or `~/.savant/cache` at build time. This is a simple path computation (not a file copy) and adds negligible overhead.

4. **POM-to-dependency-graph translation happens in memory**: Rather than persisting AMD files for Maven-sourced artifacts, Savant parses the POM and constructs dependency graph nodes in memory each build. Semantic version mappings from `build.savant` are applied during this translation.

5. **Simplicity over optimization**: The design prioritizes simplicity and compatibility with the Maven ecosystem over micro-optimizations for cold-start performance.

### 2.2 New Architecture

| Level | Location | Contents | Scope |
|-------|----------|----------|-------|
| 1. Maven global cache | `~/.m2/repository/` | JARs, POMs, source JARs, MD5s (Maven-sourced) | Global (user) |
| 2. Savant global cache | `~/.savant/cache/` | JARs, AMDs, source JARs, MD5s (Savant-sourced) + integration builds | Global (user) |
| 3. Remote repos | Savant repo + Maven Central | Everything | Network |

### 2.3 New Standard Workflow

A single fetch chain and a single publish chain handle all artifact types. **Publish routing** is driven by `FetchResult` metadata — each publish process inspects the `ItemSource` on the `FetchResult` and decides whether to accept or reject the item.

```
Fetch:   CacheProcess(~/.savant/cache, ~/.m2) -> URLProcess(savantbuild.org) -> MavenProcess(maven central)
Publish: CacheProcess(~/.savant/cache, ~/.m2)   [routes based on FetchResult.source]
```

When a remote process downloads an item, it wraps the result in a `FetchResult` tagged with its `ItemSource` (SAVANT for URLProcess, MAVEN for MavenProcess). The publish workflow passes this `FetchResult` to every publish process. `CacheProcess` manages both directories and routes SAVANT items to `savantDir` and MAVEN items to `mavenDir`. This ensures artifacts are routed to the correct global cache without any explicit routing logic in the Workflow class.

On cold builds, the full fetch chain is tried for all item types — URLProcess may 404 on Maven-sourced JARs and MavenProcess may 404 on Savant-sourced AMDs. This is an acceptable tradeoff for code simplicity since it only affects the first build with empty caches.

**Note:** Savant-sourced artifacts have AMD files natively (no POMs), which are cached in `~/.savant/cache`. Maven-sourced artifacts have POMs that are parsed directly into in-memory `ArtifactMetaData` on each build, applying semantic version mappings and license information from `build.savant`. No AMD files are generated or cached for Maven-sourced artifacts.

### 2.4 Detailed Flow: Dependency Resolution

#### Step 1: Fetch Metadata

```
fetchMetaData("org.apache.groovy:groovy:4.0.5"):
  1. fetchWorkflow.fetchItem(amdOrPomItem, publishWorkflow)
     — ResolvableItem has primary=".jar.amd" with alternative=".pom"
     — Each process checks AMD first, then POM, before moving to next process:

     a. CacheProcess: check ~/.savant/cache/.../groovy-4.0.5.jar.amd
        -> If found: return FetchResult(path, SAVANT, amdItem)
        -> If not found: check ~/.savant/cache/.../groovy-4.0.5.pom (alternative)
        -> If not found: return null
     b. MavenCacheProcess: check ~/.m2/.../groovy-4.0.5.jar.amd
        -> Not found (AMDs don't exist in ~/.m2)
        -> Check ~/.m2/.../groovy-4.0.5.pom (alternative)
        -> If found: return FetchResult(path, MAVEN, pomItem)
     c. URLProcess: try savantbuild.org for AMD, then POM
        -> If AMD found: publish FetchResult(temp, SAVANT, amdItem) to publishWorkflow
           - CacheProcess.publish: source=SAVANT, accept -> copy to ~/.savant/cache
           - MavenCacheProcess.publish: source=SAVANT, reject -> null
           -> return FetchResult(cachedPath, SAVANT, amdItem)
     d. MavenProcess: try maven central for AMD, then POM
        -> AMD not found; POM found as alternative
        -> publish FetchResult(temp, MAVEN, pomItem) to publishWorkflow
           - CacheProcess.publish: source=MAVEN, reject -> null
           - MavenCacheProcess.publish: source=MAVEN, accept -> copy to ~/.m2
        -> return FetchResult(cachedPath, MAVEN, pomItem)

  2. If FetchResult.item ends with ".amd":
     -> Savant-sourced: parse AMD file and return ArtifactMetaData

  3. If FetchResult.item ends with ".pom":
     -> Maven-sourced: process POM through loadPOM(artifact, preloadedFile)
     a. Parse POM, resolve parent POMs and imports
     b. Apply semantic version mappings from build.savant
     c. Apply license information from build.savant
     d. Translate POM dependencies into Savant dependency groups
     e. Return in-memory ArtifactMetaData (no AMD file written to disk)

  4. If fetchWorkflow returned null (neither AMD nor POM found via alternatives):
     -> Fall back to loadPOM(artifact) which handles non-semantic version POM lookup
        (different version directory, e.g., "4.0.5.Final" instead of "4.0.5")
     -> If POM found: translate and return in-memory ArtifactMetaData
     -> If still null: throw ArtifactMetaDataMissingException
```

#### Step 2: Build Dependency Graph

The `DependencyGraph` is built by recursively fetching metadata for all transitive dependencies. For Savant-sourced artifacts, this reads cached AMD files from `~/.savant/cache`. For Maven-sourced artifacts, this parses POMs in memory on each build.

#### Step 3: Reduce Graph

No change. Version compatibility checking and selection of highest compatible version.

#### Step 4: Resolve Artifacts (JARs)

```
fetchArtifact("org.apache.groovy:groovy:4.0.5"):
  1. fetchWorkflow.fetchItem(jarItem, publishWorkflow) — tries all processes in order:
     a. CacheProcess: check ~/.savant/cache/.../groovy-4.0.5.jar
        -> If found: return FetchResult(path, SAVANT, item)
     b. MavenCacheProcess: check ~/.m2/.../groovy-4.0.5.jar
        -> If found: return FetchResult(path, MAVEN, item)
     c. URLProcess: try savantbuild.org for JAR
        -> If found: publish FetchResult(temp, SAVANT, item)
           - CacheProcess accepts (SAVANT) -> ~/.savant/cache
           - MavenCacheProcess rejects (SAVANT)
        -> return FetchResult(cachedPath, SAVANT, item)
     d. MavenProcess: try Maven Central for JAR
        -> If found: publish FetchResult(temp, MAVEN, item)
           - CacheProcess rejects (MAVEN)
           - MavenCacheProcess accepts (MAVEN) -> ~/.m2
        -> return FetchResult(cachedPath, MAVEN, item)

  2. Return FetchResult.file() as the resolved path
  3. (No copy to project .savant/cache)

  Non-semantic version handling:
  4. If step 1 returned null and artifact.nonSemanticVersion != null:
     a. Retry fetchWorkflow.fetchItem with the non-semantic version item
     b. Return FetchResult.file() directly (the path in ~/.m2 with the original version)
     c. No re-publishing with semantic filename — the graph operates on semantic
        versions internally, but the resolved file path uses the original Maven version
```

### 2.5 Key Code Changes

#### 2.5.1 New Types: `ItemSource`, `FetchResult` (savant-dependency-management)

Two new types provide the metadata that drives publish routing:

```java
/**
 * Identifies the source domain of an artifact.
 */
public enum ItemSource {
  /** Artifact originates from a Savant repository (has AMD, no POM). */
  SAVANT,
  /** Artifact originates from a Maven repository (has POM, no AMD). */
  MAVEN
}
```

```java
/**
 * The result of a successful fetch operation. Bundles the fetched file path,
 * the item identity, and the source domain so that publish processes can
 * route the item to the correct cache.
 */
public record FetchResult(Path file, ItemSource source, ResolvableItem item) {}
```

`FetchResult` is the return type of `Process.fetch()` and the sole parameter of `Process.publish()`. It carries everything a publish process needs: the file to copy, the item identity (for path construction), and the source domain (for routing).

#### 2.5.1a Alternative Items on `ResolvableItem` (savant-dependency-management)

`ResolvableItem` has an `alternativeItems` field (`List<String>`, immutable, never null) that allows a single `fetchWorkflow.fetchItem()` call to check multiple item names within each Process. This reduces unnecessary HTTP calls by letting local cache processes find files under alternative names before remote processes are tried.

```java
public class ResolvableItem {
    public final List<String> alternativeItems;
    // ... group, project, name, version, item fields ...

    // 5-arg constructor — no alternatives
    public ResolvableItem(String group, String project, String name, String version, String item) { ... }

    // 6-arg constructor — with alternatives
    public ResolvableItem(String group, String project, String name, String version, String item, List<String> alternativeItems) { ... }

    // Copy constructor — drops alternatives (used for MD5, neg markers, matched items)
    public ResolvableItem(ResolvableItem other, String item) { ... }
}
```

Each `Process.fetch()` implementation tries the primary `item` first, then iterates `alternativeItems`. When an alternative matches, the `FetchResult` contains a copy of the `ResolvableItem` with the matched item name (via the copy constructor, which drops alternatives). This ensures publish workflows receive the correct filename.

#### 2.5.2 `Process` Interface Changes (savant-dependency-management)

The `Process` interface changes from:

```java
public interface Process {
  Path fetch(ResolvableItem item, PublishWorkflow publishWorkflow);
  Path publish(ResolvableItem item, Path itemFile);
}
```

To:

```java
public interface Process {
  FetchResult fetch(ResolvableItem item, PublishWorkflow publishWorkflow);
  Path publish(FetchResult fetchResult);
}
```

**Fetch**: Each process returns a `FetchResult` tagged with its `ItemSource`, or `null` if it can't handle the item. The fetch chain stops at the first non-null result.

**Publish**: Each process inspects `fetchResult.source()` and returns `null` if it doesn't want to handle the item. `CacheProcess` rejects `MAVEN`-sourced items; `MavenCacheProcess` rejects `SAVANT`-sourced items. Both processes in the publish chain are always called — each decides independently whether to accept.

#### 2.5.3 `FetchWorkflow` and `PublishWorkflow` Changes (savant-dependency-management)

`FetchWorkflow.fetchItem()` returns `FetchResult` instead of `Path`:

```java
public FetchResult fetchItem(ResolvableItem item, PublishWorkflow publishWorkflow) {
    for (Process p : processes) {
        FetchResult result = p.fetch(item, publishWorkflow);
        if (result != null) return result;
    }
    return null;
}
```

`PublishWorkflow.publish()` takes a `FetchResult` and passes it to each process:

```java
public Path publish(FetchResult fetchResult) {
    Path result = null;
    for (Process process : processes) {
        Path temp = process.publish(fetchResult);
        if (result == null) result = temp;
    }
    return result;
}
```

#### 2.5.4 Process Implementation Changes (savant-dependency-management)

All `Process.fetch()` implementations support alternative items (see Section 2.5.1a). Each tries the primary `item` first, then iterates `item.alternativeItems`. When an alternative matches, the `FetchResult` contains a `ResolvableItem` copy with the matched item name.

**`CacheProcess`** (manages both `~/.savant/cache` and `~/.m2/repository`):

- Constructor: `CacheProcess(Output output, String savantDir, String mavenDir)` — either directory can be null to disable that cache.
- `fetch()`: Tries `savantDir` first, tagging hits as `SAVANT`. If not found, tries `mavenDir`, tagging hits as `MAVEN`. Each directory check looks for the primary item, then checks for a `.neg` marker (throws `NegativeCacheException` if found), then iterates `item.alternativeItems`. Returns a `FetchResult` with the matched item name, or `null` if none found. Uses an internal `CacheHit` record to bundle the file path and matched item name.
- `publish()`: Routes based on `fetchResult.source()` — uses `savantDir` for `SAVANT`, `mavenDir` for `MAVEN`. Returns `null` if the relevant directory is null.
- `MavenCacheProcess` has been removed — its functionality is unified into `CacheProcess`.

**`URLProcess`** (Savant remote repositories):

- `fetch()`: Delegates to `tryFetchCandidate()` for the primary item, then for each alternative. `tryFetchCandidate(item, candidateItem, publishWorkflow)` downloads `candidateItem.md5` — if 404, returns null. Otherwise downloads `candidateItem` with MD5 verification, publishes both, and returns a `FetchResult` with the matched item name. Returns `null` if no candidate is found.
- `publish()`: Unchanged — throws `ProcessFailureException` (URL processes don't accept publishes).

**`MavenProcess`** (Maven remote repositories):

- Extends `URLProcess` but uses `ItemSource.MAVEN` instead of `ItemSource.SAVANT`. When it downloads an item, the `FetchResult` is tagged `MAVEN`, so the publish workflow routes it to `~/.m2` via `MavenCacheProcess`. Does NOT override `fetch()` — inherits alternative item support from `URLProcess`.

**`SVNProcess`** (SubVersion repositories):

- `fetch()`: Same pattern as `URLProcess` — delegates to `tryFetchCandidate()` for the primary item, then for each alternative. Each candidate is exported from the SVN repository with MD5 verification. Returns a `FetchResult` with the matched item name, or `null` if no candidate is found.
- `publish()`: Imports the file into the SVN repository (unchanged).

#### 2.5.5 `Workflow.java` Changes (savant-dependency-management)

The `Workflow` class retains its `fetchWorkflow` and `publishWorkflow` fields. New field:

- **`Map<Artifact, POM> pomCache`**: A simple `HashMap` that caches parsed POM objects within a build session. `loadPOM()` checks this map before fetching and parsing. This avoids redundant POM parsing when the same artifact's POM is referenced multiple times during recursive parent POM and BOM import resolution. This is not an LRU cache — it grows for the duration of the build and is discarded when the `Workflow` instance is garbage collected.

The method changes:

- **`fetchMetaData()`**: Uses a single `fetchWorkflow.fetchItem()` call with the AMD file as the primary item and the POM file as an alternative (via `ResolvableItem.alternativeItems`). If the result's item name ends in `.amd`, the artifact is Savant-sourced and the AMD is parsed directly. If the POM is found as the alternative, it is processed through `loadPOM(artifact, preloadedFile)` and translated to in-memory `ArtifactMetaData`. If neither is found via alternatives, falls back to `loadPOM(artifact)` which handles non-semantic version POM lookups (different version directory). No AMD file is generated or published for Maven-sourced artifacts.

- **`fetchArtifact()`**: Uses the fetch workflow. The process chain tries all processes in order. Each remote process tags its `FetchResult` with the appropriate `ItemSource`; the publish workflow routes to the correct cache. For Maven artifacts with non-semantic versions (e.g., `4.1.65.Final`), the JAR is resolved from `~/.m2` using the original Maven version path and the path is returned directly — **no re-publishing** of a semantic-named copy occurs. The semantic version mapping is applied in memory only.

- **`fetchSource()`**: Uses a single `fetchWorkflow.fetchItem()` call with the Savant-style source (`-src.jar`) as the primary item and the Maven-style source (`-sources.jar`) as an alternative. Each Process in the fetch chain checks both names before returning null, so local caches find `-sources.jar` files without triggering unnecessary remote lookups for `-src.jar`. If the first call returns null and the artifact has a `nonSemanticVersion`, a second call tries the non-semantic alternative source file (different version directory). If nothing is found, a negative cache marker is published for the primary item. **No renaming or re-publishing** occurs — whichever file is found, its path is returned directly.

- **`loadPOM()`**: Refactored into four methods:
  - `loadPOM(artifact)` — checks `pomCache`, then calls `fetchPOMFile(artifact)` + `processPOM(artifact, cacheKey, file)`.
  - `loadPOM(artifact, preloadedFile)` — checks `pomCache`, then calls `processPOM` directly with the preloaded file (used when POM was found as an alternative to AMD in `fetchMetaData`).
  - `fetchPOMFile(artifact)` — fetches the POM via `fetchWorkflow.fetchItem()`, with non-semantic version fallback.
  - `processPOM(artifact, cacheKey, file)` — parses the POM, recursively resolves parent POMs and BOM imports, caches in `pomCache`, and returns the result.

#### 2.5.6 POM-to-ArtifactMetaData Translation (savant-dependency-management)

Update the existing `translatePOM()` method in `Workflow` to produce an in-memory `ArtifactMetaData` directly, rather than serializing to an AMD file. This method already exists and handles:
- Resolving parent POMs and BOM imports (via `loadPOM()`)
- Mapping Maven dependency scopes to Savant dependency groups
- Handling Maven exclusions and optional dependencies

The additional work is applying semantic version mappings and license information from `build.savant` during translation, which currently happens when the AMD is serialized. This now happens in memory only.

#### 2.5.7 `WorkflowDelegate.java` (savant-core)

Update the `standard()` method to configure the fetch and publish chains with the new process order:

```java
public void standard() {
    String savantCache = System.getProperty("user.home") + "/.savant/cache";
    String mavenCache = System.getProperty("user.home") + "/.m2/repository";

    // Fetch: unified cache (Savant + Maven), then remote Savant repo, then Maven Central
    workflow.fetchWorkflow.processes.add(new CacheProcess(output, savantCache, mavenCache));
    workflow.fetchWorkflow.processes.add(new URLProcess(output, "https://repository.savantbuild.org", null, null));
    workflow.fetchWorkflow.processes.add(new MavenProcess(output, "https://repo1.maven.org/maven2", null, null));

    // Publish: unified cache routes based on FetchResult.source()
    workflow.publishWorkflow.processes.add(new CacheProcess(output, savantCache, mavenCache));
}
```

---

## 3. Handling Special Cases

### 3.1 Savant-Native Artifacts (Non-Maven)

Some artifacts are published to `https://repository.savantbuild.org` in Savant's format (not Maven format). These artifacts already have AMD files in the repository. For these:

- The AMD is fetched from the Savant repo and cached in `~/.savant/cache` (global Savant cache, **not** project-level `.savant/cache`). Savant-sourced AMDs are authoritative and shared across projects.
- The JAR is fetched from the Savant repo and stored in `~/.savant/cache` (global Savant cache, **not** `~/.m2`)
- There is **no POM** for Savant-sourced artifacts. The AMD file is the native metadata format.

This keeps Savant-sourced artifacts fully separate from Maven-sourced artifacts. The directory layout for both Savant and Maven is identical: `group/project/version/name-version.type`, so `~/.savant/cache` uses the same layout as `~/.m2/repository`. The only difference is the file naming, which Savant already handles via `getArtifactFile()` vs `getArtifactNonSemanticFile()`.

### 3.2 Non-Semantic Version Mapping

When a Maven artifact has a non-semantic version (e.g., `4.1.65.Final`), Savant:
1. Downloads the POM using the original version from `~/.m2` or Maven Central
2. Applies the semantic version mapping from `build.savant` at runtime (e.g., `4.1.65.Final` -> `4.1.65`)
3. Constructs in-memory `ArtifactMetaData` with the semantic version
4. The JAR in `~/.m2` uses the **original Maven version** path

The `fetchArtifact` method in `Workflow` tries the semantic version first, then falls back to the non-semantic version. When the non-semantic version is found (e.g., in `~/.m2`), the path to that file is returned directly — **no re-publishing** of a semantic-named copy occurs. This is a behavior change from the current implementation, which copies the JAR to the project-level `.savant/cache` with a semantic filename. Since the project-level cache is eliminated, there's no need to re-publish. The dependency graph operates on semantic versions internally; the file path is simply the location in `~/.m2` with the original Maven version.

### 3.3 Integration Builds

Integration builds (version ending in `-{integration}`) use `~/.savant/cache/` as their cache. This behavior remains unchanged, as integration builds are inherently global (they need to be shared across projects during development).

### 3.4 Negative Caching

Negative cache markers (`.neg` files) are currently used for:
- Source JARs that don't exist (to avoid repeated download attempts)
- POMs that don't exist

Under the new strategy:
- Negative markers for source JARs should be stored in the corresponding global cache (`~/.m2/repository` for Maven-sourced, `~/.savant/cache` for Savant-sourced)
- Negative markers for POMs should be stored in `~/.m2/repository`
- No AMD-related negative markers are needed for Maven-sourced artifacts (no AMDs are generated)

### 3.5 Offline Builds

**Current behavior**: Offline builds work if `.savant/cache` is populated (everything is local to the project).

**New behavior**: Offline builds work if the global caches are populated: `~/.m2/repository` has JARs and POMs (for Maven-sourced artifacts), and `~/.savant/cache` has JARs and AMDs (for Savant-sourced artifacts). Since `~/.m2` is shared across all tools, it's more likely to be populated for Maven-sourced artifacts. Savant-sourced artifacts require a prior Savant build to populate `~/.savant/cache`. No project-level cache is needed.

**Risk**: If a developer clones a project and has never built any Java project on their machine, both global caches will be empty and the first build will require network access. This is the same behavior as Maven/Gradle and is acceptable.

### 3.6 MD5 Verification

MD5 verification for JARs happens at download time (in `URLProcess.fetch()`). This does not change. The verified JAR is written to `~/.m2/repository` and the MD5 file accompanies it.

For Savant-sourced AMD files, the MD5 is fetched alongside the AMD from the Savant repository and both are cached in `~/.savant/cache`. No AMD files are generated for Maven-sourced artifacts.

### 3.7 Publishing Artifacts

When a project publishes its own artifacts (`DependencyService.publish()`), the behavior depends on context:
- **Integration builds**: Published to `~/.savant/cache/` (unchanged)
- **Release builds**: Published to the configured publish workflow (e.g., SVN repo). The JAR and AMD are published to the remote repo and `~/.savant/cache`.

---

## 4. Pros and Cons

### 4.1 Pros

| Benefit                                  | Description                                                                                                         |
|------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| **Reduced disk usage**                   | Eliminates JAR duplication across N projects. Typical savings: 100-500 MB per project.                              |
| **Faster initial setup**                 | New Savant projects immediately benefit from any JARs already in `~/.m2` from Maven/Gradle builds.                  |
| **Simpler mental model**                 | "Savant artifacts in `~/.savant/cache`, Maven artifacts in `~/.m2`, no project cache" is easy to understand.        |
| **Better Maven ecosystem compatibility** | Savant becomes a better citizen in the Maven ecosystem by sharing the same local cache.                             |
| **No project-level cache**               | `.savant/cache` is eliminated entirely. No project directory pollution at all.                                      |
| **Easier CI/CD caching**                 | CI pipelines already cache `~/.m2`. Only need to additionally cache `~/.savant/cache` for Savant-sourced artifacts. |
| **Reduced redundancy**                   | Eliminates the "same JAR in two places" problem that can lead to coherence issues.                                  |

### 4.2 Cons

| Drawback                                 | Description                                                                                                           | Mitigation                                                                                                       |
|------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| **POM parsing overhead per build**       | Maven-sourced POMs are parsed into in-memory ArtifactMetaData on every build (no cached AMDs).                        | POM parsing is fast (small XML files). A `HashMap<Artifact, POM>` cache in `Workflow` avoids redundant parsing within a single build session. |
| **Dependency on global cache integrity** | If `~/.m2` or `~/.savant/cache` is corrupted or cleared, all projects need to re-download.                            | This is the standard Maven/Gradle behavior for `~/.m2`. Users are accustomed to this.                            |
| **Two global caches**                    | Binaries are split between `~/.m2` (Maven-sourced) and `~/.savant/cache` (Savant-sourced), adding a routing decision. | Routing is automatic via `FetchResult.source` — each remote process tags items with its `ItemSource`, and publish processes filter accordingly. No manual routing logic needed. |
| **Cross-repo changes**                   | The change touches `savant-dependency-management`, `savant-core`, and `dependency-plugin`.                            | Phased implementation with backwards compatibility in transition.                                                |
| **Migration for existing projects**      | Existing projects have populated `.savant/cache` directories that become stale.                                       | Provide a migration guide. Old `.savant/cache` can be safely deleted.                                            |

---

## 5. Alternative Solutions Considered

### 5.1 Alternative A: Symlink-Based Cache

**Approach**: Instead of copying JARs to `.savant/cache`, create symlinks pointing to `~/.m2/repository`.

**Pros**: Zero additional disk usage; `.savant/cache` still "looks" populated; minimal code changes.

**Cons**: Windows compatibility issues (symlinks require admin privileges); breaks if `~/.m2` is on a different filesystem; adds complexity for a marginal benefit over the proposed solution; doesn't fundamentally simplify the mental model.

**Verdict**: Rejected. The proposed solution is simpler and more portable.

### 5.2 Alternative B: Cache AMD Files Per-Project

**Approach**: Generate AMD files from Maven POMs and cache them in a project-level `.savant/cache` directory to avoid re-parsing POMs on each build.

**Pros**: Avoids POM re-parsing overhead; AMD cache could be committed to VCS for fully offline builds.

**Cons**: Adds a project-level cache directory (disk pollution, gitignore management); AMD content depends on `build.savant` configuration so cached AMDs become stale when mappings change; added complexity for negligible performance benefit since POM parsing is fast.

**Verdict**: Rejected. The overhead of parsing POMs in memory on each build is negligible, and eliminating the project-level cache entirely is simpler.

### 5.3 Alternative C: Keep Current Architecture, Add Cache Cleanup

**Approach**: Keep `.savant/cache` storing everything but add a `savant clean-cache` command to reduce disk usage.

**Pros**: No architecture changes; simple to implement.

**Cons**: Doesn't solve the fundamental duplication problem; requires manual cleanup; doesn't improve Maven ecosystem integration.

**Verdict**: Rejected. Doesn't address the root cause.

### 5.4 Alternative D: Content-Addressable Cache

**Approach**: Store all artifacts in a content-addressable store (like Git's object store) and use hard links from project caches.

**Pros**: Zero duplication regardless of number of projects; integrity built in.

**Cons**: Significant engineering effort; unfamiliar model for Java developers; doesn't leverage existing `~/.m2` cache; overkill for the problem.

**Verdict**: Rejected. Over-engineered for the use case.

---

## 6. Risk Analysis

### 6.1 Technical Risks

| Risk                                     | Probability | Impact | Mitigation                                                                                                                                                                                         |
|------------------------------------------|-------------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Breaking existing builds**             | Medium      | High   | Feature flag to enable new strategy; keep old behavior as fallback during transition.                                                                                                              |
| **Savant-only artifacts not found**      | Low         | High   | Savant repo artifacts (JARs + AMDs) are cached in `~/.savant/cache`. The fetch chain checks `~/.savant/cache` before `~/.m2`. Test with all known Savant-only artifacts.                           |
| **Non-semantic version path resolution** | Medium      | Medium | The `nonSemanticVersion` field on `Artifact` already tracks the original Maven version. Ensure path construction uses this for `~/.m2` lookups. Comprehensive tests for version-mapped artifacts.  |
| **CI/CD environment differences**        | Low         | Medium | Document that `~/.m2` and `~/.savant/cache` must be writable. Most CI systems already support `~/.m2` via Maven cache configuration; `~/.savant/cache` may need additional CI cache configuration. |
| **Race conditions in shared caches**     | Low         | Low    | Maven itself has this issue with `~/.m2` and handles it via file locking. Savant currently does no locking for either `~/.m2` or `~/.savant/cache`; consider adding advisory locks for writes.     |
| **Integration build isolation**          | Low         | Medium | Integration builds already use `~/.savant/cache`. No change needed. Ensure the new workflow doesn't accidentally route integration artifacts to `~/.m2`.                                           |

### 6.2 Compatibility Risks

| Risk                        | Description                                                                 | Mitigation                                                                                                                                                        |
|-----------------------------|-----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Older Savant versions**   | Projects using older savant-core with the new savant-dependency-management. | The new library should be backwards compatible. Old `WorkflowDelegate.standard()` can create the new workflow structure.                                          |
| **Custom workflows**        | Projects with custom fetch/publish workflows defined in build files.        | Support the existing DSL. Custom workflows continue to work; only the `standard()` shortcut changes.                                                              |
| **Third-party tools**       | Tools that read `.savant/cache` directly.                                   | Document the change. The IDEA plugin and any other tooling that reads from `.savant/cache` for JARs needs updating to read from `~/.m2` and `~/.savant/cache`.    |
| **POM re-parsing overhead** | Maven POMs are parsed on every build instead of reading cached AMDs.        | POM parsing is fast (small XML). A `HashMap<Artifact, POM>` cache in `Workflow` avoids redundant parsing of parent POMs and BOM imports within a single build. |

---

## 7. Phased Implementation Plan

### Phase 1: POM-to-ArtifactMetaData In-Memory Translation (savant-dependency-management)

**Goal**: Implement in-memory POM parsing that produces `ArtifactMetaData` without writing AMD files.

**Changes**:

1. **Create POM translation layer**: A component that takes a Maven POM and `build.savant` configuration (semver mappings, license info) and produces an in-memory `ArtifactMetaData`. This replaces the current "POM -> AMD file -> parse AMD" flow.

2. **Update `Workflow.fetchMetaData()`**: First check `~/.savant/cache` for a Savant-sourced AMD. If not found, fetch the POM from `~/.m2` or Maven Central and translate it in memory.

3. **Remove `CacheProcess` (project-level)**: Eliminate the project-level `.savant/cache` cache process entirely.

4. **Update `CacheProcess`**: Change default `dir` from `.savant/cache` to `~/.savant/cache` so it uses the global Savant cache instead of the project-level cache.

**Tests**: All existing tests refactored. New tests for POM-to-ArtifactMetaData translation with various semver mappings.

### Phase 2: Implement FetchResult-Based Cache Routing (savant-dependency-management)

**Goal**: Route artifacts to the correct global cache based on `FetchResult.source`.

**Changes**:

1. **Introduce `ItemSource` and `FetchResult`**: New enum and record types as described in Section 2.5.1.

2. **Update `Process` interface**: Change `fetch()` to return `FetchResult` and `publish()` to accept `FetchResult` as described in Section 2.5.2.

3. **Update all Process implementations**: `CacheProcess` and `MavenCacheProcess` filter in `publish()` based on `FetchResult.source`. `URLProcess` tags results as `SAVANT`; `MavenProcess` tags results as `MAVEN`.

4. **Update `FetchWorkflow` and `PublishWorkflow`**: Return/accept `FetchResult` as described in Section 2.5.3.

5. **Handle non-semantic version JAR resolution**: When a Maven artifact has a non-semantic version, resolve the JAR from `~/.m2` using the original Maven version path. The semantic version mapping is applied in memory only.

6. **Update negative caching**: Route negative markers to the appropriate global cache. `publishNegative()` constructs a `FetchResult` with the artifact's `ItemSource` so that `CacheProcess` and `MavenCacheProcess` route the `.neg` file to the correct cache.

7. **Stop source JAR renaming**: Currently, when a Maven-style source JAR (`-sources.jar`) is found, it's republished as a Savant-style source (`-src.jar`). Under the new strategy, **no renaming occurs**. `fetchSource()` tries both naming conventions (`-src.jar` then `-sources.jar`) at runtime and returns whichever path exists. This avoids writing duplicate files to `~/.m2` and keeps the Maven cache unmodified.

**Tests**: New tests verifying FetchResult-based routing. Existing tests updated for new Process interface.

### Phase 3: Update Build File DSL (savant-core)

**Goal**: Update the standard workflow and DSL in savant-core to use the new cache strategy.

**Changes**:

1. **Update `WorkflowDelegate.standard()`**: Use the new workflow structure with `CacheProcess` and `MavenCacheProcess`.

2. **Pass `build.savant` configuration to Workflow**: Ensure semver mappings and license info are available during POM translation.

3. **Maintain backward compatibility**: Custom `fetch {}` and `publish {}` blocks should continue to work for projects that override the standard workflow.

**Tests**: Build file parsing tests in savant-core.

### Phase 4: Update Dependency Plugin (dependency-plugin)

**Goal**: Ensure the dependency plugin works correctly with the new workflow.

**Changes**:

1. **Update `integrate()`**: Ensure integration builds still publish to `~/.savant/cache`.

2. **Update classpath construction**: Classpath paths now point to `~/.m2/repository` or `~/.savant/cache` instead of project `.savant/cache`. Verify the `Classpath` object returns correct paths.

3. **Update `copy()`**: The copy target should still work (copying from `~/.m2` or `~/.savant/cache` to the target directory).

**Tests**: End-to-end tests with the dependency plugin.

### Phase 5: Migration and Cleanup

**Goal**: Help existing users migrate.

**Changes**:

1. **Add migration documentation**: Guide for deleting old `.savant/cache` contents.

2. **Version bump**: Release new versions of all three libraries with coordinated version numbers.

3. **Update savantbuild.org docs**: Reflect the new cache strategy.

---

## 8. Comprehensive Test Plan

### 8.1 Unit Tests: Cache Process Behavior (`CacheProcessTest`)

These tests follow the existing `CacheProcessTest` pattern: create test cache directories, pre-populate them with fixture files, and verify fetch/publish behavior. Each test uses `PathTools.prune()` for cleanup in `@BeforeMethod`.

| #  | Test Case                                                                 | Expected Behavior                          |
|----|---------------------------------------------------------------------------|--------------------------------------------|
| 1  | `CacheProcess.fetch()` for an AMD file that exists in `~/.savant/cache`   | Returns `FetchResult(path, SAVANT, item)` with correct path |
| 2  | `CacheProcess.fetch()` for an AMD file that doesn't exist                 | Returns null                               |
| 3  | `CacheProcess.publish()` with `FetchResult(source=SAVANT)`                | Copies file to `~/.savant/cache` directory, returns cached path |
| 4  | `CacheProcess.publish()` with `FetchResult(source=MAVEN)`                 | Returns null (rejects Maven-sourced items), no file written |
| 5  | `CacheProcess.fetch()` for a JAR in `~/.savant/cache`                     | Returns `FetchResult(path, SAVANT, item)`  |
| 6  | `CacheProcess.fetch()` for negative marker (`.neg` file) in `~/.savant/cache` | Throws `NegativeCacheException`       |
| 7  | `CacheProcess.fetch()` for integration version in `~/.savant/cache`       | Returns `FetchResult` using integration dir |
| 8  | `CacheProcess.publish()` overwrites existing file with `FetchResult(source=SAVANT)` | Old file deleted, new file copied, returns path |
| 9  | `CacheProcess.publish()` creates parent directories if missing            | Directories created, file copied successfully |

### 8.1a Unit Tests: MavenCacheProcess Behavior (`MavenCacheProcessTest`)

| #   | Test Case                                                                  | Expected Behavior                          |
|-----|----------------------------------------------------------------------------|--------------------------------------------|
| 10  | `MavenCacheProcess.fetch()` for a JAR in `~/.m2`                          | Returns `FetchResult(path, MAVEN, item)`   |
| 11  | `MavenCacheProcess.fetch()` for a JAR not in `~/.m2`                      | Returns null                               |
| 12  | `MavenCacheProcess.publish()` with `FetchResult(source=MAVEN)`            | Copies file to `~/.m2/repository`, returns path |
| 13  | `MavenCacheProcess.publish()` with `FetchResult(source=SAVANT)`           | Returns null (rejects Savant-sourced items), no file written |
| 14  | `MavenCacheProcess.fetch()` for POM in `~/.m2`                            | Returns `FetchResult(path, MAVEN, item)`   |
| 15  | `MavenCacheProcess.fetch()` for negative marker (`.neg` file) in `~/.m2`  | Throws `NegativeCacheException`            |

### 8.1b Unit Tests: URLProcess and MavenProcess (`URLProcessTest`)

These tests follow the existing `URLProcessTest` pattern: start a local HTTP server via `makeFileServer()` on port 7042, serve files from `test-deps/`, verify download + publish behavior with MD5 verification.

| #   | Test Case                                                                  | Expected Behavior                          |
|-----|----------------------------------------------------------------------------|--------------------------------------------|
| 16  | `URLProcess.fetch()` downloads item from Savant repo                       | Returns `FetchResult(path, SAVANT, item)`, MD5 verified |
| 17  | `URLProcess.fetch()` item not found (404)                                  | Returns null                               |
| 18  | `URLProcess.fetch()` with MD5 mismatch                                     | Throws `MD5Exception`                      |
| 19  | `URLProcess.fetch()` with retry on IOException                             | Retries once after 5s, returns result or throws |
| 20  | `URLProcess.publish()` always throws                                       | Throws `ProcessFailureException`           |
| 21  | `MavenProcess.fetch()` downloads item from Maven repo                      | Returns `FetchResult(path, MAVEN, item)`, MD5 verified |
| 22  | `URLProcess.fetch()` publishes `FetchResult` with correct `ItemSource`     | `publishWorkflow` receives `FetchResult(temp, SAVANT, item)` |
| 23  | `MavenProcess.fetch()` publishes `FetchResult` with correct `ItemSource`   | `publishWorkflow` receives `FetchResult(temp, MAVEN, item)` |
| 24  | `URLProcess.fetch()` with authentication (username/password)               | Basic auth header sent, download succeeds  |

### 8.1c Unit Tests: POM-to-ArtifactMetaData Translation (`MavenToolsTest`, `WorkflowTest`)

| #   | Test Case                                                                  | Expected Behavior                          |
|-----|----------------------------------------------------------------------------|--------------------------------------------|
| 25  | POM-to-ArtifactMetaData translation with valid POM and semver mappings     | Returns correct in-memory ArtifactMetaData |
| 26  | POM-to-ArtifactMetaData translation with non-semantic version and mapping  | Semantic version applied correctly         |
| 27  | POM-to-ArtifactMetaData translation with missing semver mapping            | Throws `VersionException`                  |
| 28  | POM with parent POM — parent fields inherited                              | Group, version inherited from parent when missing |
| 29  | POM with BOM import — dependencies filled from imported POM                | Dependencies from BOM available in result  |
| 30  | POM with property interpolation — `${project.version}` resolved           | Properties expanded before translation     |

### 8.2 Unit Tests: Workflow Routing (`WorkflowTest`)

These tests follow the existing `WorkflowTest` pattern: configure a `Workflow` with test cache dirs and local HTTP server, then verify artifact fetch/publish routing. Tests use `@BeforeMethod` to call `PathTools.prune()` on both cache dirs and `assertFalse(Files.isDirectory(cache))` to verify cleanup.

| #   | Test Case                                                              | Expected Behavior                                                                       |
|-----|------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| 31  | `Workflow.fetchMetaData()` for Savant-sourced artifact (AMD in cache)  | AMD fetched from `~/.savant/cache`, returns parsed `ArtifactMetaData`                   |
| 32  | `Workflow.fetchMetaData()` for Maven-sourced artifact (POM in cache)   | POM fetched from `~/.m2`, translated to in-memory `ArtifactMetaData`, no AMD file written anywhere |
| 33  | `Workflow.fetchMetaData()` for Maven-sourced artifact (POM from remote)| POM downloaded, published to `~/.m2` only, translated in-memory                        |
| 34  | `Workflow.fetchMetaData()` with missing AMD and missing POM            | Throws `ArtifactMetaDataMissingException`                                               |
| 35  | `Workflow.fetchArtifact()` for Savant-sourced artifact (JAR in cache)  | JAR fetched from `~/.savant/cache`                                                      |
| 36  | `Workflow.fetchArtifact()` for Maven-sourced artifact (JAR in cache)   | JAR fetched from `~/.m2`                                                                |
| 37  | `Workflow.fetchArtifact()` for Maven-sourced artifact (JAR from remote)| JAR downloaded, published to `~/.m2` only                                               |
| 38  | `Workflow.fetchArtifact()` with missing artifact                       | Throws `ArtifactMissingException`                                                       |
| 39  | `Workflow.fetchSource()` finds Savant-style source (`-src.jar`)        | Returns path to `-src.jar`, no renaming                                                 |
| 40  | `Workflow.fetchSource()` falls back to Maven-style source (`-sources.jar`) | Tries `-src.jar` first (miss), then `-sources.jar` (hit), returns path directly    |
| 41  | `Workflow.fetchSource()` with no source JAR available                  | Publishes `.neg` marker, returns null                                                   |
| 42  | `Workflow.loadPOM()` uses `pomCache` for repeated access               | First call parses POM, second call returns cached POM without re-fetching               |
| 43  | `Workflow.loadPOM()` with parent POM — both parent and child cached    | Parent POM and child POM both in `pomCache` after first load                            |

### 8.2a Unit Tests: Publish Routing Verification

| #   | Test Case                                                              | Expected Behavior                                                                       |
|-----|------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| 44  | JAR downloaded from Maven Central (`FetchResult.source=MAVEN`)         | JAR appears in `~/.m2` only, not `~/.savant/cache`                                      |
| 45  | JAR downloaded from Savant repo (`FetchResult.source=SAVANT`)          | JAR appears in `~/.savant/cache` only, not `~/.m2`                                      |
| 46  | POM downloaded from Maven Central (`FetchResult.source=MAVEN`)         | POM appears in `~/.m2` only, not `~/.savant/cache`                                      |
| 47  | AMD downloaded from Savant repo (`FetchResult.source=SAVANT`)          | AMD appears in `~/.savant/cache` only, not `~/.m2`                                      |
| 48  | MD5 file published alongside artifact follows same routing             | MD5 routed to same cache as its parent artifact                                         |

### 8.2b Unit Tests: Non-Semantic Version Resolution

| #   | Test Case                                                              | Expected Behavior                                                                       |
|-----|------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| 49  | `fetchArtifact()` with non-semantic version JAR in `~/.m2`             | Tries semantic version first (miss), falls back to non-semantic, returns `~/.m2` path directly |
| 50  | `fetchArtifact()` with non-semantic version — no re-publish            | After resolution, no file exists at the semantic-version path in any cache              |
| 51  | `fetchArtifact()` with non-semantic version from remote                | JAR downloaded with original version, published to `~/.m2`, path returned directly     |
| 52  | `fetchArtifact()` with non-semantic version and no mapping             | Throws `VersionException`                                                               |

### 8.2c Unit Tests: Source JAR Behavior

| #   | Test Case                                                              | Expected Behavior                                                                       |
|-----|------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| 53  | `fetchSource()` finds `-src.jar` in `~/.savant/cache` (Savant-sourced) | Returns path directly, no copy or rename                                                |
| 54  | `fetchSource()` finds `-sources.jar` in `~/.m2` (Maven-sourced)       | Returns `~/.m2` path directly, no `-src.jar` copy created                               |
| 55  | `fetchSource()` with non-semantic version falls back correctly         | Tries semantic then non-semantic, returns path without re-publishing                    |
| 56  | `fetchSource()` negative cache for Maven-sourced artifact              | `.neg` marker in `~/.m2` only                                                           |
| 57  | `fetchSource()` negative cache for Savant-sourced artifact             | `.neg` marker in `~/.savant/cache` only                                                 |
| 58  | `fetchSource()` with existing `.neg` marker short-circuits             | Throws `NegativeCacheException`, no network call                                        |

### 8.3 Integration Tests: End-to-End Resolution (`DefaultDependencyServiceTest`)

These tests follow the existing `DefaultDependencyServiceTest` pattern: build a `DependencyGraph` via `service.buildGraph()`, reduce via `service.reduce()`, resolve via `service.resolve()`, and assert the resulting `ResolvedArtifactGraph` structure. `@BeforeMethod` starts the HTTP server and prunes both cache directories. Tests verify file locations by asserting `Files.isRegularFile()` on expected paths and `!Files.exists()` on paths where files should NOT appear.

| #   | Test Case                                                                  | Expected Behavior                                                                                        |
|-----|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| 59  | Resolve a simple Maven dependency (e.g., `commons-collections:3.2.1`) cold | JAR and POM in `~/.m2`, ArtifactMetaData constructed in memory, no AMD file written                      |
| 60  | Resolve same Maven dependency again (warm cache)                           | POM parsed from `~/.m2` in memory, JAR resolved from `~/.m2`, no network calls                           |
| 61  | Resolve dependency already in `~/.m2` (from Maven build)                   | No download needed; POM parsed in memory                                                                 |
| 62  | Resolve dependency with non-semantic version (e.g., `netty:4.1.65.Final`)  | Semver mapping applied in memory; JAR resolved from `~/.m2` using original Maven version path, no re-published copy |
| 63  | Resolve dependency with transitive dependencies                            | All transitive POMs parsed in memory; all transitive JARs in `~/.m2`                                     |
| 64  | Resolve dependency with parent POM                                         | Parent POM fetched to `~/.m2` and parsed in memory; parent POM cached in `pomCache`                      |
| 65  | Resolve dependency with BOM import                                         | BOM POM in `~/.m2`; dependencies parsed correctly from imported definitions; BOM POM cached in `pomCache` |
| 66  | Resolve Savant-native artifact (from savantbuild.org, not Maven Central)   | AMD in `~/.savant/cache`; JAR in `~/.savant/cache`; nothing in `~/.m2`                                   |
| 67  | Resolve mixed graph (Savant + Maven artifacts in same dependency tree)     | Savant artifacts in `~/.savant/cache`, Maven artifacts in `~/.m2`, no cross-contamination                |

### 8.4 Integration Tests: Version Mapping

| #   | Test Case                                                                 | Expected Behavior                                                              |
|-----|---------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| 68  | Resolve artifact where Maven version == semantic version (e.g., `3.2.1`)  | Direct resolution, no mapping needed                                           |
| 69  | Resolve artifact where Maven version needs mapping (e.g., `4.1.65.Final`) | Mapping applied in memory; JAR found via non-semantic path in `~/.m2`, no re-publish |
| 70  | Resolve artifact where Maven version is simple (e.g., `1.0` -> `1.0.0`)   | Auto-fixed to 3-part semver                                                    |
| 71  | Resolve artifact with range version mapping                               | Range mapping resolved to concrete version                                     |
| 72  | Resolve artifact with missing version mapping for non-semantic version    | Throws `VersionException` with helpful error message                           |

### 8.5 Integration Tests: Negative Caching

| #   | Test Case                                                          | Expected Behavior                                                  |
|-----|--------------------------------------------------------------------|--------------------------------------------------------------------|
| 73  | Source JAR doesn't exist (Maven-sourced); negative marker created  | `.neg` file in `~/.m2` only; subsequent fetches short-circuit via `NegativeCacheException` |
| 74  | Source JAR doesn't exist (Savant-sourced); negative marker created | `.neg` file in `~/.savant/cache` only; subsequent fetches short-circuit |
| 75  | Clear negative marker and retry                                    | Negative marker deleted; fresh fetch attempted                     |
| 76  | Negative marker routing — Maven `.neg` not in `~/.savant/cache`   | Assert `!Files.exists()` on `~/.savant/cache` path for Maven-sourced `.neg` |
| 77  | Negative marker routing — Savant `.neg` not in `~/.m2`            | Assert `!Files.exists()` on `~/.m2` path for Savant-sourced `.neg` |

### 8.6 Integration Tests: Integration Builds

| #   | Test Case                                   | Expected Behavior                                                 |
|-----|---------------------------------------------|-------------------------------------------------------------------|
| 78  | Publish integration build                   | Artifact published to `~/.savant/cache/` with integration version |
| 79  | Resolve integration build dependency        | Fetched from `~/.savant/cache/`                                   |
| 80  | Integration version doesn't pollute `~/.m2` | No integration artifacts in `~/.m2`                               |

### 8.7 Integration Tests: Publishing

| #   | Test Case                   | Expected Behavior                                                  |
|-----|-----------------------------|--------------------------------------------------------------------|
| 81  | Publish a release artifact  | JAR published; source published; all to configured publish targets |
| 82  | Publish with no source file | Negative marker created for source                                 |

### 8.8 Edge Case Tests

| #   | Test Case                                                                                        | Expected Behavior                                                                                                     |
|-----|--------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| 83  | `~/.m2/repository` doesn't exist on first build                                                  | Directory created automatically by `MavenCacheProcess.publish()`                                                      |
| 84  | `~/.savant/cache` doesn't exist on first build                                                   | Directory created automatically by `CacheProcess.publish()`                                                           |
| 85  | JAR exists in `~/.m2` but is corrupted (MD5 mismatch)                                            | Re-downloaded from remote; old file replaced                                                                          |
| 86  | AMD exists in `~/.savant/cache` but is malformed XML                                             | Error thrown with clear message                                                                                       |
| 87  | POM exists in `~/.m2` but is malformed XML                                                       | Error thrown with clear message                                                                                       |
| 88  | Network unavailable but `~/.m2` has all JARs/POMs and `~/.savant/cache` has all Savant artifacts | Build succeeds (offline mode)                                                                                         |
| 89  | Network unavailable and caches are empty                                                         | Build fails with clear error message indicating which artifact is missing                                             |
| 90  | Two projects with different semver mappings for the same Maven artifact                          | Each project applies its own `build.savant` mappings in memory; no conflict (no shared AMD files for Maven artifacts) |
| 91  | Artifact with classifier (e.g., `snappy-java:1.1.10.5`)                                          | Correctly resolved from `~/.m2` using original version path                                                           |
| 92  | Concurrent builds writing to `~/.m2`                                                             | No corruption (file copy is atomic enough for this use case, or add advisory locking)                                 |
| 93  | Very deep transitive dependency tree (10+ levels)                                                | All POMs parsed; all JARs resolved; no stack overflow; `pomCache` avoids redundant parsing                            |
| 94  | Circular dependency detected                                                                     | `CyclicException` thrown (unchanged behavior)                                                                         |
| 95  | Incompatible versions in dependency graph (e.g., major version mismatch)                         | `CompatibilityException` thrown (unchanged behavior)                                                                  |
| 96  | `skipCompatibilityCheck` flag honored                                                            | Incompatible versions allowed when flag is set                                                                        |
| 97  | Delete `~/.m2/repository` between builds                                                         | JARs and POMs re-downloaded from Maven Central; build succeeds with network                                           |
| 98  | Delete `~/.savant/cache` between builds                                                          | Savant artifacts re-downloaded from Savant repo; build succeeds with network                                          |
| 99  | Artifact with exclusions                                                                         | Exclusions honored in dependency graph; excluded artifacts not resolved                                               |
| 100 | Custom workflow overrides in build file                                                          | Custom `fetch {}` / `publish {}` blocks override standard behavior                                                    |
| 101 | `pomCache` does not persist across `Workflow` instances                                          | New `Workflow` starts with empty `pomCache`; no stale POM data                                                        |
| 102 | Non-semantic JAR path returned to `ResolvedArtifactGraph`                                        | `ResolvedArtifact.file` points to `~/.m2/.../name-4.1.65.Final.jar`, not a semantic-named copy                       |

### 8.9 Performance Tests

| #   | Test Case                         | Expected Behavior                                                                      |
|-----|-----------------------------------|----------------------------------------------------------------------------------------|
| 103 | Cold build (empty caches)         | Performance comparable to current behavior (network-bound)                             |
| 104 | Warm build (all caches populated) | Comparable to current behavior; POM re-parsing adds negligible overhead                |
| 105 | Build with 200+ dependencies      | Completes in reasonable time; no excessive memory usage from in-memory POM translation |
| 106 | `pomCache` prevents redundant POM parsing during deep transitive resolution | Parent POMs shared by multiple artifacts only parsed once per build session |

### 8.10 Backward Compatibility Tests

| #   | Test Case                                         | Expected Behavior                           |
|-----|---------------------------------------------------|---------------------------------------------|
| 107 | Custom `fetch {}` with explicit `cache()` process | Works as before                             |
| 108 | Workflow with only `maven()` (no Savant cache)    | Direct Maven resolution works               |
| 109 | Workflow with only Savant cache (no Maven)        | Savant-only resolution works                |
| 110 | `CacheProcess` default dir is now `~/.savant/cache` (breaking change) | Tests verify new default; old `.savant/cache` project-level path no longer used |

---

## 9. Migration Guide (Draft)

### For Existing Users

1. Update `savant-dependency-management`, `savant-core`, and `dependency-plugin` to the new versions.
2. If using `workflow { standard() }` in your build file, the new cache strategy is automatic.
3. Delete your old `.savant/cache` directory to reclaim disk space: `rm -rf .savant/cache`
4. Your `~/.m2/repository` and `~/.savant/cache` will be populated on the next build.
5. Ensure your `build.savant` has correct semantic version mappings for all non-semantic Maven versions (these are now applied on every build rather than cached in AMD files).
6. If using custom workflows, review the updated documentation.

### For CI/CD Pipelines

1. Cache `~/.m2/repository` between builds (most CI systems already do this for Maven).
2. Cache `~/.savant/cache` between builds for Savant-sourced artifacts.
3. No other changes needed.

---

## 10. Open Questions

1. ~~**Should `.savant/cache` be committable to VCS?**~~ **No longer applicable.** The project-level `.savant/cache` has been eliminated. Maven POMs are parsed in memory; Savant AMDs are cached globally.

2. **Should we add a `savant cache clean` command?** For cleaning `~/.savant/cache` and/or `~/.m2` Savant-related artifacts. **Recommendation**: Yes, in a future release.

3. ~~**How to handle the Savant repository (`repository.savantbuild.org`)?**~~ **Resolved.** Savant-sourced artifacts (JARs + AMDs, no POMs) are cached in `~/.savant/cache`. The fetch chain checks `~/.savant/cache` first, then `~/.m2`, then remote repos. URLProcess downloads to `~/.savant/cache` via the Savant publish workflow.

4. **Should the IDEA plugin be updated?** If there's a Savant IDEA plugin that reads from `.savant/cache` for JARs, it needs to be updated to read from `~/.m2` and `~/.savant/cache`. **Recommendation**: Yes, in a coordinated release.

5. **Should AMD files include a schema version?** To handle future format changes gracefully. **Recommendation**: Consider adding a version attribute to the `<artifact-meta-data>` root element. (Only applies to Savant-sourced AMDs in `~/.savant/cache`.)

6. ~~**Performance impact of POM re-parsing?**~~ **Resolved.** A `HashMap<Artifact, POM>` cache is added to `Workflow` to avoid redundant POM parsing within a single build session. This handles the case where parent POMs and BOM imports are referenced multiple times during recursive resolution. The map is not bounded (no LRU) — it grows for the build duration and is discarded with the `Workflow` instance.