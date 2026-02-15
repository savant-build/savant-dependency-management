# Savant Dependency Management: New Cache Strategy

## Specification Document

**Date:** 2026-02-11
**Status:** DRAFT
**Branch:** `new_cache_strat`
**Affected Repos:** `savant-dependency-management`, `savant-core` (WorkflowDelegate), `dependency-plugin`

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

```
Fetch Metadata:  CacheProcess(~/.savant/cache) -> URLProcess(savantbuild.org) -> [parse POM in memory if not found]
Fetch POM:       MavenCacheProcess(~/.m2) -> MavenProcess(maven central)
Fetch JAR:       CacheProcess(~/.savant/cache) -> MavenCacheProcess(~/.m2) -> URLProcess(savantbuild.org) -> MavenProcess(maven central)
Fetch Source:    CacheProcess(~/.savant/cache) -> MavenCacheProcess(~/.m2) -> URLProcess(savantbuild.org) -> MavenProcess(maven central)

Publish Savant:  CacheProcess(~/.savant/cache)
Publish Maven:   MavenCacheProcess(~/.m2)
```

**Note:** Savant-sourced artifacts have AMD files natively (no POMs), which are cached in `~/.savant/cache`. Maven-sourced artifacts have POMs that are parsed directly into in-memory `ArtifactMetaData` on each build, applying semantic version mappings and license information from `build.savant`. No AMD files are generated or cached for Maven-sourced artifacts.

### 2.4 Detailed Flow: Dependency Resolution

#### Step 1: Fetch Metadata

```
fetchMetaData("org.apache.groovy:groovy:4.0.5"):
  1. Check ~/.savant/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar.amd
     -> If found: parse AMD and return ArtifactMetaData (Savant-sourced, cached locally)
  2. Try fetching AMD from configured remote Savant repositories (e.g., repository.savantbuild.org)
     -> If found: cache AMD in ~/.savant/cache and return ArtifactMetaData
  3. If not found in any Savant source (Maven-sourced artifact):
     a. Fetch POM from ~/.m2 or Maven Central (download to ~/.m2 if needed)
     b. Parse POM, resolve parent POMs and imports
     c. Apply semantic version mappings from build.savant (e.g., 4.1.65.Final -> 4.1.65)
     d. Apply license information from build.savant
     e. Translate POM dependencies into Savant dependency groups (compile, runtime, etc.)
     f. Return in-memory ArtifactMetaData (no AMD file written to disk)
```

#### Step 2: Build Dependency Graph

The `DependencyGraph` is built by recursively fetching metadata for all transitive dependencies. For Savant-sourced artifacts, this reads cached AMD files from `~/.savant/cache`. For Maven-sourced artifacts, this parses POMs in memory on each build.

#### Step 3: Reduce Graph

No change. Version compatibility checking and selection of highest compatible version.

#### Step 4: Resolve Artifacts (JARs)

```
fetchArtifact("org.apache.groovy:groovy:4.0.5"):
  1. Check ~/.savant/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar
     -> If found: return path (Savant-sourced artifact)
  2. Check ~/.m2/repository/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar
     -> If found: return path (Maven-sourced artifact)
  3. If not found in either global cache:
     a. Try Savant repo (repository.savantbuild.org) -> download to ~/.savant/cache/...
     b. Try Maven Central -> download to ~/.m2/repository/...
     c. Return path from whichever succeeded
  4. (No copy to project .savant/cache)
```

### 2.5 Key Code Changes

#### 2.5.1 `Workflow.java` (savant-dependency-management)

The `Workflow` class retains its existing structure with `fetchWorkflow` and `publishWorkflow` fields unchanged. The routing logic changes are entirely within the `Workflow` methods:

- **`fetchMetaData()`**: Currently tries the fetch workflow for an AMD, then falls back to POM loading and translation. The new behavior: try the fetch workflow for an AMD (which checks `~/.savant/cache` then remote Savant repos), and if not found, fetch the POM via the fetch workflow (which checks `~/.m2` then Maven Central), parse it in memory, and return the in-memory `ArtifactMetaData`. No AMD file is written.

- **`fetchArtifact()`**: Uses the fetch workflow as-is. The process chain tries `~/.savant/cache`, then `~/.m2`, then remote repos. Each process that successfully fetches calls `publishWorkflow` to cache the result in the appropriate location.

- **`fetchSource()`**: Same as `fetchArtifact()` -- uses the fetch workflow directly.

- **`loadPOM()`**: Uses the fetch workflow to find POMs (only `MavenCacheProcess` and `MavenProcess` will return results for POM items; `CacheProcess` and `URLProcess` will return null since Savant repos don't have POMs).

The single `fetchWorkflow` contains all processes in order. Each process is responsible for returning null for item types it doesn't handle, allowing the chain to continue to the next process.

#### 2.5.2 `CacheProcess.java` (savant-dependency-management)

Update the existing `CacheProcess` to use `~/.savant/cache` as its default `dir` instead of the project-level `.savant/cache`. Currently `CacheProcess` has two directories:
- `dir` -- defaults to `.savant/cache` (project-level, used for non-integration builds)
- `integrationDir` -- defaults to `~/.savant/cache` (global, used for integration builds)

After the change, `dir` defaults to `~/.savant/cache` (same as `integrationDir`), eliminating the project-level cache. The integration routing logic in `fetch()` and `publish()` may be simplified since both paths now point to the same global location.

`CacheProcess` handles Savant-sourced artifacts (JARs + AMDs) and returns null for item types it doesn't handle (e.g., POMs), allowing the fetch chain to continue. `MavenCacheProcess` continues to handle `~/.m2/repository` for Maven-sourced artifacts (JARs + POMs) and returns null for Savant-specific item types (e.g., AMDs).

Each process in the publish workflow similarly decides whether to accept a given item. When `URLProcess` fetches a Savant artifact and calls `publishWorkflow.publish()`, `CacheProcess` accepts and caches it while `MavenCacheProcess` skips it (and vice versa for Maven-sourced artifacts).

#### 2.5.3 POM-to-ArtifactMetaData Translation (savant-dependency-management)

Update the existing `translatePOM()` method in `Workflow` to produce an in-memory `ArtifactMetaData` directly, rather than serializing to an AMD file. This method already exists and handles:
- Resolving parent POMs and BOM imports (via `loadPOM()`)
- Mapping Maven dependency scopes to Savant dependency groups
- Handling Maven exclusions and optional dependencies

The additional work is applying semantic version mappings and license information from `build.savant` during translation, which currently happens when the AMD is serialized. This now happens in memory only.

#### 2.5.4 `WorkflowDelegate.java` (savant-core)

Update the `standard()` method to configure the fetch and publish chains with the new process order:

```java
public void standard() {
    // Fetch: Savant global cache, then Maven cache, then remote Savant repo, then Maven Central
    workflow.fetchWorkflow.processes.add(new CacheProcess(output, null, null));
    workflow.fetchWorkflow.processes.add(new MavenCacheProcess(output, null, null));
    workflow.fetchWorkflow.processes.add(new URLProcess(output, "https://repository.savantbuild.org", null, null));
    workflow.fetchWorkflow.processes.add(new MavenProcess(output, "https://repo1.maven.org/maven2", null, null));

    // Publish: each process decides whether to accept the item
    workflow.publishWorkflow.processes.add(new CacheProcess(output, null, null));
    workflow.publishWorkflow.processes.add(new MavenCacheProcess(output, null, null));
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

The `fetchArtifact` method in `Workflow` already handles this dual-lookup (semantic first, then non-semantic fallback) -- see `Workflow.java:77-97`. The JAR is resolved from `~/.m2` using the original version path. No AMD file is written; the mapping is applied in memory each build.

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
| **POM parsing overhead per build**       | Maven-sourced POMs are parsed into in-memory ArtifactMetaData on every build (no cached AMDs).                        | POM parsing is fast (small XML files). The overhead is negligible compared to network I/O for cold fetches.      |
| **Dependency on global cache integrity** | If `~/.m2` or `~/.savant/cache` is corrupted or cleared, all projects need to re-download.                            | This is the standard Maven/Gradle behavior for `~/.m2`. Users are accustomed to this.                            |
| **Two global caches**                    | Binaries are split between `~/.m2` (Maven-sourced) and `~/.savant/cache` (Savant-sourced), adding a routing decision. | The routing is determined by which remote process successfully fetches the artifact. The fetch chain tries both. |
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
| **POM re-parsing overhead** | Maven POMs are parsed on every build instead of reading cached AMDs.        | POM parsing is fast (small XML). Profile to confirm. If needed, an in-memory LRU cache within a single build session can avoid redundant parsing of the same POM. |

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

### Phase 2: Implement New Cache Routing (savant-dependency-management)

**Goal**: Route artifacts to the correct global cache based on source.

**Changes**:

1. **Update `Workflow.fetchArtifact()`**: Check `~/.savant/cache` first, then `~/.m2`, then remote repos. Route downloads to the correct cache based on which remote process fetched them.

2. **Handle non-semantic version JAR resolution**: When a Maven artifact has a non-semantic version, resolve the JAR from `~/.m2` using the original Maven version path. The semantic version mapping is applied in memory only.

3. **Update negative caching**: Route negative markers to the appropriate global cache (`~/.m2` for Maven-sourced, `~/.savant/cache` for Savant-sourced).

4. **Handle source JAR republishing**: Currently, when a Maven-style source JAR (`-sources.jar`) is found, it's republished as a Savant-style source (`-src.jar`). Under the new strategy, consider whether to maintain this renaming in `~/.m2` or handle it as a name resolution at build time.

**Tests**: New tests verifying correct routing. Existing tests updated.

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

### 8.1 Unit Tests: Cache Process Behavior

| #  | Test Case                                                                 | Expected Behavior                          |
|----|---------------------------------------------------------------------------|--------------------------------------------|
| 1  | `CacheProcess.fetch()` for an AMD file that exists in `~/.savant/cache`   | Returns the path to the cached AMD file    |
| 2  | `CacheProcess.fetch()` for an AMD file that doesn't exist                 | Returns null                               |
| 3  | `CacheProcess.publish()` for an AMD file                                  | Writes AMD to `~/.savant/cache` directory  |
| 4  | `CacheProcess.fetch()` for a JAR in `~/.savant/cache`                     | Returns the path (Savant-sourced artifact) |
| 5  | `CacheProcess.publish()` for a JAR                                        | Writes JAR to `~/.savant/cache`            |
| 6  | `MavenCacheProcess.fetch()` for a JAR in `~/.m2`                          | Returns the path in `~/.m2`                |
| 7  | `MavenCacheProcess.fetch()` for a JAR not in `~/.m2`                      | Returns null                               |
| 8  | `MavenCacheProcess.publish()` for a JAR                                   | Writes JAR to `~/.m2/repository`           |
| 9  | POM-to-ArtifactMetaData translation with valid POM and semver mappings    | Returns correct in-memory ArtifactMetaData |
| 10 | POM-to-ArtifactMetaData translation with non-semantic version and mapping | Semantic version applied correctly         |
| 11 | POM-to-ArtifactMetaData translation with missing semver mapping           | Throws `VersionException`                  |
| 12 | Negative cache marker in `~/.m2` for source JAR                           | Throws `NegativeCacheException`            |
| 13 | Negative cache marker in `~/.savant/cache` for source JAR                 | Throws `NegativeCacheException`            |

### 8.2 Unit Tests: Workflow Routing

| #  | Test Case                                                              | Expected Behavior                                                                       |
|----|------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| 14 | `Workflow.fetchMetaData()` for Savant-sourced artifact                 | AMD fetched from `~/.savant/cache`                                                      |
| 15 | `Workflow.fetchMetaData()` for Maven-sourced artifact                  | POM fetched from `~/.m2`, translated to in-memory ArtifactMetaData, no AMD file written |
| 16 | `Workflow.fetchArtifact()` for Savant-sourced artifact                 | JAR fetched from `~/.savant/cache`                                                      |
| 17 | `Workflow.fetchArtifact()` for Maven-sourced artifact                  | JAR fetched from `~/.m2`                                                                |
| 18 | `Workflow.fetchSource()` routes through artifact workflow              | Source JAR fetched from `~/.m2` or `~/.savant/cache`                                    |
| 19 | `Workflow.loadPOM()` routes through Maven cache                        | POM fetched from `~/.m2`                                                                |
| 20 | JAR downloaded from Maven Central is published to `~/.m2` only         | JAR appears in `~/.m2`, not `~/.savant/cache`                                           |
| 21 | JAR downloaded from Savant repo is published to `~/.savant/cache` only | JAR appears in `~/.savant/cache`, not `~/.m2`                                           |
| 22 | POM downloaded from Maven Central is published to `~/.m2` only         | POM appears in `~/.m2`, not `~/.savant/cache`                                           |

### 8.3 Integration Tests: End-to-End Resolution

| #  | Test Case                                                                  | Expected Behavior                                                                                        |
|----|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| 23 | Resolve a simple Maven dependency (e.g., `commons-collections:3.2.1`) cold | JAR and POM in `~/.m2`, ArtifactMetaData constructed in memory, no AMD file written                      |
| 24 | Resolve same Maven dependency again (warm cache)                           | POM parsed from `~/.m2` in memory, JAR resolved from `~/.m2`, no network calls                           |
| 25 | Resolve dependency already in `~/.m2` (from Maven build)                   | No download needed; POM parsed in memory                                                                 |
| 26 | Resolve dependency with non-semantic version (e.g., `netty:4.1.65.Final`)  | Semver mapping from `build.savant` applied in memory; JAR resolved from `~/.m2` using Maven version path |
| 27 | Resolve dependency with transitive dependencies                            | All transitive POMs parsed in memory; all transitive JARs in `~/.m2`                                     |
| 28 | Resolve dependency with parent POM                                         | Parent POM fetched to `~/.m2` and parsed in memory                                                       |
| 29 | Resolve dependency with BOM import                                         | BOM POM in `~/.m2`; dependencies parsed correctly from imported definitions                              |
| 30 | Resolve Savant-native artifact (from savantbuild.org, not Maven Central)   | AMD in `~/.savant/cache`; JAR in `~/.savant/cache`                                                       |

### 8.4 Integration Tests: Version Mapping

| #  | Test Case                                                                 | Expected Behavior                                                              |
|----|---------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| 31 | Resolve artifact where Maven version == semantic version (e.g., `3.2.1`)  | Direct resolution, no mapping needed                                           |
| 32 | Resolve artifact where Maven version needs mapping (e.g., `4.1.65.Final`) | Mapping from `build.savant` applied in memory; JAR found via non-semantic path |
| 33 | Resolve artifact where Maven version is simple (e.g., `1.0` -> `1.0.0`)   | Auto-fixed to 3-part semver                                                    |
| 34 | Resolve artifact with range version mapping                               | Range mapping resolved to concrete version                                     |
| 35 | Resolve artifact with missing version mapping for non-semantic version    | Throws `VersionException` with helpful error message                           |

### 8.5 Integration Tests: Negative Caching

| #  | Test Case                                                          | Expected Behavior                                                  |
|----|--------------------------------------------------------------------|--------------------------------------------------------------------|
| 36 | Source JAR doesn't exist (Maven-sourced); negative marker created  | `.neg` file in `~/.m2`; subsequent fetches short-circuit           |
| 37 | Source JAR doesn't exist (Savant-sourced); negative marker created | `.neg` file in `~/.savant/cache`; subsequent fetches short-circuit |
| 38 | Clear negative marker and retry                                    | Negative marker deleted; fresh fetch attempted                     |

### 8.6 Integration Tests: Integration Builds

| #  | Test Case                                   | Expected Behavior                                                 |
|----|---------------------------------------------|-------------------------------------------------------------------|
| 39 | Publish integration build                   | Artifact published to `~/.savant/cache/` with integration version |
| 40 | Resolve integration build dependency        | Fetched from `~/.savant/cache/`                                   |
| 41 | Integration version doesn't pollute `~/.m2` | No integration artifacts in `~/.m2`                               |

### 8.7 Integration Tests: Publishing

| #  | Test Case                   | Expected Behavior                                                  |
|----|-----------------------------|--------------------------------------------------------------------|
| 42 | Publish a release artifact  | JAR published; source published; all to configured publish targets |
| 43 | Publish with no source file | Negative marker created for source                                 |

### 8.8 Edge Case Tests

| #  | Test Case                                                                                        | Expected Behavior                                                                                                     |
|----|--------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| 44 | `~/.m2/repository` doesn't exist on first build                                                  | Directory created automatically                                                                                       |
| 45 | `~/.savant/cache` doesn't exist on first build                                                   | Directory created automatically                                                                                       |
| 46 | JAR exists in `~/.m2` but is corrupted (MD5 mismatch)                                            | Re-downloaded from remote; old file replaced                                                                          |
| 47 | AMD exists in `~/.savant/cache` but is malformed XML                                             | Error thrown with clear message                                                                                       |
| 48 | POM exists in `~/.m2` but is malformed XML                                                       | Error thrown with clear message                                                                                       |
| 49 | Network unavailable but `~/.m2` has all JARs/POMs and `~/.savant/cache` has all Savant artifacts | Build succeeds (offline mode)                                                                                         |
| 50 | Network unavailable and caches are empty                                                         | Build fails with clear error message indicating which artifact is missing                                             |
| 51 | Two projects with different semver mappings for the same Maven artifact                          | Each project applies its own `build.savant` mappings in memory; no conflict (no shared AMD files for Maven artifacts) |
| 52 | Artifact with classifier (e.g., `snappy-java:1.1.10.5`)                                          | Correctly resolved from `~/.m2` using original version path                                                           |
| 53 | Concurrent builds writing to `~/.m2`                                                             | No corruption (file copy is atomic enough for this use case, or add advisory locking)                                 |
| 54 | Very deep transitive dependency tree (10+ levels)                                                | All POMs parsed; all JARs resolved; no stack overflow                                                                 |
| 55 | Circular dependency detected                                                                     | `CyclicException` thrown (unchanged behavior)                                                                         |
| 56 | Incompatible versions in dependency graph (e.g., major version mismatch)                         | `CompatibilityException` thrown (unchanged behavior)                                                                  |
| 57 | `skipCompatibilityCheck` flag honored                                                            | Incompatible versions allowed when flag is set                                                                        |
| 58 | Delete `~/.m2/repository` between builds                                                         | JARs and POMs re-downloaded from Maven Central; build succeeds with network                                           |
| 59 | Delete `~/.savant/cache` between builds                                                          | Savant artifacts re-downloaded from Savant repo; build succeeds with network                                          |
| 60 | Artifact with exclusions                                                                         | Exclusions honored in dependency graph; excluded artifacts not resolved                                               |
| 61 | Custom workflow overrides in build file                                                          | Custom `fetch {}` / `publish {}` blocks override standard behavior                                                    |

### 8.9 Performance Tests

| #  | Test Case                         | Expected Behavior                                                                      |
|----|-----------------------------------|----------------------------------------------------------------------------------------|
| 62 | Cold build (empty caches)         | Performance comparable to current behavior (network-bound)                             |
| 63 | Warm build (all caches populated) | Comparable to current behavior; POM re-parsing adds negligible overhead                |
| 64 | Build with 200+ dependencies      | Completes in reasonable time; no excessive memory usage from in-memory POM translation |

### 8.10 Backward Compatibility Tests

| #  | Test Case                                         | Expected Behavior                           |
|----|---------------------------------------------------|---------------------------------------------|
| 65 | Old-style `Workflow` constructor still works      | Backward-compatible; old behavior preserved |
| 66 | Custom `fetch {}` with explicit `cache()` process | Works as before                             |
| 67 | Workflow with only `maven()` (no Savant cache)    | Direct Maven resolution works               |
| 68 | Workflow with only Savant cache (no Maven)        | Savant-only resolution works                |

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

6. **Performance impact of POM re-parsing?** Maven POMs are parsed in memory on every build instead of reading cached AMDs. Need to profile with large dependency trees (200+ deps) to confirm the overhead is acceptable. **Recommendation**: Profile during Phase 1 implementation. If needed, add an in-memory LRU cache within a single build session.