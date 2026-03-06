# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Code Style

Never delete existing comments when modifying code. If method signatures change, update the comment parameters accordingly rather than removing the comment.

## Versioning

When building a new version of a project, **always bump the version in `build.savant`** before running `sb int`. Use semantic versioning:
- **Patch** (e.g., `2.0.2` → `2.0.3`): Bug fixes only
- **Minor** (e.g., `2.0.2` → `2.1.0`): New features (e.g., adding new cache routing behavior)
- **Major** (e.g., `2.0.2` → `3.0.0`): Breaking API changes with no backward compatibility

When this library's version changes, also update the dependency version in downstream projects (e.g., `savant-core/build.savant` and its `idea.settings.moduleMap`). When using `sb int` to publish an integration build, downstream projects must reference the **integration version** (e.g., `2.1.0-{integration}`) in their `build.savant` dependency declaration — not the bare version (e.g., `2.1.0`).

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

## Project Context

This is a **library** used by the Savant build runtime (`savant-core`, `dependency-plugin`, etc.) — not a standalone tool. All sibling Savant projects live one directory up at `/Users/bpontarelli/dev/os/savant/`.

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
- **`Process`** interface — Implemented by `CacheProcess`, `URLProcess`, `MavenProcess`, `SVNProcess`.

### Cache Routing via FetchResult

Each fetched artifact is tagged with an `ItemSource` (SAVANT or MAVEN) via a `FetchResult` record. This controls publish routing:

- `CacheProcess(output, savantDir, mavenDir)` — Manages both Savant and Maven caches. Fetch tries savantDir first (SAVANT), then mavenDir (MAVEN). Publish routes SAVANT items to savantDir, MAVEN items to mavenDir. Either dir can be null.
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
