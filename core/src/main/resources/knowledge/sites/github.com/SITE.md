---
name: github.com
display: GitHub
description: Code hosting: repos, code, issues, pull requests
domains: ["github.com"]
aliases: ["GitHub", "github"]
apps: []
open: github.com
mode: desktop
---
## What it is
A code-hosting and collaboration website. A repository holds code, documentation, issues and pull requests.

## Pages and URLs
- Repository: `https://github.com/<owner>/<repo>`.
- A repository's issues and pull requests are at `/issues` and `/pulls` after the repository address.
- Search: `https://github.com/search?q=<encoded terms>&type=repositories`; you can also search code, issues and other types.

## Features
- **Repositories**: public repositories can usually be viewed without signing in: files, README, commits, branches, issues and pull requests; private repositories need an account with access.
- **Star**: the Star button at the top right of the repo page is for bookmarking and showing support, and requires sign-in. When the current account has already starred, the button shows Starred; the button state tells whether it is starred.
- **Account**: what is visible and possible after sign-in depends on the account's permissions; button text varies with the UI language.

## Known behavior
- If the repository does not exist, is private, or the current account has no access, the page may just show "not found".
- Star counts may use abbreviations such as `1.2k`.
