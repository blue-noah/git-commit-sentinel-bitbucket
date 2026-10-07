# 9. Testing strategy and quality gates

## Status

Accepted

## Context

The plugin runs inside every push to the repositories it guards: a bug either
blocks developers or lets bad commits through, and a Bitbucket upgrade can
change behaviour under it. Unit tests alone don't show that the plugin loads
in Bitbucket's OSGi container, nor that a test would notice a broken line of
production code.

## Decision

- **Coverage and mutation gates at 100%**, per module, failing the build:
  JaCoCo (instructions, lines, branches) and PIT with the `STRONGER` mutators.
  Every line is executed by a test, and every mutant is killed by an
  assertion. PIT runs in the same Maven invocation as `verify`.
- **End-to-end tests on a real Bitbucket** (`e2e/`): Atlassian's official
  image in a Podman pod, the plugin installed through REST as an admin would,
  real `git push`es. CI runs them on the production version (9.4.13) and on
  the latest 9.4 patch, with Atlassian's public timebomb license.
- **The architecture is tested** with ArchUnit: dependencies point inwards
  (ADR 0007), outbound adapters never depend on inbound ones, and
  `adapter.text` depends on the JDK only.
- **No deceptive characters in sources** ("Trojan Source"): a test rejects
  invisible and bidirectional characters in every source file.
- **Test resources are injected** with inject-resources
  (`@GivenTextResource`). It is a small library with known risks: its last
  release is 1.0.0 (2024), it depends on the unmaintained `reflections`, and
  it declares JUnit 5.11 while the project runs JUnit 6 (the project's BOM
  wins). It is test scope only; if it ever breaks, reading a classpath file
  takes a six-line method.

How tests are written (style and libraries) is a convention, not a decision:
see [CONTRIBUTING.md](../../CONTRIBUTING.md).

## Consequences

- A change is not done until every mutant it introduces is killed: some tests
  exist only to pin a boundary or a message.
- CI takes minutes, not seconds, and depends on Docker Hub and Maven Central;
  a network failure there fails a job that a rerun fixes.
- A Bitbucket patch that changes behaviour shows up in CI before an upgrade.
