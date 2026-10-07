# Architecture Decision Records

One file per significant decision, in the standard [ADR
format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
(Nygard): Status, Context, Decision, Consequences. Never edited after
acceptance — a changed decision gets a new ADR that supersedes the old one.

| ADR | Decision |
|---|---|
| [0001](0001-push-time-enforcement.md) | Enforce at push time, on commits new to the repository |
| [0002](0002-no-bundled-dependencies-no-io.md) | No bundled libraries, no I/O on the push path |
| [0003](0003-rule-parity-with-client-hook.md) | Same rules, names and levels as git-commit-sentinel |
| [0004](0004-project-and-repository-configuration.md) | Configuration per project and repository, through Bitbucket's hook settings |
| [0005](0005-fail-open.md) | Fail open on internal errors |
| [0006](0006-scope-push-only-skip-merges.md) | Only `git push`; merge commits are never checked |
| [0007](0007-hexagonal-architecture.md) | Hexagonal architecture |
