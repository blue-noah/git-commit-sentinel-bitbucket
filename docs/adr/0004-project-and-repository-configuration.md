# 4. Configuration per project and repository, through Bitbucket's own hook settings

## Status

Accepted

## Context

Teams need different rules (types, levels, feature branch naming), and admins
need to change them at runtime. Some organisations create projects through an
external orchestrator and want to configure the hook as part of that flow.

A global layer was considered and dropped: an always-on global hook plus a
global admin page would add a second hook, a settings store, a cache that must
converge across cluster nodes and an admin UI — a lot of code for something an
orchestrator does with one REST call per project.

## Decision

One configurable repository hook, `commit-sentinel-hook`, with project and
repository scope — nothing else:

- **Project**: enabling it (with or without settings) covers every repository
  of the project, present and future.
- **Repository**: can override the project settings, or disable the hook.
- Blank fields and `default` levels mean the built-in defaults, identical to
  git-commit-sentinel's.

Settings are Bitbucket's native hook settings: edited from *Settings → Hooks*
or the REST API (`PUT /rest/api/1.0/projects/{key}/settings/hooks/{hook}/enabled`
with the settings as JSON), validated by the hook on save in both cases, and
handed to the hook by Bitbucket on every push.

## Consequences

- No storage, cache, admin page or extra hook of our own: Bitbucket handles
  persistence, inheritance and cluster consistency.
- Enabling on every project is the orchestrator's (or an admin's) job; there is
  no "on everywhere" switch.
- A repository override replaces the project settings as a whole (Bitbucket
  semantics): fields left blank there mean the built-in defaults, not the
  project's values.
