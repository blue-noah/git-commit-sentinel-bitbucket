# 7. Hexagonal architecture

## Status

Accepted

## Context

What a push check decides (which branches, who is exempt, which commits, when
to reject, how much to report) was written against Bitbucket's types
(`RefChange`, `CommitAddedDetails`, `RepositoryHookResult`), mixed with the
translation from and to Bitbucket. It could only be tested through mocks of
Bitbucket's API, and a Bitbucket API change would touch the decisions too.

## Decision

Three layers, one package each, with dependencies pointing inwards only:

- `domain`: the rules, plain Java (the `rules` package of ADR 0003).
- `application`: the `CheckPush` use case and `PushedCommitsCheck`, working on
  their own types (`PushedRef`, `PushedCommit`, `SentinelConfig`,
  `PushVerdict`) and telling the developer through the `PushReport` port.
- `adapter`: everything that knows Bitbucket, split by direction.
  - `adapter.inbound`, where Bitbucket calls in: `SentinelHook` turns a push into
    the use case, `PushedCommitsListener` streams the commits into it,
    `SettingsParser` reads the hook settings.
  - `adapter.outbound`, where the application calls out: `PusherTerminal`
    implements `PushReport`.

  The pusher's terminal belongs to a single push, so `SentinelHook` creates it
  for each push: an inbound adapter may depend on an outbound one, never the
  other way round.

Domain and application carry no framework annotation: `plugin-context.xml`
creates their objects. `ArchitectureTest` fails if a layer imports from a layer
outside it, or an outbound adapter from an inbound one.

## Consequences

- The push decisions are tested with plain objects and a mocked port; the
  adapter tests check only the translation and fail-open.
- A Bitbucket API change stays in `adapter`.
- A few more types than a plugin this size strictly needs.
