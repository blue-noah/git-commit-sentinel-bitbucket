# git-commit-sentinel-bitbucket

A Bitbucket Data Center 9.4 plugin that checks commits pushed to feature
branches against [Conventional Commits](https://www.conventionalcommits.org/),
with independently configurable `off`/`warn`/`error` levels per rule.

It is the server-side twin of
[git-commit-sentinel](https://github.com/blue-noah/git-commit-sentinel) (a
client-side `commit-msg` hook): same rules, same names, same defaults. The
client hook gives feedback at commit time; this plugin enforces on push.

- **Download**: the plugin jar is attached to each [release](https://github.com/blue-noah/git-commit-sentinel-bitbucket/releases).
- **Docs site**: https://blue-noah.github.io/git-commit-sentinel-bitbucket/ — the essentials, as a short static page.
- **Design decisions**: [docs/adr](docs/adr).

## What it does

On every `git push`, for each feature branch created or updated (by default
`feature/.+`, configurable), every commit **new to the repository** is checked:

```text
remote: git-commit-sentinel-bitbucket: 1a2b3c4 on feature/login: "wip: stuff"
remote: git-commit-sentinel-bitbucket:   error: [type] type "wip" is not in the allowed list: feat, fix, ...
remote: git-commit-sentinel-bitbucket: 5d6e7f8 on feature/login: "fix: the bug."
remote: git-commit-sentinel-bitbucket:   warning: [description-period] description must not end with a period
```

- Any `error` finding rejects the push; `warning`s are shown and the push proceeds.
- Merge commits, tags, branch deletions and non-feature branches are never checked.
- Bypass users (e.g. CI or release accounts) are never checked.
- Commit messages and branch names are shown as written, except for characters that could attack
  or deceive the reader (terminal escape sequences, carriage returns, bidirectional overrides,
  invisible characters): those are printed as a visible `\u{...}`.
- If the plugin itself fails, the push is accepted and the error logged
  ([fail-open](docs/adr/0005-fail-open.md)).

| Rule | Checks | Default |
|---|---|---|
| `format` | header matches `<type>(<scope>)!: <description>` | error |
| `type` | type is one of the allowed types | error |
| `description-empty` | description is not empty | error |
| `description-period` | description does not end with `.` | warn |
| `header-length` | header ≤ max length (default 100 characters) | warn |
| `body-blank-line` | blank line between header and body | warn |

## Configuration

The hook is **Git Commit Sentinel for Bitbucket** in *Settings → Hooks*
([ADR 0004](docs/adr/0004-project-and-repository-configuration.md)):

- **Project**: enable it once, every repository of the project (present and
  future) is checked.
- **Repository**: override the project settings, or disable the hook.

Changes apply to the next push. Blank fields and `default` levels use the
built-in defaults. A repository override replaces the project settings as a
whole: its blank fields mean the built-in defaults, not the project's values.

| Field (key) | Meaning |
|---|---|
| Feature branch regex (`branchPattern`) | Java regex matched against the whole branch name, without `refs/heads/` |
| Allowed types (`types`) | comma-separated |
| Header max length (`headerMaxLength`) | in characters |
| Rule levels (`rule-<name>`) | `default` / `off` / `warn` / `error` |
| Bypass users (`bypassUsers`) | usernames, one per line (or comma-separated); never checked |

### Exempting CI pushes made with an HTTP access token

A push authenticated with a project or repository HTTP access token is made by a Bitbucket *bot user*
(type `SERVICE`), e.g. `access-token-user/1/3`, shared by every token of that project or repository. Put
that name in `bypassUsers`. To find it:

```sh
curl -s -u admin "$BB/rest/access-tokens/latest/projects/PAY" | jq -r '.values[].user.name'
```

### REST (e.g. from a project-provisioning pipeline)

```sh
HOOK=com.github.bluenoah.git-commit-sentinel-bitbucket:git-commit-sentinel-bitbucket-hook

# enable on a project, with settings (omit the body's fields to use the defaults)
curl -X PUT "$BB/rest/api/1.0/projects/PAY/settings/hooks/$HOOK/enabled" \
     -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"types":"feat,fix,chore","rule-description-period":"error","bypassUsers":"ci-bot"}'
```

| Action | Call |
|---|---|
| Change settings | `PUT …/settings/hooks/$HOOK/settings` with the full JSON |
| Read state | `GET …/settings/hooks/$HOOK` |
| Disable | `DELETE …/settings/hooks/$HOOK/enabled` |
| Repository back to "inherit from project" | `DELETE …/repos/{slug}/settings/hooks/$HOOK` |

`…` is `/rest/api/1.0/projects/{key}` or `/rest/api/1.0/projects/{key}/repos/{slug}`.
Values can be JSON strings, numbers or booleans (`"headerMaxLength": 72` works); Bitbucket
rejects arrays, so lists are comma-separated strings (`"bypassUsers": "ci-bot,release-bot"`).
Invalid settings are rejected with `400` and the offending fields, from the UI
and from REST alike.

## Build & test

Requires JDK 17 or 21 (`sdk env` picks it up from `.sdkmanrc`) and Maven; no
Atlassian SDK needed.

```sh
mvn spotless:apply                           # format the code (palantir-java-format)
mvn verify                                   # format check, tests, JaCoCo coverage check, plugin jar in target/
mvn org.pitest:pitest-maven:mutationCoverage # mutation testing (after verify)
```

Formatting is [palantir-java-format](https://github.com/palantir/palantir-java-format), applied by
[Spotless](https://github.com/diffplug/spotless): `verify` fails on unformatted code, `spotless:apply`
fixes it.

Both test quality gates are at **100%** and fail the build below it (CI runs both):

- **Coverage** (JaCoCo): instructions, lines and branches. Report: `target/site/jacoco/index.html`.
- **Mutation score** (PIT, `STRONGER` mutators): every mutant must be killed by an assertion.
  Report: `target/pit-reports/index.html`. PIT's own line coverage isn't gated: unlike JaCoCo it
  counts the private constructors of utility classes, which nothing should call.

### End-to-end tests, on a real Bitbucket

```sh
mvn package -DskipTests && e2e/run.sh   # Bitbucket 9.4.13, the version the plugin is built against
BITBUCKET_VERSION=9.4.26 e2e/run.sh     # any other release
KEEP=1 e2e/run.sh                       # leave it running: http://localhost:7990 (admin/admin)
```

`e2e/run.sh` starts Atlassian's official image (`atlassian/bitbucket`, which ships its own JDK and a
git that release supports: 2.47.3 in 9.4.13) as a pod (`podman kube play e2e/bitbucket-pod.yaml`),
installs the plugin through the REST API as an admin would, runs `e2e/e2e.sh` and removes the pod.

- Needs Podman, with at least **4 GiB** of memory for its machine on macOS
  (`podman machine set --memory 4096`).
- The license is Atlassian's public [timebomb license](https://developer.atlassian.com/platform/marketplace/timebomb-licenses-for-testing-server-apps/)
  for Bitbucket Data Center (`e2e/timebomb-license.yaml`): it expires 3 hours after *each* Bitbucket
  start, so every run gets a fresh one. Nothing to renew.
- The bundled code search (OpenSearch) is off: the tests don't need it, and it doesn't fit in 4 GiB.
- CI runs them on 9.4.13 (production) and on 9.4.26 (latest 9.4 patch), to catch a patch that
  changes behaviour before upgrading.

## Install

Upload `git-commit-sentinel-bitbucket-<version>.jar`, from the [latest
release](https://github.com/blue-noah/git-commit-sentinel-bitbucket/releases) (checksum in `SHA256SUMS.txt`),
from *Administration → Manage apps → Upload app*. Recent Bitbucket releases disable uploading apps by default ("Plugins cannot be
installed via upload"): start Bitbucket with `-Dupm.plugin.upload.enabled=true` (e.g. in
`JVM_SUPPORT_RECOMMENDED_ARGS`) to allow it, as `e2e/bitbucket-pod.yaml` does.

## Release

Push a tag `vX.Y.Z`: CI sets the version, runs every check (format, tests, coverage, mutation testing) and
publishes a GitHub release with the jar and its SHA-256.

## Design

- [docs/adr](docs/adr) — why it's built this way.
Hexagonal architecture ([ADR 0007](docs/adr/0007-hexagonal-architecture.md)), dependencies pointing inwards only:

- `domain/` — the rules, plain Java.
- `application/` — the push check use case, and the `PushReport` port it tells the developer through.
- `adapter/inbound/` — where Bitbucket calls in: the repository hook, the stream of pushed commits, the hook settings.
- `adapter/outbound/` — where the application calls out: the pusher's terminal, implementing `PushReport`.

No bundled libraries: only APIs Bitbucket provides.

## License

[MIT](LICENSE)
