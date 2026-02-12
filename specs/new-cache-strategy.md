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
Publish: CacheProcess(.savant/cache) -> MavenCacheProcess(~/.m2)
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

Everything else in `.savant/cache` (JARs, POMs, source JARs, MD5s) is a duplicate of what exists or can be fetched into `~/.m2/repository`.

---

## 2. Proposed Solution

### 2.1 Design Principles

1. **Global cache is the single source of truth for binaries**: `~/.m2/repository` stores all JARs, POMs, source JARs, and MD5 files. This is shared across all projects and all build tools (Maven, Gradle, Savant).

2. **Project cache stores only AMD mappings**: `.savant/cache` stores only `.amd` files and their `.amd.md5` checksums. These are the Savant-specific metadata that bridges Savant's semantic versioning to Maven's artifact layout.

3. **JARs are resolved from `~/.m2` on every build**: Rather than caching JARs locally, Savant resolves the path to each JAR in `~/.m2/repository` at build time. This is a simple path computation (not a file copy) and adds negligible overhead.

4. **Simplicity over optimization**: The design prioritizes simplicity and compatibility with the Maven ecosystem over micro-optimizations for cold-start performance.

### 2.2 New Architecture

| Level | Location | Contents | Scope |
|-------|----------|----------|-------|
| 1. Project AMD cache | `.savant/cache/` | AMD files + AMD MD5s only | Per-project |
| 2. Global artifact cache | `~/.m2/repository/` | JARs, POMs, source JARs, MD5s | Global (user) |
| 3. Integration cache | `~/.savant/cache/` | Integration build artifacts (full) | Global (user) |
| 4. Remote repos | Savant repo + Maven Central | Everything | Network |

### 2.3 New Standard Workflow

```
Fetch AMD:       AmdCacheProcess(.savant/cache) -> [generate from POM if missing]
Fetch POM:       MavenCacheProcess(~/.m2) -> MavenProcess(maven central)
Fetch JAR:       MavenCacheProcess(~/.m2) -> MavenProcess(maven central)
Fetch Source:    MavenCacheProcess(~/.m2) -> MavenProcess(maven central)

Publish AMD:     AmdCacheProcess(.savant/cache)
Publish Others:  MavenCacheProcess(~/.m2)
```

### 2.4 Detailed Flow: Dependency Resolution

#### Step 1: Fetch Metadata (AMD)

```
fetchMetaData("org.apache.groovy:groovy:4.0.5"):
  1. Check .savant/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar.amd
     -> If found: parse and return ArtifactMetaData
  2. If not found:
     a. Fetch POM from ~/.m2 or Maven Central (download to ~/.m2 if needed)
     b. Parse POM, resolve parent POMs and imports
     c. Translate POM -> ArtifactMetaData (apply semver mappings, licenses, groups)
     d. Serialize AMD to .savant/cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar.amd
     e. Return ArtifactMetaData
```

#### Step 2: Build Dependency Graph

No change from current behavior. The `DependencyGraph` is built by recursively fetching AMD files for all transitive dependencies.

#### Step 3: Reduce Graph

No change. Version compatibility checking and selection of highest compatible version.

#### Step 4: Resolve Artifacts (JARs)

```
fetchArtifact("org.apache.groovy:groovy:4.0.5"):
  1. Check ~/.m2/repository/org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar
     -> If found: return path
  2. If not found:
     a. Download from Maven Central to ~/.m2/repository/...
     b. Return path in ~/.m2
  3. (No copy to .savant/cache)
```

### 2.5 Key Code Changes

#### 2.5.1 `Workflow.java` (savant-dependency-management)

The `Workflow` class needs to be refactored to use **separate workflow chains** for different item types:

- **AMD workflow**: Fetch from `.savant/cache` -> Generate from POM if missing; Publish to `.savant/cache` only
- **Artifact workflow**: Fetch from `~/.m2` -> Download from remote; Publish to `~/.m2` only
- **POM workflow**: Fetch from `~/.m2` -> Download from remote; Publish to `~/.m2` only
- **Source workflow**: Fetch from `~/.m2` -> Download from remote; Publish to `~/.m2` only

The current design has a single `FetchWorkflow` and `PublishWorkflow` for all item types. The refactoring introduces type-awareness.

**Approach A (Recommended): Modify Workflow to route by item type**

```java
public class Workflow {
  // AMD-specific workflows
  public final FetchWorkflow amdFetchWorkflow;
  public final PublishWorkflow amdPublishWorkflow;

  // Artifact/POM/Source workflows (global cache)
  public final FetchWorkflow artifactFetchWorkflow;
  public final PublishWorkflow artifactPublishWorkflow;

  // ... existing fields (mappings, rangeMappings, output)
}
```

**Approach B (Alternative): Keep single workflow, change CacheProcess behavior**

Modify `CacheProcess` to only cache AMD files, and pass all other items through without caching locally. This is simpler but less explicit.

#### 2.5.2 `CacheProcess.java` (savant-dependency-management)

Under Approach A, create a new `AmdCacheProcess` that:
- Only handles `.amd` and `.amd.md5` files
- Uses `.savant/cache` as its directory
- Rejects (passes through) non-AMD items

Under Approach B, modify existing `CacheProcess` to filter by item type.

#### 2.5.3 `WorkflowDelegate.java` (savant-core)

Update the `standard()` method and the DSL to reflect the new workflow structure:

```java
public void standard() {
    // AMD fetch: project cache only
    workflow.amdFetchWorkflow.processes.add(new CacheProcess(output, null, null));

    // Artifact fetch: Maven cache then Maven Central
    workflow.artifactFetchWorkflow.processes.add(new MavenCacheProcess(output, null, null));
    workflow.artifactFetchWorkflow.processes.add(new MavenProcess(output, "https://repo1.maven.org/maven2", null, null));

    // Savant repo for Savant-native artifacts
    workflow.artifactFetchWorkflow.processes.add(new URLProcess(output, "https://repository.savantbuild.org", null, null));

    // AMD publish: project cache only
    workflow.amdPublishWorkflow.processes.add(new CacheProcess(output, null, null));

    // Artifact publish: Maven cache only
    workflow.artifactPublishWorkflow.processes.add(new MavenCacheProcess(output, null, null));
}
```

#### 2.5.4 `DefaultDependencyService.java` (savant-dependency-management)

Minimal changes. The service delegates to `Workflow` for fetching; the routing logic lives in `Workflow`.

#### 2.5.5 `DependencyPlugin.groovy` (dependency-plugin)

Update the `integrate()` method and any workflow construction to use the new workflow structure.

---

## 3. Handling Special Cases

### 3.1 Savant-Native Artifacts (Non-Maven)

Some artifacts are published to `https://repository.savantbuild.org` in Savant's format (not Maven format). These artifacts already have AMD files in the repository. For these:

- The AMD is fetched from the Savant repo and cached in `.savant/cache`
- The JAR is fetched from the Savant repo and stored in `~/.m2/repository` (normalized to Maven layout)

This works because the directory layout for both Savant and Maven is identical: `group/project/version/name-version.type`. The only difference is the file naming, which Savant already handles via `getArtifactFile()` vs `getArtifactNonSemanticFile()`.

### 3.2 Non-Semantic Version Mapping

When a Maven artifact has a non-semantic version (e.g., `4.1.65.Final`), Savant:
1. Downloads the POM using the original version from `~/.m2` or Maven Central
2. Creates the AMD with the semantic version mapping
3. Stores the AMD in `.savant/cache` using the **semantic version** path
4. The JAR in `~/.m2` uses the **original Maven version** path

The `fetchArtifact` method in `Workflow` already handles this dual-lookup (semantic first, then non-semantic fallback) -- see `Workflow.java:77-97`. The change is that instead of re-publishing a semantic-named copy to `.savant/cache`, it returns the path to the JAR in `~/.m2` using the original version path.

### 3.3 Integration Builds

Integration builds (version ending in `-{integration}`) use `~/.savant/cache/` as their cache. This behavior remains unchanged, as integration builds are inherently global (they need to be shared across projects during development).

### 3.4 Negative Caching

Negative cache markers (`.neg` files) are currently used for:
- Source JARs that don't exist (to avoid repeated download attempts)
- POMs that don't exist

Under the new strategy:
- Negative markers for source JARs should be stored in `~/.m2/repository` (since that's where sources live)
- Negative markers for AMDs can be stored in `.savant/cache`
- Negative markers for POMs can be stored in `~/.m2/repository`

### 3.5 Offline Builds

**Current behavior**: Offline builds work if `.savant/cache` is populated (everything is local to the project).

**New behavior**: Offline builds work if `~/.m2/repository` is populated (JARs are there) AND `.savant/cache` has AMDs. Since `~/.m2` is shared across all tools, it's more likely to be populated than a per-project cache.

**Risk**: If a developer clones a project and has never built any Java project on their machine, `~/.m2` will be empty and the first build will require network access. This is the same behavior as Maven/Gradle and is acceptable.

### 3.6 MD5 Verification

MD5 verification for JARs happens at download time (in `URLProcess.fetch()`). This does not change. The verified JAR is written to `~/.m2/repository` and the MD5 file accompanies it.

For AMD files, the MD5 is generated when Savant creates the AMD from a POM translation. The AMD and its MD5 are stored together in `.savant/cache`.

### 3.7 Publishing Artifacts

When a project publishes its own artifacts (`DependencyService.publish()`), the behavior depends on context:
- **Integration builds**: Published to `~/.savant/cache/` (unchanged)
- **Release builds**: Published to the configured publish workflow (e.g., SVN repo). The local AMD cache in `.savant/cache` is updated. The JAR is published to the remote repo and `~/.m2`.

---

## 4. Pros and Cons

### 4.1 Pros

| Benefit | Description |
|---------|-------------|
| **Reduced disk usage** | Eliminates JAR duplication across N projects. Typical savings: 100-500 MB per project. |
| **Faster initial setup** | New Savant projects immediately benefit from any JARs already in `~/.m2` from Maven/Gradle builds. |
| **Simpler mental model** | "AMDs are local, JARs are global" is easy to understand. |
| **Better Maven ecosystem compatibility** | Savant becomes a better citizen in the Maven ecosystem by sharing the same local cache. |
| **Smaller project directories** | `.savant/cache` shrinks from hundreds of MB to a few KB (AMD XML files). |
| **Easier CI/CD caching** | CI pipelines already cache `~/.m2`. No need to additionally cache `.savant/cache` for JARs. |
| **Reduced redundancy** | Eliminates the "same JAR in two places" problem that can lead to coherence issues. |

### 4.2 Cons

| Drawback | Description | Mitigation |
|----------|-------------|------------|
| **JAR resolution overhead per build** | Every build resolves JAR paths from `~/.m2` instead of `.savant/cache`. | This is a path computation (string concatenation + file existence check), not I/O. Negligible overhead. |
| **Dependency on `~/.m2` integrity** | If `~/.m2` is corrupted or cleared, all projects need to re-download. | This is the standard Maven/Gradle behavior. Users are accustomed to this. |
| **Cross-repo changes** | The change touches `savant-dependency-management`, `savant-core`, and `dependency-plugin`. | Phased implementation with backwards compatibility in transition. |
| **Migration for existing projects** | Existing projects have populated `.savant/cache` directories that become stale. | Provide a migration guide. Old `.savant/cache` can be safely deleted. |
| **Savant-native artifacts** | Artifacts only in the Savant repository (not in Maven Central) still need to be cached somewhere. | Store in `~/.m2/repository` using the same layout. |

---

## 5. Alternative Solutions Considered

### 5.1 Alternative A: Symlink-Based Cache

**Approach**: Instead of copying JARs to `.savant/cache`, create symlinks pointing to `~/.m2/repository`.

**Pros**: Zero additional disk usage; `.savant/cache` still "looks" populated; minimal code changes.

**Cons**: Windows compatibility issues (symlinks require admin privileges); breaks if `~/.m2` is on a different filesystem; adds complexity for a marginal benefit over the proposed solution; doesn't fundamentally simplify the mental model.

**Verdict**: Rejected. The proposed solution is simpler and more portable.

### 5.2 Alternative B: Global Savant Cache (No Project Cache)

**Approach**: Move everything to `~/.savant/cache` (global) and eliminate the project-level cache entirely.

**Pros**: Simplest model; one cache location; no project directory pollution.

**Cons**: AMDs encode project-specific decisions (semver mappings, license choices). Different projects may need different AMDs for the same artifact. Global AMDs would create conflicts. Also, AMD files are the core value Savant adds and committing them to VCS with the project is a useful option.

**Verdict**: Rejected. Per-project AMD caching is necessary because AMD content depends on per-project configuration (semver mappings, license overrides).

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

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Breaking existing builds** | Medium | High | Feature flag to enable new strategy; keep old behavior as fallback during transition. |
| **Savant-only artifacts not found** | Low | High | Explicitly handle Savant repo artifacts by caching them in `~/.m2` with Maven-compatible paths. Test with all known Savant-only artifacts. |
| **Non-semantic version path resolution** | Medium | Medium | The `nonSemanticVersion` field on `Artifact` already tracks the original Maven version. Ensure path construction uses this for `~/.m2` lookups. Comprehensive tests for version-mapped artifacts. |
| **CI/CD environment differences** | Low | Medium | Document that `~/.m2` must be writable. Most CI systems already support this via Maven cache configuration. |
| **Race conditions in shared `~/.m2`** | Low | Low | Maven itself has this issue and handles it via file locking. Savant currently does no locking; consider adding advisory locks for writes. |
| **Integration build isolation** | Low | Medium | Integration builds already use `~/.savant/cache`. No change needed. Ensure the new workflow doesn't accidentally route integration artifacts to `~/.m2`. |

### 6.2 Compatibility Risks

| Risk | Description | Mitigation |
|------|-------------|------------|
| **Older Savant versions** | Projects using older savant-core with the new savant-dependency-management. | The new library should be backwards compatible. Old `WorkflowDelegate.standard()` can create the new workflow structure. |
| **Custom workflows** | Projects with custom fetch/publish workflows defined in build files. | Support the existing DSL. Custom workflows continue to work; only the `standard()` shortcut changes. |
| **Third-party tools** | Tools that read `.savant/cache` directly. | Document the change. The IDEA plugin and any other tooling that reads from `.savant/cache` for JARs needs updating. |

---

## 7. Phased Implementation Plan

### Phase 1: Refactor Workflow Internals (savant-dependency-management)

**Goal**: Introduce the concept of type-specific workflows without changing external behavior.

**Changes**:

1. **Create `AmdCacheProcess`**: A new `Process` implementation that only handles AMD files. It extends `CacheProcess` but filters to only `.amd` and `.amd.md5` items.

2. **Refactor `Workflow` class**: Add separate workflow chains for AMD vs artifact resolution. The constructor accepts separate fetch/publish workflows for each type. Maintain backward-compatible constructor that creates the old behavior.

3. **Update `Workflow.fetchMetaData()`**: Route AMD fetches through the AMD workflow chain.

4. **Update `Workflow.fetchArtifact()`**: Route JAR fetches through the artifact workflow chain.

5. **Update `Workflow.fetchSource()`**: Route source fetches through the artifact workflow chain.

6. **Update `Workflow.loadPOM()`**: Route POM fetches through the artifact workflow chain.

**Tests**: All existing tests must continue to pass with no behavioral change.

### Phase 2: Implement New Cache Strategy (savant-dependency-management)

**Goal**: Implement the new cache routing so AMDs go to `.savant/cache` and everything else goes to `~/.m2`.

**Changes**:

1. **Create new `Workflow` factory method**: `Workflow.standard(Output output)` that creates the new workflow with:
   - AMD fetch: `CacheProcess(.savant/cache)` then generate from POM
   - AMD publish: `CacheProcess(.savant/cache)`
   - Artifact fetch: `MavenCacheProcess(~/.m2)` then `MavenProcess(maven central)`
   - Artifact publish: `MavenCacheProcess(~/.m2)`

2. **Handle non-semantic version JAR resolution**: When `fetchArtifact()` falls back to non-semantic version, it should look in `~/.m2/repository` using the Maven version path rather than re-publishing to `.savant/cache`.

3. **Update negative caching**: Route negative markers to the appropriate cache (AMD negatives to `.savant/cache`, source negatives to `~/.m2`).

4. **Handle source JAR republishing**: Currently, when a Maven-style source JAR (`-sources.jar`) is found, it's republished as a Savant-style source (`-src.jar`). Under the new strategy, consider whether to maintain this renaming in `~/.m2` or handle it as a name resolution at build time.

**Tests**: New tests verifying the separation of concerns. Existing tests refactored to validate new behavior.

### Phase 3: Update Build File DSL (savant-core)

**Goal**: Update the standard workflow and DSL in savant-core to use the new cache strategy.

**Changes**:

1. **Update `WorkflowDelegate.standard()`**: Use the new workflow factory.

2. **Update `ProcessDelegate`**: If the DSL needs new methods for configuring AMD-specific vs artifact-specific processes, add them.

3. **Maintain backward compatibility**: Custom `fetch {}` and `publish {}` blocks should continue to work for projects that override the standard workflow.

**Tests**: Build file parsing tests in savant-core.

### Phase 4: Update Dependency Plugin (dependency-plugin)

**Goal**: Ensure the dependency plugin works correctly with the new workflow.

**Changes**:

1. **Update `integrate()`**: Ensure integration builds still publish to `~/.savant/cache`.

2. **Update classpath construction**: Classpath paths now point to `~/.m2/repository` instead of `.savant/cache`. Verify the `Classpath` object returns correct paths.

3. **Update `copy()`**: The copy target should still work (copying from `~/.m2` to the target directory).

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

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 1 | `AmdCacheProcess.fetch()` for an AMD file that exists | Returns the path to the cached AMD file |
| 2 | `AmdCacheProcess.fetch()` for an AMD file that doesn't exist | Returns null |
| 3 | `AmdCacheProcess.publish()` for an AMD file | Writes AMD to `.savant/cache` directory |
| 4 | `AmdCacheProcess.fetch()` for a non-AMD file (JAR) | Returns null (should not cache JARs) |
| 5 | `AmdCacheProcess.publish()` for a non-AMD file | No-op or returns null (should not cache JARs) |
| 6 | `MavenCacheProcess.fetch()` for a JAR in `~/.m2` | Returns the path in `~/.m2` |
| 7 | `MavenCacheProcess.fetch()` for a JAR not in `~/.m2` | Returns null |
| 8 | `MavenCacheProcess.publish()` for a JAR | Writes JAR to `~/.m2/repository` |
| 9 | Negative cache marker in `.savant/cache` for AMD | Throws `NegativeCacheException` |
| 10 | Negative cache marker in `~/.m2` for source JAR | Throws `NegativeCacheException` |

### 8.2 Unit Tests: Workflow Routing

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 11 | `Workflow.fetchMetaData()` routes through AMD workflow | AMD fetched from `.savant/cache`, not `~/.m2` |
| 12 | `Workflow.fetchArtifact()` routes through artifact workflow | JAR fetched from `~/.m2`, not `.savant/cache` |
| 13 | `Workflow.fetchSource()` routes through artifact workflow | Source JAR fetched from `~/.m2` |
| 14 | `Workflow.loadPOM()` routes through artifact workflow | POM fetched from `~/.m2` |
| 15 | AMD generated from POM is published to `.savant/cache` only | AMD file appears in `.savant/cache`, not `~/.m2` |
| 16 | JAR downloaded from Maven Central is published to `~/.m2` only | JAR appears in `~/.m2`, not `.savant/cache` |
| 17 | POM downloaded from Maven Central is published to `~/.m2` only | POM appears in `~/.m2`, not `.savant/cache` |

### 8.3 Integration Tests: End-to-End Resolution

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 18 | Resolve a simple dependency (e.g., `commons-collections:3.2.1`) cold | JAR in `~/.m2`, AMD in `.savant/cache`, no JAR in `.savant/cache` |
| 19 | Resolve same dependency again (warm cache) | AMD from `.savant/cache`, JAR from `~/.m2`, no network calls |
| 20 | Resolve dependency already in `~/.m2` (from Maven build) | No download needed; AMD generated and cached locally |
| 21 | Resolve dependency with non-semantic version (e.g., `netty:4.1.65.Final`) | AMD uses semantic version; JAR resolved from `~/.m2` using Maven version path |
| 22 | Resolve dependency with transitive dependencies | All transitive AMDs in `.savant/cache`; all transitive JARs in `~/.m2` |
| 23 | Resolve dependency with parent POM | Parent POM fetched to `~/.m2`; parent not in `.savant/cache` |
| 24 | Resolve dependency with BOM import | BOM POM in `~/.m2`; AMD generated correctly from imported dependency definitions |
| 25 | Resolve Savant-native artifact (from savantbuild.org, not Maven Central) | AMD in `.savant/cache`; JAR in `~/.m2` |

### 8.4 Integration Tests: Version Mapping

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 26 | Resolve artifact where Maven version == semantic version (e.g., `3.2.1`) | Direct resolution, no mapping needed |
| 27 | Resolve artifact where Maven version needs mapping (e.g., `4.1.65.Final`) | Mapping applied; AMD stores semantic version; JAR found via non-semantic path |
| 28 | Resolve artifact where Maven version is simple (e.g., `1.0` -> `1.0.0`) | Auto-fixed to 3-part semver |
| 29 | Resolve artifact with range version mapping | Range mapping resolved to concrete version |
| 30 | Resolve artifact with missing version mapping for non-semantic version | Throws `VersionException` with helpful error message |

### 8.5 Integration Tests: Negative Caching

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 31 | Source JAR doesn't exist; negative marker created | `.neg` file in `~/.m2`; subsequent fetches short-circuit |
| 32 | AMD negative marker present | `NegativeCacheException` thrown; no network request |
| 33 | Clear negative marker and retry | Negative marker deleted; fresh fetch attempted |

### 8.6 Integration Tests: Integration Builds

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 34 | Publish integration build | Artifact published to `~/.savant/cache/` with integration version |
| 35 | Resolve integration build dependency | Fetched from `~/.savant/cache/`, not `.savant/cache` |
| 36 | Integration version doesn't pollute `~/.m2` | No integration artifacts in `~/.m2` |

### 8.7 Integration Tests: Publishing

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 37 | Publish a release artifact | AMD published; JAR published; source published; all to configured publish targets |
| 38 | Publish with no source file | Negative marker created for source |

### 8.8 Edge Case Tests

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 39 | `~/.m2/repository` doesn't exist on first build | Directory created automatically |
| 40 | `.savant/cache` doesn't exist on first build | Directory created automatically |
| 41 | JAR exists in `~/.m2` but is corrupted (MD5 mismatch) | Re-downloaded from remote; old file replaced |
| 42 | AMD exists in `.savant/cache` but is malformed XML | Error thrown with clear message |
| 43 | Network unavailable but `~/.m2` has all JARs and `.savant/cache` has all AMDs | Build succeeds (offline mode) |
| 44 | Network unavailable and `~/.m2` is empty | Build fails with clear error message indicating which artifact is missing |
| 45 | Two projects with different semver mappings for the same artifact | Each project has its own AMD in its `.savant/cache`; no conflict |
| 46 | Artifact with classifier (e.g., `snappy-java:1.1.10.5`) | Correctly resolved from `~/.m2` using original version path |
| 47 | Concurrent builds writing to `~/.m2` | No corruption (file copy is atomic enough for this use case, or add advisory locking) |
| 48 | Very deep transitive dependency tree (10+ levels) | All AMDs cached; all JARs resolved; no stack overflow |
| 49 | Circular dependency detected | `CyclicException` thrown (unchanged behavior) |
| 50 | Incompatible versions in dependency graph (e.g., major version mismatch) | `CompatibilityException` thrown (unchanged behavior) |
| 51 | `skipCompatibilityCheck` flag honored | Incompatible versions allowed when flag is set |
| 52 | Delete `.savant/cache` between builds | AMDs re-generated from POMs in `~/.m2`; build succeeds with network |
| 53 | Delete `~/.m2/repository` between builds | JARs re-downloaded from Maven Central; AMDs still in `.savant/cache`; build succeeds with network |
| 54 | Artifact with exclusions | Exclusions honored in dependency graph; excluded artifacts not resolved |
| 55 | Custom workflow overrides in build file | Custom `fetch {}` / `publish {}` blocks override standard behavior |

### 8.9 Performance Tests

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 56 | Cold build (empty caches) | Performance comparable to current behavior (network-bound) |
| 57 | Warm build (all caches populated) | Equal or faster than current behavior (fewer cache checks) |
| 58 | Re-build after deleting `.savant/cache` only | Slightly slower (AMD regeneration) but no network for JARs |
| 59 | Build with 200+ dependencies | Completes in reasonable time; no excessive memory usage |

### 8.10 Backward Compatibility Tests

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 60 | Old-style `Workflow` constructor still works | Backward-compatible; old behavior preserved |
| 61 | Custom `fetch {}` with explicit `cache()` process | Works as before; project cache stores all items |
| 62 | Workflow with only `cache()` (no Maven) | Savant-only resolution works |
| 63 | Workflow with only `maven()` (no cache) | Direct Maven resolution works |

---

## 9. Migration Guide (Draft)

### For Existing Users

1. Update `savant-dependency-management`, `savant-core`, and `dependency-plugin` to the new versions.
2. If using `workflow { standard() }` in your build file, the new cache strategy is automatic.
3. Delete your old `.savant/cache` directory to reclaim disk space: `rm -rf .savant/cache`
4. Your `~/.m2/repository` will be populated on the next build.
5. If using custom workflows, review the updated documentation.

### For CI/CD Pipelines

1. Cache `~/.m2/repository` between builds (most CI systems already do this for Maven).
2. Optionally cache `.savant/cache` for faster AMD resolution (small, low priority).
3. No other changes needed.

---

## 10. Open Questions

1. **Should `.savant/cache` be committable to VCS?** AMD files are small and deterministic. Committing them would allow fully offline builds without even needing `~/.m2`. However, this may cause merge conflicts and adds noise to the repo. **Recommendation**: Don't commit by default, but support it as an option.

2. **Should we add a `savant cache clean` command?** For cleaning `.savant/cache` and/or `~/.m2` Savant-related artifacts. **Recommendation**: Yes, in a future release.

3. **How to handle the Savant repository (`repository.savantbuild.org`)?** Artifacts here use Savant's format and may not exist in Maven Central. They should be stored in `~/.m2/repository` as well. **Recommendation**: The URLProcess already writes to the publish workflow, which now targets `~/.m2`. This should work without special handling.

4. **Should the IDEA plugin be updated?** If there's a Savant IDEA plugin that reads from `.savant/cache` for JARs, it needs to be updated to read from `~/.m2`. **Recommendation**: Yes, in a coordinated release.

5. **Should AMD files include a schema version?** To handle future format changes gracefully. **Recommendation**: Consider adding a version attribute to the `<artifact-meta-data>` root element.
