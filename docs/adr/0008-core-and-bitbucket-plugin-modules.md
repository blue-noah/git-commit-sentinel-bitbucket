# 8. A core module and a Bitbucket plugin module

## Status

Accepted

## Context

Bitbucket 10 runs on Java 21, Spring 6 and Spring Scanner 6, and injects with
`jakarta.inject` 2 (package `jakarta.inject`) where Bitbucket 9.4 uses the
`javax.inject` package: one plugin jar can't serve both major versions. The
repository hook API the adapters use is the same: the code compiles, and its
tests pass, against Bitbucket 10.5.1 once `javax.inject` becomes
`jakarta.inject`.

The domain and the application (ADR 0007) know nothing of Bitbucket, so they
can be shared by one plugin per major version.

## Decision

A Maven multi-module build, in parallel (`-T1C`):

- `core`: `domain` and `application`, plain Java 17, no dependencies.
- `bitbucket-plugin`: the Bitbucket 9.4 adapters, the plugin descriptor and
  resources; artifact `git-commit-sentinel-bitbucket`, so the plugin key and
  the jar name are unchanged.

The plugin jar embeds `core` (AMPS unpacks it into the bundle). This amends ADR
0002: still no third-party library is bundled, only this project's own code.

Each module meets the quality gates with its own tests: 100% JaCoCo coverage
and 100% PIT mutation score; the plugin's gates cover the adapters only.

## Consequences

- The compiler enforces that the core never depends on Bitbucket.
- Supporting Bitbucket 10 means a second plugin module (e.g.
  `bitbucket-10-plugin`) on the same `core`, with its own adapters, built
  against `bitbucket-parent` 10 and Java 21.
- PIT must run in the same Maven invocation as `verify`
  (`mvn verify org.pitest:pitest-maven:mutationCoverage`), so the reactor
  resolves `core` for the plugin module.
- AMPS and the Spring Scanner plugin are not marked thread-safe: Maven warns
  about the parallel build, without consequence for a two-module reactor.
