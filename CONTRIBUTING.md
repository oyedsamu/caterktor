# Contributing to CaterKtor

Thanks for your interest in CaterKtor. This document describes how to build the
project, the coding standards we hold public API to, and the process for
getting a change merged.

CaterKtor is an opinionated application networking layer on top of Ktor 3.x
for Kotlin Multiplatform. The design intent is documented in `PRD-v2.md`. If
you are proposing a feature rather than a fix, read `PRD-v2.md` first — a
meaningful number of "obvious" features are explicit non-goals.

## Building

### Prerequisites

- JDK 11 or newer.
- Android SDK (for the `android` target).
- Xcode with the command-line tools (for the Apple targets: `iosArm64`,
  `iosSimulatorArm64`, `iosX64`, `macosArm64`, `macosX64`). Apple targets can
  only be built on macOS.
- No separate Kotlin install is required; the Gradle wrapper pins the
  toolchain.

### Common commands

```
./gradlew build      # full build across all configured targets
./gradlew check      # all tests plus lint
./gradlew apiCheck   # verify public API matches the committed .api dumps
./gradlew apiDump    # regenerate public API dumps
```

`apiDump` must be run and the updated `.api` files committed in the same
pull request whenever the public API changes. CI enforces this via
`apiCheck`.

If you only have access to a subset of platforms (for example, Linux with no
Xcode), run the target-specific tasks for what you can build locally; CI
exercises the full matrix.

## Coding standards

These are enforced by CI and by review. They apply to every module.

- **`explicitApi()` is on in every published module.** Every public
  declaration has an explicit visibility and an explicit return type. No
  inferred public types.
- **Zero `@Suppress` in public API.** If you believe you need one, open an
  issue first and explain why; do not file a PR that adds a suppression to a
  public declaration.
- **KDoc on every public type and public function.** Non-obvious behavior
  earns a worked example in KDoc. Public KDoc samples are compiled and
  tested (see PRD-v2 §9).
- **Experimental APIs are annotated `@ExperimentalCaterktor`** until they
  stabilize. `Interceptor` ships experimental in 0.1.0. See PRD-v2 §9 for
  the stabilization policy.
- **Cancellation is sacred.** Never catch `CancellationException`. Never
  convert it to a `NetworkError`. It propagates, always. A `try { ... }
  catch (t: Throwable)` that swallows cancellation is a release blocker.
- **No global state.** No process singletons, no static mutable token
  stores, no hidden `object` that outlives a `NetworkClient`. Everything is
  constructed, scoped, and disposable.
- American English in all identifiers, KDoc, and docs.

## Binary compatibility policy

`kotlinx-binary-compatibility-validator` runs in CI from commit one.

- Any change that affects the public API requires running
  `./gradlew apiDump` and committing the updated `.api` files in the same
  PR as the code change.
- A public API is **removed** only after it has been marked
  `@Deprecated(level = DeprecationLevel.WARNING)` for at least one minor
  release, then `DeprecationLevel.ERROR` for at least one further minor
  release. Minimum two minors of notice. No shortcuts, no "small rename,
  nobody uses it yet."
- Experimental APIs annotated `@ExperimentalCaterktor` are exempt from the
  deprecation window but still appear in the API dump and still require an
  `apiDump` refresh when they change.

If you are unsure whether a change affects the public surface, run
`./gradlew apiCheck` locally. The diff is the answer.

## Commit messages

We use Conventional Commits. The allowed types are:

- `feat:` — a new feature.
- `fix:` — a bug fix.
- `docs:` — documentation only.
- `refactor:` — behavior-preserving code change.
- `test:` — tests only.
- `chore:` — tooling, dependencies, housekeeping.
- `ci:` — CI configuration.
- `perf:` — measurable performance improvement.
- `build:` — build system or published artifacts.

Scope is optional but strongly preferred when a change is localized to a
module. Use the module name, for example:

```
feat(core): introduce Chain
fix(auth): deduplicate concurrent 401 refreshes
docs(readme): document deadline vs withTimeout
```

Subjects are in the imperative mood, present tense, with no trailing
period. Soft cap of 72 characters on the subject line. Breaking changes go
in the body with a `BREAKING CHANGE:` trailer and a migration note.

## Pull requests

- **One logical change per PR.** Unrelated cleanups go in a separate PR.
- **Include a test.** If the change is infrastructure or documentation,
  say so in the PR body and why a test is not possible.
- **Run on all affected KMP targets.** If you change a platform-specific
  source set, run the tests for that target locally before pushing.
- **`./gradlew apiCheck` must pass.** If public API changed,
  `./gradlew apiDump` must have been run and the resulting `.api` files
  committed in the same PR.
- **KDoc updated** for any changed public surface, including any new
  `@ExperimentalCaterktor` annotations.
- **No new `@Suppress` in public API.** Internals are fine when justified.
- **Cancellation is not swallowed** anywhere the change touches.
- **Every public-API change is reviewed by the Tech Lead.** Do not merge
  without that approval.

The PR template captures this as a checklist. Do not delete the checklist.

## What not to contribute

These are explicit non-goals. PRs adding them will be closed.

- **Our own Retrofit-style annotation codegen.** We ship a Ktorfit adapter
  (`caterktor-ktorfit`) and co-develop with Ktorfit upstream. A second
  declarative layer is not on the table. See PRD-v2 §5.2.
- **XML support.** Not in scope at any milestone. Bring your own converter
  if you need it.
- **gRPC support.** Different transport, different project.
- **Required DI bindings.** No Hilt, Koin, Dagger, or Kodein modules as
  required dependencies. CaterKtor composes into any DI container; it
  requires none.
- **Logger adapters** (Timber, Napier, Kermit, SLF4J, etc.) as first-party
  artifacts. Events are a `SharedFlow`; users wire a logger in three
  lines. See PRD-v2 §11.

If you think an exception is warranted, open an issue and argue the case
before writing code.

## Reporting security issues

Do not open a public issue for security reports. See `SECURITY.md`.

## Code of conduct

Participation in this project is governed by the Contributor Covenant
`CODE_OF_CONDUCT.md`. Violations can be reported to `oyedsamu@gmail.com`.
