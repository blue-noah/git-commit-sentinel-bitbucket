# 5. Fail open on internal errors

## Status

Accepted

## Context

If the plugin itself breaks (a bug, an unexpected API behaviour, unreadable
settings), either every push is blocked until an admin intervenes, or some
commits go unchecked for a while.

## Decision

Fail open. Any unexpected exception during a check is logged at `ERROR` with
the repository, and the push is accepted with a `remote:` warning asking the
developer to tell their administrator.

## Consequences

- A plugin bug never stops a team from shipping.
- An outage of the checks is visible (logs, developer warning) but not
  enforced; the client-side hook still covers developers who installed it.
