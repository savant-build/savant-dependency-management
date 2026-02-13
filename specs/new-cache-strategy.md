# Savant Dependency Management: New Cache Strategy

## Specification Document

**Date:** 2026-02-11 (updated 2026-02-13)
**Status:** IMPLEMENTED - IN REVIEW
**Branch:** `new_cache_strat`
**Affected Repos:** `savant-dependency-management`, `savant-core`

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

6. **Stale AMD mappings**: When a developer adds or changes a `semanticVersions` mapping in their build file, the previously-cached AMD files still contain the old mapping (because `MavenTools.toSavantDependencies(pom, mappings)` bakes mappings into the AMD XML at generation time). The developer must manually delete `.savant/cache` to pick up the change.

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

1. **Large binaries (JARs) are always global per-user**: No per-project JAR caching. JARs live in a single global location per user:
   - **Maven-sourced JARs** -> `~/.m2/repository` (shared with Maven/Gradle)
   - **Savant-native JARs** -> `~/.savant/cache` (Savant-specific artifacts not in Maven repos)

2. **Savant-native AMDs are authoritative and cached globally**: Artifacts published to the Savant repository (`repository.savantbuild.org`) include publisher-created `.amd` files with correct semantic versions, licenses, and dependency groups. These AMDs are **not project-specific** -- they don't depend on the build file's `semanticVersions` mappings. They are cached in `~/.savant/cache` (global) alongside their JARs.

3. **Maven-sourced AMDs are generated in-memory on every build**: For Maven artifacts (which have POMs, not AMDs), Savant generates `ArtifactMetaData` objects in memory by parsing POMs and applying the **current** build file's `semanticVersions` mappings. This eliminates the stale-cache problem entirely -- mappings are always applied fresh. These AMDs are **never persisted to disk** because they are project-specific (different projects may map the same Maven artifact differently).

4. **In-process caching during a single build**: A `Map<String, ArtifactMetaData>` within the `Workflow` instance prevents re-processing the same artifact's POM (or re-downloading the same Savant AMD) multiple times during a single build invocation. This map lives only for the duration of the build.

5. **Simplicity over optimization**: The design prioritizes simplicity and compatibility with the Maven ecosystem over micro-optimizations for cold-start performance.

6. **No local project cache (`.savant/cache`)**: The per-project `.savant/cache` directory is eliminated entirely. All cached artifacts live in global per-user directories (`~/.m2/repository` and `~/.savant/cache`). Per-project mapping decisions are in-memory only.

### 2.2 New Architecture

| Level | Location | Contents | Scope |
|-------|----------|----------|-------|
| 1. In-memory AMD cache | JVM heap during build | `ArtifactMetaData` objects (from POMs or Savant AMDs) | Per-build |
| 2. Maven artifact cache | `~/.m2/repository/` | Maven-sourced JARs, POMs, source JARs, MD5s | Global (user) |
| 3. Savant artifact cache | `~/.savant/cache/` | Savant-native JARs, AMDs, sources + integration build artifacts | Global (user) |
| 4. Remote repos | Savant repo + Maven Central | Everything | Network |

**Key distinction -- two types of artifacts, two cache strategies:**

| Artifact Source | JARs cached in | AMD strategy | Why |
|----------------|---------------|--------------|-----|
| **Maven** (Maven Central, etc.) | `~/.m2/repository` | Generated in-memory from POM on every build | Project-specific `semanticVersions` mappings are baked into AMD generation. Different projects may map the same Maven artifact differently. Caching would create stale mappings. |
| **Savant-native** (repository.savantbuild.org) | `~/.savant/cache` | Downloaded from Savant repo, cached in `~/.savant/cache` (global) | AMDs are authoritative (created by publisher). No project-specific mappings involved. Safe to cache globally. |
| **Integration builds** | `~/.savant/cache` | Full AMD + JAR cached | Integration artifacts are inherently global (shared across projects during development). Unchanged from current behavior. |

### 2.3 New Standard Workflow

```
Fetch AMD:
  - Savant-native:  CacheProcess(~/.savant/cache) -> URLProcess(savantbuild.org) [cached globally]
  - Maven-sourced:  [generate in-memory from POM on every build, never persisted]

Fetch POM:       MavenCacheProcess(~/.m2) -> URLProcess(savantbuild.org) -> MavenProcess(maven central)
Fetch JAR:       CacheProcess(~/.savant/cache) -> MavenCacheProcess(~/.m2) -> URLProcess(savantbuild.org) -> MavenProcess(maven central)
Fetch Source:    CacheProcess(~/.savant/cache) -> MavenCacheProcess(~/.m2) -> URLProcess(savantbuild.org) -> MavenProcess(maven central)

Publish (Savant-native): CacheProcess(~/.savant/cache)
Publish (Maven-sourced): MavenCacheProcess(~/.m2)
```

### 2.4 Detailed Flow: Dependency Resolution

#### Step 1: Fetch Metadata (AMD)

The AMD fetch path now branches based on whether the artifact is Savant-native or Maven-sourced:

```
fetchMetaData(artifact):
  1. Check in-memory cache (Map<String, ArtifactMetaData>)
     -> If found: return cached ArtifactMetaData

  2. Try to fetch pre-built AMD from Savant repo:
     a. Check ~/.savant/cache (global) for AMD file
     b. If not there, check URLProcess (repository.savantbuild.org)
     c. If found: parse AMD, cache in ~/.savant/cache (global), store in memory, return
        (This is a Savant-native artifact with an authoritative AMD)

  3. If no AMD found, this is a Maven artifact -- generate AMD in-memory:
     a. Fetch POM from ~/.m2 or Maven Central (download to ~/.m2 if needed)
     b. Parse POM, resolve parent POMs and imports (also from ~/.m2)
     c. Translate POM -> ArtifactMetaData (apply CURRENT semver mappings from build file)
     d. Store in in-memory cache ONLY (no disk write -- mappings are project-specific)
     e. Return ArtifactMetaData

  4. If neither AMD nor POM found: throw ArtifactMetaDataMissingException
```

#### Step 2: Build Dependency Graph

No change from current behavior. The `DependencyGraph` is built by recursively fetching AMD (now via the two-path strategy above) for all transitive dependencies.

#### Step 3: Reduce Graph

No change. Version compatibility checking and selection of highest compatible version.

#### Step 4: Resolve Artifacts (JARs)

```
fetchArtifact(artifact):
  1. Check ~/.savant/cache (for Savant-native artifacts)
  2. Check ~/.m2/repository (for Maven-sourced artifacts)
     -> If found: return path
  3. If not found:
     a. Download from Savant repo or Maven Central
     b. Cache to ~/.savant/cache or ~/.m2/repository respectively
     c. Return path
  4. (No per-project .savant/cache)
```

### 2.5 In-Memory AMD: Cost/Benefit Analysis

#### Performance Cost

For each dependency, the in-memory approach must:
1. **Read POM from disk** (`~/.m2`): ~1-50KB per POM, local I/O, ~0.1-1ms
2. **Parse POM XML**: DOM parsing + variable replacement, ~1-5ms
3. **Resolve parent POMs**: Recursive POM loading, typically 1-3 parents per artifact
4. **Resolve imports**: BOM imports add more POM loads
5. **Translate to ArtifactMetaData**: Mapping application, ~0.1ms

**Estimated cost for a project with 100 dependencies:**
- ~100 unique artifacts * ~2.5 parent/import POMs each = ~350 POM parses
- At ~2ms each = ~700ms total for AMD generation
- With in-process caching (parent POMs reused across siblings): ~300-500ms

**Compared to disk-cached AMD approach:**
- ~100 AMD file reads + XML parses = ~100-200ms
- Delta: **~200-400ms additional per build** for in-memory approach

**Verdict**: For most builds (which take seconds to minutes), 200-400ms is negligible. The build is typically dominated by compilation, testing, and network I/O for downloading JARs on cold builds.

#### Benefits

1. **No stale AMD cache for Maven artifacts**: Mappings from the current build file are always applied fresh. No need to delete `.savant/cache` when changing `semanticVersions` mappings.
2. **No per-project cache directory**: The per-project `.savant/cache` directory is eliminated entirely. All cached artifacts live in global per-user directories.
3. **Savant-native AMDs are cached globally**: Publisher-created AMDs are cached in `~/.savant/cache` and shared across all projects. No redundant downloads.
4. **Simpler mental model**: "Maven JARs are in `~/.m2`, Savant artifacts are in `~/.savant/cache`, Maven-to-AMD mappings are in-memory."

#### Risks

1. **Offline builds**: Offline builds require POMs to be in `~/.m2` and Savant AMDs in `~/.savant/cache`. Both are populated during normal builds, so this is only a problem on a truly fresh machine (same as Maven/Gradle).
2. **Memory usage**: For 100 dependencies, ArtifactMetaData objects are small (a few KB each). 100 objects = ~100KB-1MB. Negligible.
3. **Repeated work across builds**: Each `savant` invocation re-generates Maven-sourced AMDs from POMs. For projects with very large dependency trees (500+), this could become noticeable. Savant-native AMDs are not re-generated (they're cached). Can be revisited later if needed.

### 2.6 Key Code Changes

#### 2.6.1 `Workflow.java` (savant-dependency-management)

The `Workflow` class needs major changes to support the two-path AMD strategy:

**A. Add in-memory AMD cache**:

```java
public class Workflow {
  // NEW: In-memory AMD cache for the duration of this build
  private final Map<String, ArtifactMetaData> amdCache = new HashMap<>();

  // Existing fields
  public final FetchWorkflow fetchWorkflow;    // Used for POMs, JARs, sources, and Savant-native AMDs
  public final PublishWorkflow publishWorkflow; // Used for JARs, sources (global caches)
  public final Map<String, Version> mappings = new HashMap<>();
  public final Map<String, String> rangeMappings = new HashMap<>();
  public final Output output;
}
```

**B. `fetchMetaData()` -- Two-path AMD resolution**:

```java
public ArtifactMetaData fetchMetaData(Artifact artifact) {
  String cacheKey = artifact.id.group + ":" + artifact.id.project + ":" + artifact.version;

  // 1. Check in-memory cache first
  ArtifactMetaData cached = amdCache.get(cacheKey);
  if (cached != null) {
    return cached;
  }

  // 2. Try to fetch pre-built AMD (Savant-native artifact)
  //    This checks ~/.savant/cache then repository.savantbuild.org
  //    If found, it's an authoritative AMD -- cache globally in ~/.savant/cache
  ResolvableItem amdItem = new ResolvableItem(..., artifact.getArtifactMetaDataFile());
  Path amdFile = fetchWorkflow.fetchItem(amdItem, publishWorkflow);
  if (amdFile != null) {
    ArtifactMetaData amd = ArtifactTools.parseArtifactMetaData(amdFile, mappings);
    amdCache.put(cacheKey, amd);
    return amd;
  }

  // 3. No AMD found -- this is a Maven artifact. Generate AMD in-memory from POM.
  POM pom = loadPOM(artifact);
  if (pom == null) {
    throw new ArtifactMetaDataMissingException(artifact);
  }

  // Translate POM to AMD using CURRENT mappings (always fresh, never persisted)
  ArtifactMetaData amd = translatePOM(pom);
  amdCache.put(cacheKey, amd);
  return amd;
  // NOTE: No publishWorkflow.publish() for Maven-generated AMDs
}
```

**C. `fetchArtifact()` -- Remove per-project cache republishing**:

The current code re-publishes non-semantic-versioned JARs to `.savant/cache` under the semantic name. With the new approach, we return the path from `~/.m2` (Maven) or `~/.savant/cache` (Savant-native) with the original version name. The `nonSemanticVersion` field on `Artifact` already tracks the original version for path construction.

**D. `fetchSource()` -- Simplify**:

Remove the republishing of `-sources.jar` as `-src.jar` into the per-project cache. Instead, return the path to the source JAR in `~/.m2` or `~/.savant/cache`. The source file naming (`-sources.jar` vs `-src.jar`) can be resolved at lookup time rather than by renaming on disk.

#### 2.6.2 `WorkflowDelegate.java` (savant-core)

Update the `standard()` method:

```java
public void standard() {
    // Fetch: Savant global cache, Maven cache, Savant repo, Maven Central
    // CacheProcess(~/.savant/cache) handles Savant-native artifacts + cached AMDs
    // MavenCacheProcess(~/.m2) handles Maven-sourced JARs/POMs
    workflow.fetchWorkflow.processes.add(new CacheProcess(output, null, null));  // ~/.savant/cache (global)
    workflow.fetchWorkflow.processes.add(new MavenCacheProcess(output, null, null));
    workflow.fetchWorkflow.processes.add(new URLProcess(output, "https://repository.savantbuild.org", null, null));
    workflow.fetchWorkflow.processes.add(new MavenProcess(output, "https://repo1.maven.org/maven2", null, null));

    // Publish: Savant global cache + Maven cache
    // Savant-native artifacts go to ~/.savant/cache, Maven artifacts go to ~/.m2
    workflow.publishWorkflow.processes.add(new CacheProcess(output, null, null));  // ~/.savant/cache (global)
    workflow.publishWorkflow.processes.add(new MavenCacheProcess(output, null, null));
}
```

**Key change from current behavior**: `CacheProcess` now points at `~/.savant/cache` (global) instead of `.savant/cache` (per-project). This requires changing the `CacheProcess` default directory, or explicitly passing the global path. See Phase 3 for details.

**Note**: The `fetch {}` and `publish {}` DSL methods continue to work unchanged for custom workflows. Only `standard()` changes.

#### 2.6.3 `DefaultDependencyService.java` (savant-dependency-management)

**`publish()` method**: Currently publishes AMD to the publish workflow. With in-memory AMDs, the `publish()` method should still write the AMD when explicitly publishing a release (the AMD goes to the remote publish target like SVN), but NOT to a local disk cache.

Integration builds continue to use `~/.savant/cache/` via the existing `CacheProcess` integration dir.

#### 2.6.4 No Changes Required: `dependency-plugin`, `idea-plugin`

Both plugins are **consumers** of the resolved dependency graph. They receive `ResolvedArtifact` objects with `.file` and `.sourceFile` paths from `Workflow.fetchArtifact()` / `Workflow.fetchSource()`. The plugins don't know or care where on disk those files live. The paths will now point to `~/.m2` instead of `.savant/cache`, but the plugins work with whatever paths the Workflow provides.

**dependency-plugin**: `DependencyPlugin.groovy` calls `dependencyService.buildGraph()` and `dependencyService.reduce()` using `project.workflow`. Classpath, copy, integrate, and resolve operations all flow through the Workflow's artifact resolution. No code changes needed.

**idea-plugin**: `IDEAPlugin.groovy` uses `dependencyPlugin.resolve {}` to get a `ResolvedArtifactGraph`, then writes IML entries using `destination.file` paths. These paths will transparently point to `~/.m2`. No code changes needed.

#### 2.6.5 Out of Scope: `maven-bridge`

The `SavantBridge` in `maven-bridge` is a standalone CLI tool for one-time conversion of Maven artifacts into a Savant repository. It uses a non-standard `CacheProcess` API and operates independently. If the new cache strategy eliminates the need for pre-converting Maven artifacts (since Savant now dynamically maps Maven artifacts via POM-to-AMD translation on every build), this tool may become unnecessary. Deferred to a separate evaluation.

#### 2.6.6 Out of Scope: `pom-plugin`, `savant-utils`

- **pom-plugin**: Generates `pom.xml` from Savant dependencies. No cache interaction.
- **savant-utils**: Utility classes (Classpath, MD5, Graph). No cache interaction.

---

## 3. Handling Special Cases

### 3.1 Savant-Native Artifacts (Non-Maven)

Artifacts published to `https://repository.savantbuild.org` in Savant's format already have publisher-created `.amd` files with correct semantic versions, licenses, and dependency groups. These AMDs are **authoritative** -- they were created by the artifact publisher, not generated from a POM using project-specific mappings.

**Why Savant-native AMDs can be cached globally:**
- They don't depend on the build file's `semanticVersions` mappings
- Different projects consuming the same Savant-native artifact will always get the same AMD
- There is no stale-mapping problem for these artifacts

**Cache strategy for Savant-native artifacts:**
- **AMD**: Downloaded from Savant repo, cached in `~/.savant/cache` (global per-user). On subsequent builds, served from `~/.savant/cache` without network access.
- **JAR**: Downloaded from Savant repo, cached in `~/.savant/cache` (global per-user). Savant-native JARs are not stored in `~/.m2/repository` since they aren't Maven artifacts and wouldn't be useful to Maven/Gradle.
- **Sources**: Same as JARs -- cached in `~/.savant/cache`.

The directory layout for Savant and Maven is identical: `group/project/version/name-version.type`. The `CacheProcess` pointing at `~/.savant/cache` handles Savant-native artifacts, while `MavenCacheProcess` pointing at `~/.m2/repository` handles Maven-sourced artifacts.

### 3.2 Non-Semantic Version Mapping

When a Maven artifact has a non-semantic version (e.g., `4.1.65.Final`), Savant:
1. Downloads the POM using the original version from `~/.m2` or Maven Central
2. Generates the ArtifactMetaData **in memory** with the semantic version mapping applied from the current build file
3. The JAR in `~/.m2` uses the **original Maven version** path

The `fetchArtifact` method in `Workflow` already handles this dual-lookup (semantic first, then non-semantic fallback) -- see `Workflow.java:77-97`. The change is that instead of re-publishing a semantic-named copy to `.savant/cache`, it returns the path to the JAR in `~/.m2` using the original version path.

### 3.3 Integration Builds

Integration builds (version ending in `-{integration}`) use `~/.savant/cache/` as their cache. This behavior remains unchanged, as integration builds are inherently global (they need to be shared across projects during development).

### 3.4 Negative Caching

Negative cache markers (`.neg` files) are currently used for:
- Source JARs that don't exist (to avoid repeated download attempts)
- POMs that don't exist

Under the new strategy:
- **Source JAR negatives**: Stored in `~/.m2/repository` (since that's where sources live). This prevents repeated network requests for non-existent source JARs.
- **AMD negatives**: Not needed. With in-memory generation, if a POM doesn't exist, we simply don't generate an AMD. No `.neg` file needed.
- **POM negatives**: Could be stored in `~/.m2/repository`, but for released artifacts this is uncommon. If a POM doesn't exist for a declared dependency, it's a hard error (`ArtifactMetaDataMissingException`).

### 3.5 Offline Builds

**Current behavior**: Offline builds work if `.savant/cache` is populated (everything is local to the project).

**New behavior**: Offline builds work if the global caches are populated:
- `~/.m2/repository` has Maven-sourced JARs and POMs
- `~/.savant/cache` has Savant-native JARs and AMDs

Since both are shared across all projects and populated during normal builds, they're more likely to be complete than a per-project cache.

**Risk**: If a developer clones a project and has never built any Java project on their machine, both caches will be empty and the first build will require network access. This is the same behavior as Maven/Gradle and is acceptable.

### 3.6 MD5 Verification

MD5 verification for JARs happens at download time (in `URLProcess.fetch()`). This does not change. The verified JAR is written to `~/.m2/repository` and the MD5 file accompanies it.

For AMD files, since they are generated in-memory and never written to disk, MD5 verification is not applicable. The integrity is guaranteed by the POM source (which has its own MD5 in `~/.m2`).

### 3.7 Publishing Artifacts

When a project publishes its own artifacts (`DependencyService.publish()`), the behavior depends on context:
- **Integration builds**: Published to `~/.savant/cache/` (unchanged). This includes the AMD file, JAR, source, and MD5s.
- **Release builds**: Published to the configured publish workflow (e.g., SVN repo). The AMD is generated and published to the remote target. JARs are published to the remote target and `~/.m2`.

---

## 4. Pros and Cons

### 4.1 Pros

| Benefit | Description |
|---------|-------------|
| **Reduced disk usage** | Eliminates JAR duplication across N projects. JARs live in one global location per user. Typical savings: 100-500 MB per project. |
| **No stale cache for Maven artifacts** | Semantic version mappings are always applied fresh from the current build file. No need to manually delete `.savant/cache` when changing `semanticVersions`. |
| **Savant-native AMDs cached globally** | Publisher-created AMDs in `~/.savant/cache` are shared across all projects. Downloaded once, used everywhere. |
| **Faster initial setup** | New Savant projects immediately benefit from any JARs already in `~/.m2` from Maven/Gradle builds, and any Savant-native artifacts in `~/.savant/cache` from other Savant projects. |
| **Simpler mental model** | "Maven JARs in `~/.m2`, Savant artifacts in `~/.savant/cache`, Maven-to-AMD mappings are in-memory per build." |
| **Better Maven ecosystem compatibility** | Savant becomes a better citizen in the Maven ecosystem by sharing the same local cache for Maven-sourced artifacts. |
| **No project directory pollution** | Per-project `.savant/cache` is eliminated entirely. |
| **Easier CI/CD caching** | CI pipelines cache `~/.m2` and `~/.savant/cache`. No per-project cache to manage. |
| **Fewer repos to change** | Only `savant-dependency-management` and `savant-core` need code changes. |

### 4.2 Cons

| Drawback | Description | Mitigation |
|----------|-------------|------------|
| **POM parsing on every build** | Every build must parse Maven POMs to generate AMDs (~300-500ms for 100 deps). Savant-native AMDs are cached and not re-parsed. | Acceptable for most builds. Can add opt-in disk caching later if needed. |
| **Dependency on global cache integrity** | If `~/.m2` or `~/.savant/cache` is corrupted or cleared, all projects need to re-download. | This is the standard Maven/Gradle behavior. Users are accustomed to this. |
| **Cross-repo changes** | The change touches `savant-dependency-management` and `savant-core`. | Only 2 repos, well-scoped changes. |
| **Migration for existing projects** | Existing projects have populated per-project `.savant/cache` directories that become unused. | Provide a migration guide. Old `.savant/cache` can be safely deleted. |

---

## 5. Alternative Solutions Considered

### 5.1 Alternative A: Symlink-Based Cache

**Approach**: Instead of copying JARs to `.savant/cache`, create symlinks pointing to `~/.m2/repository`.

**Verdict**: Rejected. Windows compatibility issues; doesn't simplify the mental model.

### 5.2 Alternative B: Global Savant Cache (No Project Cache)

**Approach**: Move everything to `~/.savant/cache` (global) and eliminate the project-level cache entirely.

**Verdict**: Rejected. AMDs encode project-specific decisions (semver mappings). Different projects may need different AMDs for the same artifact. Global AMDs would create conflicts.

### 5.3 Alternative C: Keep Current Architecture, Add Cache Cleanup

**Approach**: Keep `.savant/cache` storing everything but add a `savant clean-cache` command.

**Verdict**: Rejected. Doesn't address the root cause.

### 5.4 Alternative D: Content-Addressable Cache

**Approach**: Store all artifacts in a content-addressable store and use hard links.

**Verdict**: Rejected. Over-engineered for the use case.

### 5.5 Alternative E: Disk-Cached AMDs Only (Original Spec v1 Approach)

**Approach**: Keep `.savant/cache` but store only AMD files there. JARs go to `~/.m2`.

**Pros**: Avoids POM re-parsing on every build (~200-400ms savings). Allows offline builds if AMDs are cached.

**Cons**: Still has the stale-mapping problem (changing `semanticVersions` requires deleting `.savant/cache`). Still requires AMD publish workflow, AMD negative caching, and disk I/O logic.

**Verdict**: Deferred as a fallback. If the performance cost of in-memory AMD generation proves unacceptable for large projects, we can add an opt-in disk cache for AMDs later. The in-memory approach is simpler and should be tried first.

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
| **In-memory AMD performance for large projects** | Low | Medium | Benchmark with 200+ dependency project. If > 1s overhead, add opt-in AMD disk cache as Alternative E. |

### 6.2 Compatibility Risks

| Risk | Description | Mitigation |
|------|-------------|------------|
| **Older Savant versions** | Projects using older savant-core with the new savant-dependency-management. | The new library should be backwards compatible. Old `WorkflowDelegate.standard()` can create the new workflow structure. |
| **Custom workflows** | Projects with custom fetch/publish workflows defined in build files. | Support the existing DSL. Custom workflows continue to work; only the `standard()` shortcut changes. |
| **Third-party tools reading `.savant/cache`** | Tools that read `.savant/cache` directly for JARs. | Document the change. After review, we've confirmed that `dependency-plugin` and `idea-plugin` do NOT read `.savant/cache` directly -- they consume the resolved artifact graph. |

---

## 7. Phased Implementation Plan

### Phase 1: Refactor `Workflow.fetchMetaData()` -- Two-Path AMD Resolution (savant-dependency-management)

**Goal**: Implement the two-path AMD strategy: Savant-native AMDs cached globally in `~/.savant/cache`, Maven-sourced AMDs generated in-memory from POMs.

**Changes**:

1. **Add in-memory AMD cache to `Workflow`**: A `Map<String, ArtifactMetaData> amdCache` field that persists for the lifetime of the `Workflow` instance. Covers both Savant-native and Maven-generated AMDs.

2. **Rewrite `Workflow.fetchMetaData()`**: Two-path resolution:
   - Check in-memory cache first (covers both types)
   - Try fetching pre-built AMD via `fetchWorkflow` (hits `~/.savant/cache` then Savant repo). If found, this is a Savant-native artifact -- parse and cache in memory.
   - If no AMD found, load POM (from `~/.m2` or Maven Central), translate to AMD with current mappings, cache in memory only (no disk write).

3. **Remove per-project AMD disk writes**: Stop writing Maven-generated AMD files to disk. Savant-native AMDs are still written to `~/.savant/cache` (global) via the publish workflow.

4. **Add in-memory POM caching**: Cache loaded `POM` objects in a `Map<String, POM>` on the Workflow to avoid re-parsing shared parent POMs and BOM POMs.

**Tests**: All existing tests must be updated to validate the new two-path behavior. See Test Plan (Section 8).

### Phase 2: Simplify `fetchArtifact()` and `fetchSource()` (savant-dependency-management)

**Goal**: Stop republishing JARs/sources to per-project `.savant/cache`. Return paths from global caches directly.

**Changes**:

1. **`fetchArtifact()`**: When a non-semantic version JAR is found in `~/.m2`, return its path directly instead of republishing under the semantic version name. For Savant-native JARs, return the path from `~/.savant/cache`. The semantic-to-non-semantic mapping is already tracked on the `Artifact` object.

2. **`fetchSource()`**: When a Maven-style `-sources.jar` is found, return its path directly instead of republishing as `-src.jar`. Consumers that need the path just use whatever is returned.

3. **Negative caching for sources**: Continue writing `.neg` markers to the appropriate global cache (`~/.m2/repository` for Maven sources, `~/.savant/cache` for Savant sources).

**Tests**: New tests validating paths point to global caches (`~/.m2` or `~/.savant/cache`). Source tests updated for new naming behavior.

### Phase 3: Update `WorkflowDelegate.standard()` and `CacheProcess` Default (savant-core + savant-dependency-management)

**Goal**: Update the standard workflow so `CacheProcess` points at `~/.savant/cache` (global) instead of `.savant/cache` (per-project).

**Changes**:

1. **Change `CacheProcess` default directory**: The default `dir` in `CacheProcess` currently defaults to `.savant/cache` (relative to project). Change it to `~/.savant/cache` (global per-user), or provide a new constructor / parameter for this. The `integrationDir` already defaults to `~/.savant/cache` so this aligns the behavior.

2. **Update `WorkflowDelegate.standard()`**: Configure the standard workflow with:
   - Fetch: `CacheProcess(~/.savant/cache)` + `MavenCacheProcess(~/.m2)` + `URLProcess(savantbuild.org)` + `MavenProcess(maven central)`
   - Publish: `CacheProcess(~/.savant/cache)` + `MavenCacheProcess(~/.m2)`

3. **Keep DSL backward-compatible**: `fetch { cache() }` and `publish { cache() }` continue to work for projects with custom workflows.

**Tests**: Update `GroovyBuildFileParserTest` to verify the new standard workflow configuration. Verify `CacheProcess` default directory change.

### Phase 4: Mapping Strategy Audit (savant-dependency-management)

**Goal**: Review the current POM-to-AMD mapping strategy for bugs, performance issues, and gaps that could prevent reliable dynamic mapping of Maven artifacts.

**Investigation Areas**:

1. **Version mapping correctness**: Are there edge cases where `MavenTools.toSavantDependencies(pom, mappings)` produces incorrect results? What happens with version ranges, SNAPSHOT versions, classifiers?

2. **POM parsing performance**: Profile the POM parsing path (`MavenTools.parsePOM()`, parent/import resolution). Identify any hotspots or redundant work.

3. **Parent POM resolution depth**: Some Maven artifacts have deeply nested parent POM hierarchies. Is there a risk of excessive network calls or stack depth?

4. **BOM import handling**: Test with complex BOM imports (e.g., Spring Boot, AWS SDK). Are all dependencies correctly resolved?

5. **Error handling**: When a POM is malformed or a parent POM is missing, are the error messages helpful? Are there silent failures?

6. **Range mapping coverage**: The `rangeMappings` feature was recently added. Verify it handles all edge cases.

**Deliverable**: A list of identified issues with severity and recommended fixes. These can be addressed as part of this work or tracked separately.

### Phase 5: Performance Benchmarking

**Goal**: Quantify the actual performance cost of in-memory AMD generation vs disk-cached AMDs.

**Approach**:

1. Create a test project with 50, 100, and 200 dependencies (mix of simple and complex POM hierarchies).
2. Measure time for `buildGraph()` with in-memory AMD generation.
3. Compare to baseline (current disk-cached approach).
4. If delta > 1 second for 200 deps, evaluate adding opt-in disk caching as Alternative E.
5. Measure memory usage for in-memory AMD cache.

### Phase 6: Migration and Cleanup

**Goal**: Help existing users migrate.

**Changes**:

1. **Version bump**: Release new versions of `savant-dependency-management` and `savant-core` with coordinated version numbers.

2. **Migration guide**: Document that `.savant/cache` can be safely deleted after upgrading.

3. **Update build files**: Projects using `workflow { standard() }` get the new behavior automatically. Projects with custom `fetch { cache() }` workflows continue to work unchanged.

---

## 8. Comprehensive Test Plan

### 8.1 Testing Patterns (Existing Conventions)

All tests follow these established patterns from the codebase:

- **Framework**: TestNG with `@Test`, `@BeforeSuite`, `@BeforeMethod`, `@AfterMethod`, `@DataProvider`
- **Base class**: `BaseUnitTest` provides `projectDir`, `cache`, `integration`, `mavenCache`, `output`, and a shared `workflow`
- **Fixtures**: Pre-seeded artifacts in `test-deps/savant/`, `test-deps/maven/`, and `test-deps/integration/`
- **Cleanup**: `PathTools.prune(path)` to clean test directories before each test
- **HTTP mocking**: `BaseUnitTest.makeFileServer()` creates a local HTTP server on port 7042 for testing URL/Maven processes
- **Assertions**: TestNG assertions (`assertNotNull`, `assertTrue`, `assertEquals`, `assertNull`)
- **Path validation**: `file.toAbsolutePath().toString().replace('\\', '/').endsWith(expected)` pattern for cross-platform path checks

### 8.2 Unit Tests: In-Memory AMD Generation (WorkflowTest.java)

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 1 | `fetchMetaData()` with POM in `~/.m2` (test dir) | ArtifactMetaData generated in-memory from POM; no AMD file written to disk |
| 2 | `fetchMetaData()` called twice for same artifact | Second call returns cached in-memory ArtifactMetaData; POM not re-parsed |
| 3 | `fetchMetaData()` with POM not in `~/.m2` but available on remote | POM downloaded to `~/.m2`, AMD generated in-memory |
| 4 | `fetchMetaData()` with no POM anywhere | `ArtifactMetaDataMissingException` thrown |
| 5 | `fetchMetaData()` with semantic version mapping | Mapping applied from `workflow.mappings`; resulting AMD has correct semver dependencies |
| 6 | `fetchMetaData()` after changing mappings mid-build | New mapping reflected immediately (no stale cache) |
| 7 | `fetchMetaData()` with parent POM | Parent POM resolved from `~/.m2`; AMD includes parent-defined dependencies |
| 8 | `fetchMetaData()` with BOM import | BOM POM resolved; imported dependency definitions included |

### 8.3 Unit Tests: Artifact Fetching Without Local Cache (WorkflowTest.java)

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 9 | `fetchArtifact()` for JAR in `~/.m2` (test dir) | Returns path in `~/.m2`; no copy to `.savant/cache` |
| 10 | `fetchArtifact()` for JAR not in `~/.m2` | Downloads to `~/.m2`; returns path in `~/.m2` |
| 11 | `fetchArtifact()` with non-semantic version | Falls back to Maven version path in `~/.m2`; does NOT republish under semantic name |
| 12 | `fetchArtifact()` for missing artifact | Throws `ArtifactMissingException` |

### 8.4 Unit Tests: Source Fetching Without Local Cache (WorkflowTest.java)

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 13 | `fetchSource()` for `-sources.jar` in `~/.m2` | Returns path to `-sources.jar` in `~/.m2`; does NOT republish as `-src.jar` |
| 14 | `fetchSource()` for `-src.jar` in `~/.m2` | Returns path to `-src.jar` directly |
| 15 | `fetchSource()` with non-semantic version | Falls back to Maven version source path; returns `~/.m2` path |
| 16 | `fetchSource()` for non-existent source | Creates `.neg` marker in `~/.m2` (test dir); returns null |
| 17 | `fetchSource()` with existing `.neg` marker | Returns null immediately (short-circuit) |

### 8.5 Unit Tests: CacheProcess Behavior (CacheProcessTest.java)

Existing tests continue to pass for `CacheProcess` (still used by custom workflows and integration builds):

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 18 | `CacheProcess.fetch()` for existing artifact | Returns path (unchanged behavior) |
| 19 | `CacheProcess.fetch()` for integration build artifact | Routes to integration dir (unchanged behavior) |
| 20 | `CacheProcess.publish()` to cache dir | Copies file to cache (unchanged behavior) |
| 21 | `CacheProcess.publish()` for integration build | Copies to integration dir (unchanged behavior) |

### 8.6 Integration Tests: End-to-End with Maven Central (WorkflowTest.java)

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 22 | Resolve `org.apache.groovy:groovy:4.0.5` cold | POM downloaded to `~/.m2` (test dir); AMD generated in-memory; JAR downloaded to `~/.m2`; no `.savant/cache` files |
| 23 | Resolve `io.vertx:vertx-core:3.9.8` with netty version mappings | All mapped dependencies in AMD use semantic versions; JARs in `~/.m2` use original Maven versions |
| 24 | Resolve same dependency twice in one build | Second resolve uses in-memory cached AMD; only one POM parse |
| 25 | Resolve dependency with transitive dependencies | All transitive AMDs generated in-memory; all JARs in `~/.m2` |
| 26 | Resolve dependency with parent POM | Parent POM in `~/.m2`; no parent POM in `.savant/cache` |

### 8.7 Integration Tests: DefaultDependencyService (DefaultDependencyServiceTest.java)

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 27 | `buildGraph()` + `reduce()` + `resolve()` full pipeline | Complete dependency resolution with all JARs from `~/.m2`; no `.savant/cache` involvement |
| 28 | `publish()` for integration build | AMD, JAR, source, MD5s all published to `~/.savant/cache` |
| 29 | `publish()` for release build | AMD generated and published to remote; JAR published to remote and `~/.m2` |
| 30 | `resolve()` with `fetchSource: true` | Source JARs resolved from `~/.m2` |

### 8.8 Integration Tests: Integration Builds

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 31 | Publish integration build | Artifact published to `~/.savant/cache/` with integration version |
| 32 | Resolve integration build dependency | Fetched from `~/.savant/cache/`, not `~/.m2` |
| 33 | Integration version doesn't pollute `~/.m2` | No integration artifacts in `~/.m2` |

### 8.9 Integration Tests: Savant-Core WorkflowDelegate (GroovyBuildFileParserTest.java)

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 34 | `standard()` produces workflow without CacheProcess in fetch chain | Only MavenCacheProcess, URLProcess, MavenProcess in fetch |
| 35 | `standard()` produces workflow without CacheProcess in publish chain | Only MavenCacheProcess in publish |
| 36 | Custom `fetch { cache() }` still works | CacheProcess added to fetch chain as before |
| 37 | Custom `publish { cache() }` still works | CacheProcess added to publish chain as before |
| 38 | `semanticVersions {}` block still works | Mappings populated on workflow |

### 8.10 Edge Case Tests

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 39 | `~/.m2/repository` doesn't exist on first build | Directory created automatically |
| 40 | JAR exists in `~/.m2` but is corrupted (MD5 mismatch) | Re-downloaded from remote; old file replaced |
| 41 | Network unavailable but `~/.m2` has all JARs and POMs | Build succeeds |
| 42 | Network unavailable and `~/.m2` is empty | Build fails with clear error message indicating which artifact is missing |
| 43 | Two projects with different semver mappings for the same artifact | Each build generates its own in-memory AMD with project-specific mappings; no conflict |
| 44 | Artifact with classifier (e.g., `snappy-java:1.1.10.5`) | Correctly resolved from `~/.m2` using original version path |
| 45 | Very deep transitive dependency tree (10+ levels) | All AMDs generated; all JARs resolved; no stack overflow |
| 46 | Circular dependency detected | `CyclicException` thrown (unchanged behavior) |
| 47 | Incompatible versions in dependency graph | `CompatibilityException` thrown (unchanged behavior) |
| 48 | `skipCompatibilityCheck` flag honored | Incompatible versions allowed when flag is set |
| 49 | Artifact with exclusions | Exclusions honored in dependency graph; excluded artifacts not resolved |
| 50 | Custom workflow overrides in build file | Custom `fetch {}` / `publish {}` blocks override standard behavior |
| 51 | Delete `~/.m2/repository` between builds | JARs and POMs re-downloaded; AMDs re-generated in-memory; build succeeds with network |

### 8.11 Performance Tests

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 52 | Cold build (empty `~/.m2`) | Performance comparable to current behavior (network-bound) |
| 53 | Warm build (`~/.m2` populated) | Equal or faster than current behavior (fewer disk writes) |
| 54 | Warm build AMD generation time for 50 deps | Measure POM parse + translate time; document baseline |
| 55 | Warm build AMD generation time for 100 deps | Measure POM parse + translate time; document baseline |
| 56 | Warm build AMD generation time for 200 deps | Measure POM parse + translate time; document baseline |
| 57 | Memory usage for in-memory AMD cache | Measure heap impact; should be < 5MB for 200 deps |
| 58 | Build with 200+ dependencies | Completes in reasonable time; no excessive memory usage |

### 8.12 Backward Compatibility Tests

| # | Test Case | Expected Behavior |
|---|-----------|-------------------|
| 59 | Old-style `Workflow` constructor still works | Backward-compatible; old behavior preserved |
| 60 | Custom `fetch {}` with explicit `cache()` process | Works as before; project cache stores all items |
| 61 | Workflow with only `cache()` (no Maven) | Savant-only resolution works |
| 62 | Workflow with only `maven()` (no cache) | Direct Maven resolution works |

---

## 9. Mapping Strategy Audit Plan

### 9.1 Goal

With the new in-memory approach, every build dynamically maps Maven artifacts to Savant format via POM parsing. This makes the mapping strategy a critical path. We need to identify and fix any issues that could cause incorrect or slow mapping.

### 9.2 Investigation Checklist

1. **`MavenTools.parsePOM()`**: Review for correctness. Test with POMs that use:
   - `${project.version}`, `${parent.version}`, custom properties
   - BOMs (`<scope>import</scope>`)
   - Deeply nested parent hierarchies (3+ levels)
   - `<dependencyManagement>` sections
   - `<optional>true</optional>` and `<exclusions>`

2. **`MavenTools.toSavantDependencies()`**: Review mapping logic:
   - Are Maven scopes correctly mapped to Savant groups?
   - Are optional dependencies handled correctly?
   - Are exclusions properly translated?
   - What happens when a transitive dependency has a version that needs mapping but no mapping is configured?

3. **`MavenTools.toArtifact()`**: Review version resolution:
   - How are version ranges handled?
   - What happens with SNAPSHOT versions?
   - What about classifier-based artifacts?

4. **Performance hotspots**:
   - Is `POM.replaceKnownVariablesAndFillInDependencies()` called multiple times unnecessarily?
   - Are parent POMs being re-loaded for sibling dependencies? (Should be addressed by in-memory POM caching within the Workflow)
   - Is there any O(n^2) behavior in the variable replacement logic?

5. **Error messages**: When mapping fails, are the error messages actionable?
   - Missing version mapping for non-semantic version: does it tell the user exactly what to add to `semanticVersions {}`?
   - Missing parent POM: does it say which artifact triggered the lookup?

### 9.3 Deliverable

A markdown file `specs/mapping-strategy-audit.md` documenting findings, with a table of issues and recommended fixes.

---

## 10. Migration Guide (Draft)

### For Existing Users

1. Update `savant-dependency-management` and `savant-core` to the new versions.
2. If using `workflow { standard() }` in your build file, the new cache strategy is automatic.
3. Delete your old per-project `.savant/cache` directories to reclaim disk space: `rm -rf .savant/cache`
4. Your `~/.m2/repository` will be populated on the next build (Maven-sourced POMs and JARs).
5. Your `~/.savant/cache` will be populated on the next build (Savant-native AMDs and JARs).
6. If using custom workflows, no changes needed -- custom `fetch { cache() }` blocks continue to work.

### For CI/CD Pipelines

1. Cache `~/.m2/repository` between builds (most CI systems already do this for Maven).
2. Cache `~/.savant/cache` between builds (for Savant-native artifacts).
3. No per-project cache to manage.

---

## 11. Open Questions

1. ~~**Should `.savant/cache` be committable to VCS?**~~ No longer applicable. `.savant/cache` is eliminated for non-integration builds.

2. **Should we add a `savant cache clean` command?** For cleaning `~/.m2` of Savant-related artifacts. **Recommendation**: Low priority; `~/.m2` is shared with Maven/Gradle and has its own cleanup patterns.

3. ~~**How to handle the Savant repository (`repository.savantbuild.org`)?**~~ Resolved. Savant-native artifacts (JARs + authoritative AMDs) are cached globally in `~/.savant/cache`. They are not stored in `~/.m2/repository` since they aren't Maven artifacts. The `CacheProcess` in the standard workflow handles this -- it now points at `~/.savant/cache` (global) instead of `.savant/cache` (per-project).

4. ~~**Should the IDEA plugin be updated?**~~ No. After code review, the IDEA plugin consumes the resolved artifact graph and doesn't read `.savant/cache` directly. It will transparently receive paths pointing to `~/.m2`.

5. ~~**Should AMD files include a schema version?**~~ No. The AMD XML schema is not changing. We're only changing where AMDs come from (in-memory generation vs disk read) and where they're cached. The format itself is untouched.

6. **Is `maven-bridge` still needed?** If Savant dynamically maps Maven artifacts via POM-to-AMD on every build, the manual bridge tool may be unnecessary. **Recommendation**: Evaluate after the new strategy is working. Likely to be deprecated.

7. ~~**Should we add in-memory POM caching?**~~ Yes, add this in Phase 1 as a performance optimization. Parent POMs and BOM POMs are shared across sibling dependencies.

8. ~~**`CacheProcess` default directory change**~~ Resolved. Change the default `dir` in `CacheProcess` from `.savant/cache` (per-project) to `~/.savant/cache` (global per-user). This converges `dir` and `integrationDir` to the same global directory, which is correct:
   - Integration JARs have always been in `~/.savant/cache` (must be global so other projects can use them during local dev)
   - Regular Savant-native artifacts now join them there (no collision -- version strings differ)
   - The path is still overridable via constructor arg or `cache(dir: "/custom/path")` in build files
   - Since users must upgrade the library version to pick up these changes, the default change is expected

---

## Appendix A: Repository Analysis

### Repos Reviewed

| Repo | Relevance | Changes Needed | Testing Framework |
|------|-----------|---------------|-------------------|
| `savant-dependency-management` | Primary | Yes (Workflow, WorkflowDelegate is downstream) | TestNG, BaseUnitTest, test-deps fixtures, HTTP mock |
| `savant-core` | Secondary | Yes (WorkflowDelegate.standard()) | TestNG, BaseUnitTest |
| `dependency-plugin` | Consumer only | No code changes needed | TestNG, Groovy tests |
| `idea-plugin` | Consumer only | No code changes needed | TestNG, Groovy tests |
| `maven-bridge` | Out of scope | May be deprecated | TestNG |
| `pom-plugin` | No interaction | No changes | TestNG, Groovy tests |
| `savant-utils` | No interaction | No changes | TestNG |

### Key Source Files by Repo

**savant-dependency-management** (changes needed):
- `src/main/java/org/savantbuild/dep/workflow/Workflow.java` -- Main refactoring target
- `src/main/java/org/savantbuild/dep/workflow/FetchWorkflow.java` -- No API change, but behavior changes
- `src/main/java/org/savantbuild/dep/workflow/PublishWorkflow.java` -- Reduced usage (no AMD publishing)
- `src/main/java/org/savantbuild/dep/workflow/process/CacheProcess.java` -- Unchanged (still used by custom workflows)
- `src/main/java/org/savantbuild/dep/workflow/process/MavenCacheProcess.java` -- Unchanged
- `src/main/java/org/savantbuild/dep/DefaultDependencyService.java` -- Minor changes to `publish()`
- `src/main/java/org/savantbuild/dep/maven/MavenTools.java` -- Audit target for mapping strategy
- `src/test/java/org/savantbuild/dep/workflow/process/WorkflowTest.java` -- Major test updates
- `src/test/java/org/savantbuild/dep/DefaultDependencyServiceTest.java` -- Test updates
- `src/test/java/org/savantbuild/dep/workflow/process/CacheProcessTest.java` -- Tests unchanged

**savant-core** (changes needed):
- `src/main/java/org/savantbuild/parser/groovy/WorkflowDelegate.java` -- Update `standard()`
- `src/test/java/org/savantbuild/parser/groovy/GroovyBuildFileParserTest.java` -- Test updates
