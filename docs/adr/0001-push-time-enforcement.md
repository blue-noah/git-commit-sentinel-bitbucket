# 1. Enforce at push time, on commits new to the repository

## Status

Accepted

## Context

A server cannot see, let alone block, a local commit: by the time Bitbucket
hears about a commit it already exists on the developer's machine. The
client-side [git-commit-sentinel](https://github.com/blue-noah/git-commit-sentinel)
gives instant feedback at commit time, but it is opt-in and skippable
(`--no-verify`). The server is where the rules can actually be enforced.

## Decision

A `PreRepositoryHook<RepositoryPushHookRequest>` registers a commit callback
with `RepositoryHookCommitFilter.ADDED_TO_REPOSITORY`. Bitbucket streams each
commit that is new to the repository exactly once; commits already reachable
from another ref (e.g. `main` merged into a feature branch) are never
re-validated, and no history walk or `git` call is made by the plugin.

- `error` finding → the push is rejected, with every finding printed.
- `warn` finding → printed as `remote:` lines; the push proceeds.

## Consequences

- Commit-time feedback stays the client hook's job; the two are complementary.
- Rewriting an already-pushed bad commit requires a force-push to the feature
  branch, which is the normal workflow for feature branches.
- Output is capped (20 commits shown in full) so a large push can't flood the
  terminal; the count of rejected commits is always exact.
- **Known limitation**: a commit already in the repository is never checked
  again. Commits pushed first to a branch that isn't checked (e.g. `tmp/x`)
  pass unchecked when a feature branch is later created on them (verified on
  Bitbucket 9.4.13). `ADDED_TO_ANY_REF` doesn't close this gap either (it also
  skips commits reachable from other refs when a branch is created) and it
  re-checks `main`'s commits whenever a feature branch merges or rebases onto
  `main`, so it was rejected. Mitigations, by configuration: a broader
  `branchPattern` (e.g. `(?!main$|develop$|release/).+`, every branch except
  the protected ones), and Bitbucket branch permissions restricting who can
  create branches outside the checked pattern.
