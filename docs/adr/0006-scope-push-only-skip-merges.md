# 6. Only `git push`; merge commits are never checked

## Status

Accepted

## Context

Bitbucket also creates commits itself (file edits in the UI, pull request
merges), and git creates merge commits with fixed messages
(`Merge branch 'main' into feature/x`) that developers don't usually write.

## Decision

- Only the `REPO_PUSH` trigger is checked; UI edits and pull request merges are not.
- Merge commits (more than one parent) are always skipped, with no option.

## Consequences

- Syncing a feature branch with `main` by merging never trips the rules.
- Commits created through the Bitbucket UI are not validated; revisit with a new
  ADR if that becomes a gap.
