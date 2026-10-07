# Contributing

Changes reach `main` through pull requests only: linear history, no merge
commits, and three checks green (`test`, `e2e (9.4.13)`, `e2e (9.4.26)`).
How to build and test: [README](README.md#build--test). Why things are built
the way they are: [docs/adr](docs/adr).

## Enforced by the build

Some of the rules below fail the build when broken: `CodingRules`, checked by
each module's `CodingRulesTest` with ArchUnit, covers static methods (factories
only), static fields (final), injection (constructors only), mutable fields
(only in the objects of a single push), BDDMockito and AssertJ BDD only, and
classpath files injected in tests; `ArchitectureTest` covers the layers;
Spotless the formatting; JaCoCo and PIT the gates. The others rely on review.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/), as the plugin
itself enforces: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `build:`,
`ci:`, `chore:`, with `!` for a breaking change. The body says why.

## Code

- **Readable without comments.** No comments in production or test code:
  names say what, tests and ADRs say why. A short class Javadoc only where the
  role of a class isn't obvious from its name (the Bitbucket entry points, for
  instance).
- **Self-explanatory names**, even long ones: `isCheckedOtherwiseToldSkipped`
  beats a short name plus a comment.
- **Functional Java 17**: `var`, records, `Optional` pipelines,
  `Predicate.not`, switch expressions.
- **Strings**: `"...%s...".formatted(...)` with `%s` only (locale-safe), or
  text blocks; no `+` concatenation.
- **No static methods except factory methods** (`of`, `parse`, `standard`):
  helpers are instance methods, utility classes are objects.
- **No production code that only tests use.**
- **Dependency injection** through constructors only. Long-lived
  collaborators are wired in `plugin-context.xml` (the core carries no
  annotations); objects that belong to one push are created with `new` during
  that push.
- **Hexagonal layers** (ADR 0007): `domain` and `application` in `core`,
  adapters in the plugin module. ArchUnit enforces it.
- **Formatting**: palantir-java-format through Spotless; run
  `mvn spotless:apply`.
- **Untrusted text** (commit messages, branch names, usernames, settings)
  reaches a terminal, a log or a form only through `ControlCharacters`.

## Tests

- **Structure**: `// given`, `// when`, `// then` (or `// when / then`), and
  nothing else as comments. The object under test is called `sut`.
- **Assertions**: AssertJ in BDD style, `BDDAssertions.then(...)`.
- **Mocks**: BDDMockito only, `given(...).willReturn(...)` and
  `BDDMockito.then(mock).should()...`; no `when`/`verify`.
- **Public contracts as literals**: setting keys, hook key and messages that
  users or scripts rely on are written out in tests, so renaming one fails a
  test instead of breaking users silently.
- **Classpath files** are injected with inject-resources
  (`@TestWithResources`, `@GivenTextResource`).
- **Parallel**: JUnit runs classes and methods concurrently; no shared mutable
  state between tests.
- **Gates**: 100% JaCoCo coverage and 100% PIT mutation score per module
  (ADR 0009). Run `mvn verify org.pitest:pitest-maven:mutationCoverage`
  before opening a pull request, and the end-to-end tests when the change
  touches what Bitbucket sees (wiring, descriptor, form, hook behaviour).
