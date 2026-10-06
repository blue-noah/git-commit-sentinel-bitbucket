# 3. Same rules, names and levels as git-commit-sentinel

## Status

Accepted

## Context

Developers meet the same rules twice: locally (client hook) and on push
(this plugin). A message accepted by one and rejected by the other would make
one of them look broken.

## Decision

Port git-commit-sentinel's rules 1:1: same header grammar, same rule names
(`format`, `type`, `description-empty`, `description-period`, `header-length`,
`body-blank-line`), same default levels, same `off`/`warn`/`error` semantics,
same default types and header length (100, counted in characters). Its test
cases are ported in `RuleSetTest`.

The wording of the messages is not part of the parity: the plugin shows the
developer's text as written, with only the characters that could attack or
deceive the reader neutralized.

Each rule is one small class implementing the sealed `Rule`, listed in
`RuleSet.standard()`; the `rules` package has no Bitbucket dependency and is
tested in isolation.

## Consequences

- A rule change must land in both repositories. A shared, language-neutral
  file of test vectors is the natural next step to make drift impossible.
