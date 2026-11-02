# Contributing to Ward

Thanks for helping. Ward is in alpha, so bug reports, live harness checks and docs are as useful as
code.

## Ways to help

- **Report a bug or ask for a feature** with the [issue templates](https://github.com/RicardoRB/ward-ai/issues/new/choose).
- **Verify an agent integration** you already use (Cursor, Codex, Gemini CLI, Copilot CLI, and
  others). Issues labeled [`help wanted`](https://github.com/RicardoRB/ward-ai/labels/help%20wanted)
  describe the steps.
- **Improve the docs.** Issues labeled
  [`good first issue`](https://github.com/RicardoRB/ward-ai/labels/good%20first%20issue) are a
  good start and need no Java.

## Docs changes

The site lives in `website/` (Astro Starlight; pages under `website/src/content/docs/`).
It needs Node 22.

```bash
cd website
npm ci
npm run dev     # preview at http://localhost:4321/ward-ai/
npm run build   # also validates internal links
```

## Code changes

You need JDK 25. The Maven wrapper is included.

```bash
./mvnw -q test                        # run all tests
./mvnw -Dtest=ClassName#method test   # run one test
./mvnw spotless:apply                 # format
./mvnw package                        # build target/ward.jar
```

Before you open a pull request, run Ward on Ward itself:

```bash
java -jar target/ward.jar check --profile pre-push
```

Read [AGENTS.md](AGENTS.md) first. It covers the package layout, the patterns to copy, and the
static-analysis rules. In short:

- Fix static-analysis findings at the cause. Do not add suppressions or edit `config/`.
- New behavior needs tests that assert it.
- CLI changes update the matching page under `website/`; `ward init` changes also update `skills/`.

## Pull requests

1. Fork, then branch from `main`.
2. Keep each pull request to one change, with a [Conventional Commits](https://www.conventionalcommits.org/)
   title (`fix:`, `feat:`, `docs:` …).
3. Link the issue it closes.
4. Add a line to the changelog for each user-visible change. The entry goes in the section for the
   version in `pom.xml`, in [CHANGELOG.md](CHANGELOG.md). A pull request check fails when that section is missing.

## Releases

Releases are tags on `main` (`vX.Y.Z`). The release workflow publishes the tag's `CHANGELOG.md`
section as the GitHub release notes, followed by the list of merged pull requests. Before tagging:

1. Bump the version in `pom.xml` and `.claude-plugin/plugin.json`.
2. In `CHANGELOG.md`, rename the section to `## [X.Y.Z] - YYYY-MM-DD`, and add its compare link at the bottom.
3. Tag `vX.Y.Z` on `main`. The release fails before publishing when `CHANGELOG.md` has no section for the tag.

By contributing you agree that your work is licensed under the [Apache 2.0 License](LICENSE).
Report vulnerabilities privately through
[GitHub Security Advisories](https://github.com/RicardoRB/ward-ai/security/advisories/new), not in
a public issue.
