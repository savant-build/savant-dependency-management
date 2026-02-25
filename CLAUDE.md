# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow

Code must be reviewed before it is committed to a feature branch. Always present changes for review and wait for approval before committing.

## Code Style

Never delete existing comments when modifying code. If method signatures change, update the comment parameters accordingly rather than removing the comment.

## Build Commands

This project uses the Savant build system. The CLI command is `sb`.

```bash
sb compile          # Compile Java sources
sb test             # Run all tests (depends on jar)
sb test --test=CacheProcessTest   # Run a single test class
sb jar              # Build JAR (depends on compile)
sb clean            # Clean build output
sb int              # Integration build (depends on test)
sb release          # Full release (depends on clean + test)
```

Java 17 is required. Test framework is TestNG 6.8.7.

## Architecture

This library handles Java dependency resolution for the Savant build system. The resolution process has three phases:

1. **Build DependencyGraph** (`DependencyService.buildGraph`) — Recursively fetches ArtifactMetaData (AMD) files and Maven POMs to construct a raw dependency graph. Multiple versions of the same artifact can exist in the graph.

2. **Reduce to ArtifactGraph** (`DependencyService.reduce`) — BFS traversal selects the highest compatible version of each artifact, verifies compatibility constraints, and produces a single-version-per-artifact graph.

3. **Resolve to ResolvedArtifactGraph** (`DependencyService.resolve`) — Downloads actual JAR and source files, applies traversal rules (transitivity, license checks), and produces the final graph with file paths.

### Workflow / Process Chain Pattern

Artifact fetching and publishing use a chain-of-responsibility pattern:

- **`Workflow`** — Orchestrates a `FetchWorkflow` + `PublishWorkflow`. Contains `fetchMetaData`, `fetchArtifact`, `fetchSource`, and `loadPOM` methods.
- **`FetchWorkflow`** — Ordered list of `Process` instances. Tries each in order until one returns a non-null `FetchResult`.
- **`PublishWorkflow`** — Ordered list of `Process` instances. Publishes to all processes in the chain.
- **`Process`** interface — Implemented by `CacheProcess`, `MavenCacheProcess`, `URLProcess`, `MavenProcess`, `SVNProcess`.

### Cache Routing via FetchResult

Each fetched artifact is tagged with an `ItemSource` (SAVANT or MAVEN) via a `FetchResult` record. This controls publish routing:

- `CacheProcess` (Savant cache, `~/.savant/cache`) — Only accepts `ItemSource.SAVANT`
- `MavenCacheProcess` (Maven cache, `~/.m2/repository`) — Only accepts `ItemSource.MAVEN`
- `URLProcess` tags items as `SAVANT`; `MavenProcess` tags items as `MAVEN`

Maven POMs are translated to `ArtifactMetaData` in-memory (no AMD files written for Maven-sourced artifacts).

### Key Domain Types

- `Artifact` → `ReifiedArtifact` (+ licenses) → `ResolvedArtifact` (+ file paths)
- `ArtifactID` — Identity: group, project, name, type (e.g., `org.example:mylib:mylib:jar`)
- `ArtifactMetaData` — Dependencies + licenses, parsed from AMD files or translated from POMs
- `Dependencies` — Map of named `DependencyGroup`s (compile, runtime, test, etc.)
- `Version` — Semantic version with support for integration versions (`{integration}` suffix)
- Non-semantic Maven versions (e.g., `4.1.65.Final`) are mapped to semantic versions via `Workflow.mappings`

### Maven Integration

`MavenTools` and `POM` handle Maven POM parsing. `Workflow.loadPOM` recursively loads parent POMs and BOM imports. A `pomCache` avoids redundant parsing. `MavenTools.toSavantDependencies` translates Maven dependencies to Savant's model.

## Test Infrastructure

- **`BaseUnitTest`** — Base class for all tests. Sets up static `workflow`, `cache`, `mavenCache`, `integration` paths, and provides `makeFileServer()` for local HTTP server on port 7042.
- Test fixtures live in `test-deps/savant/` (Savant layout) and `test-deps/maven/` (Maven layout).
- Tests serving from `http://localhost:7042/test-deps/savant` use `URLProcess`; tests using `MavenProcess` point to the same server at `http://localhost:7042/test-deps/maven`.
- The static `workflow` field in `BaseUnitTest` is shared across test classes. Tests that override it should reset it in `@BeforeMethod` to avoid polluting other tests.
- Some tests hit Maven Central directly (e.g., `WorkflowTest`, `mavenCentralComplex`) and may fail due to HTTP/2 rate limiting when run concurrently. These pass when run individually.
