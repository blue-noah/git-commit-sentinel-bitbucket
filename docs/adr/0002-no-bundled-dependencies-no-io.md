# 2. No bundled libraries, no I/O on the push path

## Status

Accepted

## Context

Every push to a checked branch runs this code. Any added latency or failure
mode (network calls, a database query per push, a third-party library's bug or
CVE) is paid by every developer, on every push.

## Decision

- Only APIs Bitbucket itself provides (`provided` scope); the plugin jar bundles
  no library.
- The push path performs no network or database access of its own: the hook
  settings arrive with the hook invocation (see [ADR 0004](0004-project-and-repository-configuration.md)).
- The branch regex is compiled on each push that touches the hook: microseconds against a push
  that costs milliseconds, so no cache (and no shared mutable state) is worth having.

## Consequences

- Validation cost is a handful of regex matches per new commit.
- A push that touches no feature branch costs a regex match per ref and nothing else.
