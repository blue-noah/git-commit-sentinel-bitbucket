# GitHub App: diuis-repo-settings

Runs [safe-settings](https://github.com/github-community-projects/safe-settings) via GitHub Actions
(`.github/workflows/safe-settings-sync.yml`) to apply the declarative repository settings in
`.github/repos/git-commit-sentinel-bitbucket.yml`: no merge commits, and a ruleset on the default branch
(no deletion, no force push, linear history, pull requests, and the `test`, `e2e (9.4.13)` and
`e2e (9.4.26)` checks required on an up-to-date branch).

This repository is its own "admin repo", like git-commit-sentinel. The app is the same one, App ID
`4951095`.

The sync authenticates as the app, so it needs only:

- repository **variables** (public identifiers): `SAFE_SETTINGS_APP_ID`, `SAFE_SETTINGS_GH_ORG`;
- repository **secret**: `SAFE_SETTINGS_PRIVATE_KEY`, the app's private key (.pem), never committed.

The app's client ID and client secret are not needed: neither safe-settings nor Probot reads them during a
sync (Probot only writes them when it creates an app from a manifest).
