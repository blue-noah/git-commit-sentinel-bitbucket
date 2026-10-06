# GitHub App: diuis-repo-settings

Runs [safe-settings](https://github.com/github-community-projects/safe-settings) via GitHub Actions
(`.github/workflows/safe-settings-sync.yml`) to apply the declarative repository settings in
`.github/repos/git-commit-sentinel-bitbucket.yml`: no merge commits, and a ruleset on the default branch
(no deletion, no force push, linear history, pull requests, and the `test`, `e2e (9.4.13)` and
`e2e (9.4.26)` checks required on an up-to-date branch).

This repository is its own "admin repo", like git-commit-sentinel. The app is the same one:

- App ID: `4951095`
- Client ID: `Iv23lidyUXa4TyCjLnD5`

Never committed here (GitHub Actions repository secrets only):
- Client secret
- Private key (.pem)

App ID, client ID and organization are public identifiers, consumed as repository **variables**:
`SAFE_SETTINGS_APP_ID`, `SAFE_SETTINGS_GITHUB_CLIENT_ID`, `SAFE_SETTINGS_GH_ORG`. The client secret and
private key are repository **secrets**: `SAFE_SETTINGS_GITHUB_CLIENT_SECRET`, `SAFE_SETTINGS_PRIVATE_KEY`.
