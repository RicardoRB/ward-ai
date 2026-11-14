# Changelog

All notable changes to Ward are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and Ward uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Ward is in alpha, so minor versions may
still change behavior. Breaking changes are called out under **Changed**.

## [Unreleased]

### Added

- Native Android rules, `ward:android/*`, that run in the `ward` gate over `AndroidManifest.xml` and the XML files under `res/` (`layout`, `menu`, `xml`, `values`, `drawable`, `navigation`, `color`), with no Android SDK and no Gradle run: `exported-service`, `exported-receiver` and `exported-content-provider` (an exported component with no permission), `debuggable-application`, `cleartext-transport` (the manifest flag and `cleartextTrafficPermitted` in a network security config), `allow-backup`, `hardcoded-text` and `missing-content-description`. They follow the ids and documented behavior of Android Lint (Apache-2.0); the `standard` and `strict` presets select them. The `android-lint` gate stays for the checks that need the compiled platform API, such as `NewApi`. `tools:ignore` is not honored: turn a rule off in `ward-rules.toml`. Not in this version: `unused-resources`, which needs the references of the Java and Kotlin sources (#550).

## [0.8.0] - 2026-11-13

### Added

- Native OpenAPI lint rules, `ward:openapi/*`, for OpenAPI 3.0 and 3.1 documents in YAML or JSON: the OpenAPI 3.x rules of Spectral 6.17.1 (`spectral:oas` with every rule) and of its OWASP ruleset 2.0.1, with Spectral's severities, positions and messages, a JSON Schema validator for the document and the examples, and `$ref` resolution inside the project (#522). `ward rules list` shows them, and `ward-rules.toml` configures them.
- The `secrets` check (`uses = "secrets"`), native secret detection that needs no executable: the 222 rules of the Gitleaks 8.30.1 default configuration run inside Ward as `ward:secrets/<gitleaks-rule-id>`, over every text file, in the fast profiles like `ward-format`. Findings carry the rule, file, line and column and never the secret or any source text. Options: `exclude-paths`, `scan-history` and `history-since` on the check, and `entropy` and `exclude-paths` per rule in `ward-rules.toml` (#516).
- `ward check` text output shows what the `ward` gate checks: one result line per rule category (`✓ ward · style`, `✗ ward · correctness — 3 errors`) instead of the single `ward` line, and on an interactive terminal the running line names the current phase (`… ward: analyzing 12 of 812 files with 431 rules`). JSON and SARIF output are unchanged.
- The `crap` check (`uses = "crap"`) scores every function as `complexity^2 * (1 - coverage)^3 + complexity` and fails the functions above `max` (default 30). Complexity is the value of the language's complexity rule; coverage is read per line from a JaCoCo XML or lcov report, by default at the build tool's conventional path, or from `report`. The gate runs no tests, treats a missing, unparseable or stale report as an execution error, and is opt-in.
- `ward init --gate crap` offers the `crap` gate for Maven, Gradle, Vitest, Jest, Bun and Python projects, after the gate that produces the coverage data. No preset selects it. It is not offered for Swift, Android and Angular (`ng test`) projects.
- The native `vulnerabilities` check (`uses = "vulnerabilities"`): it matches the resolved dependencies of every toolchain against OSV advisories, with the version ordering of Maven, semver and PEP 440. Options `minimum-severity` and `max-age-days`. A reduced copy of each advisory database lives under `.ward/cache/vulnerabilities/`. `ward check --offline` makes no network request, and `ward vulnerabilities update` refreshes the databases (#519).
- Native `license` gate for Maven, Gradle, Node, Python and Swift projects: it checks the licenses of the resolved dependencies against a deny or allow policy in `[checks.license]` (`deny`, `allow`, `exceptions`, `scopes`, `unknown`), reading only local metadata. `ward sbom` writes a CycloneDX 1.6 JSON bill of materials and `ward licenses` the third-party license notice (#520).
- `ward licenses` bundles the full text of the Eclipse Public License 2.0, so a notice with EPL-2.0 dependencies carries the license itself. Ward's own `THIRD-PARTY-LICENSES` now includes that text for the Eclipse JDT libraries it ships.
- The `build-policy` check (`uses = "build-policy"`) runs the native `ward:build/*` rules over the build files of Maven, Gradle, Node and Python projects without starting a process: `plugin-version-pinned`, `no-dynamic-version`, `minimum-build-tool-version`, `duplicate-dependency-declaration`, `dependency-convergence` and `require-upper-bound`. The last two read the versions that Maven requested before it chose one, with the defaults of Maven Enforcer's `dependencyConvergence` and `requireUpperBoundDeps` (every scope, the Maven version order), and `EnforcerParitySmokeTest` runs the real plugin over their fixtures. `ward init --preset strict` selects both for Maven projects. See [Build policy rules](website/src/content/docs/reference/build-policy-rules.mdx) (#521).
- Native OpenAPI breaking-change rules `ward:openapi/<check id>`, one for each error-level check of oasdiff 1.37.0 that Ward can evaluate (346 of 359; the others are listed as not applicable in the rule catalog with their reason). Each has a base and revision fixture whose result a real oasdiff run confirmed; the opt-in `OasdiffParitySmokeTest` and the `oasdiff-parity` CI job keep them in step (#523).
- Ward's own security rules, `ward:<language>/<weakness>` in the category `security`, so a project needs no external source scanner: 66 rules written from the MITRE CWE and the JDK, Node.js, Python and framework documentation. Java has 13 taint rules (`cross-site-scripting`, `untrusted-redirect`, `server-side-request-forgery`, `ldap-injection`, `xpath-injection`, `log-forging`, `header-splitting`, `untrusted-deserialization`, `script-injection`, `expression-injection`, `jndi-injection`, `unsafe-reflection`, `regex-injection`) and 17 declarative rules (weak hash, cipher, ECB mode, RSA padding and key size, insecure random, hostname and certificate checks, TLS protocol, clear-text transport, JWT verification, hard-coded password, XML entities, deserialization settings, CSRF, CORS, cookie flags). JavaScript and TypeScript have 10 taint rules and 18 declarative rules, and Python has 8 declarative rules. Rules that report injection, broken cryptography, certificate bypass, unsafe deserialization or hard-coded credentials are `error` in `standard` and `strict`; hardening rules are `warning` in `strict` only. Taint is intra-procedural, as for the existing injection rules. Every rule has a catalog row with its CWE ids and sources; see [security rules](https://github.com/RicardoRB/ward-ai/blob/main/website/src/content/docs/reference/security-rules.mdx).
- The existing `sql-injection`, `command-injection` and `path-injection` rules of Java, and `command-injection` of JavaScript and TypeScript, gain sinks: Hibernate `Session` and jOOQ `DSLContext` plain SQL, Commons Exec `CommandLine`, `PrintWriter`, `PrintStream` and `ZipFile` file names, `shelljs` and `execa`; and `sql-injection` is a new rule of JavaScript and TypeScript.
- `api-compat` is a native check for Java and Kotlin. It compares the public API of the main sources in the working tree with the same sources at a baseline Git revision, `base` or the latest release tag, and reports incompatible changes as `api-compat:<kind>` findings (#526). It runs no build, no external tool and no network access; `[checks.api-compat]` takes `base` and `include`. Init offers it to Java and Kotlin projects with a release tag and the generated CI workflow fetches full history and tags. Kotlin visibility (`internal`, `private`), modality (closed by default), `object`, `data class`, properties as accessors, top-level declarations, nullability and default arguments are part of the model; `nullability-narrowed` and `default-argument-removed` are new kinds. The documented limits of the Kotlin reader are in the gates reference.
- The taint rules follow calls between the functions of one file, to a depth of four calls (a function that returns a source value is a source for its callers, and a function whose parameter reaches a sink is a sink for that argument), and Java types imported with a wildcard (`java.io.*`, `java.sql.*`, `javax.servlet.http.*` and the other JDK and servlet packages Ward lists) resolve to their receiver types. The Java sources now include the environment, system properties, standard input, sockets, database results and files. On the Juliet Java 1.3 suite the injection rules find about 22% to 34% of the cases that were all missed before (see `benchmark/security-report.txt`); flows across classes, through fields and through the response writer are still missed. A taint specification qualifier that starts with `*` matches the end of the qualifier text.
- Security rules for the groups the Semgrep removal left uncovered. Java: `ward:java/identity-cipher` (`NullCipher`), `static-iv`, `constant-gcm-nonce`, `unauthenticated-cbc` (CBC with padding), `environment-injection` and `session-attribute-injection`. JavaScript and TypeScript (Express and Node `crypto`): `cookie-missing-secure`, `cookie-missing-http-only`, `cookie-missing-same-site`, `cookie-broad-scope`, `missing-frame-protection`, `unfinished-aead`, `weak-gcm-tag-length` and `weak-argon2-parameters`. Not provided: a cookie-expiry rule (a cookie with no expiry is a browser-session cookie, and the matching CWE is deprecated), a reused-IV rule that follows a constant held in a field, and AWS CDK rules (Infrastructure as Code, out of scope).

### Changed

- **Breaking (alpha, no migration):** the `zap` gate type is removed, the way `semgrep` is, and Ward offers no dynamic scanning (DAST). A `ward.toml` with `uses = "zap"` is a configuration error (exit `2`) that lists the supported types, and `ward init --gate zap` fails as an unknown gate. A check whose `command` runs `zap-baseline.py` or another ZAP script is a plain `command` check: Ward reports its exit status, extracts no findings, gives no install guidance and no longer requires a `-t` target. Also removed: the `<TARGET_URL>` placeholder, the init option, the install-catalog entry, the doctor probe and the command type inference. Static security rules of the `ward` gate cover cookie flags, CSRF, CORS, clear-text transport and certificate checks in code. The security page of the docs now lists the network requests Ward makes: the update check and the OSV download of `vulnerabilities`.
- **Breaking (alpha):** The `enforcer` gate is removed. `ward init` no longer offers it and writes no `maven-enforcer-plugin` entry, no `config/enforcer-rules.xml` and no Gradle equivalent (on Gradle the gate ran `gradle check`); `--gate enforcer` and `uses = "enforcer"` are rejected as an unknown gate, the `compliance` legacy identifier is no longer read, and a check whose command runs `enforcer:enforce` is a plain command check. Re-running init removes a `config/enforcer-rules.xml` that carries Ward's marker and keeps and reports one you edited; the plugin entry in `pom.xml` is yours to remove. Migration: replace `[checks.enforcer]` with `build-policy` and select `build.dependency-convergence` and `build.require-upper-bound` in `ward-rules.toml` (`ward init --preset strict` does it for Maven); see [moving from the `enforcer` gate](website/src/content/docs/reference/build-policy-rules.mdx).
- **Breaking (alpha, no migration):** the `semgrep` gate type is removed, the way `gitleaks` is. A `ward.toml` with `uses = "semgrep"` is a configuration error (exit `2`) that lists the supported types, and `ward init --gate semgrep` fails as an unknown gate. A check whose `command` runs Semgrep is a plain `command` check: Ward reports its exit status, extracts no findings and gives no install guidance. Delete the `uses` key to keep running it that way. Also removed: the Semgrep step of the generated CI workflow, its partial-check table, its install-catalog entry, its doctor probe and its command type inference. Use the security rules of the `ward` gate instead.
- **Breaking (alpha, no migration):** Ward removes the 131 rules that were written from rows of the Semgrep registry (59 for Java, 72 for JavaScript and TypeScript), because the registry rules are under the Semgrep Rules License, which does not allow distributing them. Their rule files, fixtures, taint specifications, catalog rows, the pinned id list and the Semgrep parity tests are deleted, and the ids leave the `ward-rules.toml` schema and the rule reference. A `ward-rules.toml` that names a removed id is invalid: `[select]` reports `unknown rule` and a `[rules.<id>]` table reports `unknown rule id` (exit `2`); remove the id. The `standard` and `strict` presets lose all 131. A check whose command runs Semgrep still works as a plain command check. Detection that is lost, and the rule of Ward's own security rules that now covers part of it:
  - Java, weak or broken cryptography (MD5, SHA-1, SHA-224, DES, 3DES, RC2, RC4, Blowfish key size, default AES, null cipher, CBC padding oracle, GCM nonce reuse, static initialization vector): covered in part by `ward:java/weak-hash`, `weak-cipher`, `weak-key-size` and `ecb-mode`; no replacement for the null cipher, the CBC padding oracle, GCM nonce reuse and the static initialization vector.
  - Java, RSA without padding: `ward:java/rsa-without-padding`.
  - Java, unencrypted sockets, plain-HTTP requests, insecure SMTP and weak SSL contexts: `ward:java/cleartext-transport` and `weak-tls-protocol`, narrower for sockets and SMTP.
  - Java, JWT `none` algorithm, decode without verify and hard-coded secrets: `ward:java/jwt-without-verification` and `hard-coded-password`.
  - Java, unsafe Jackson and JMS deserialization: `ward:java/untrusted-deserialization` and `unsafe-deserialization-settings`.
  - Java, XML external entities (`DocumentBuilderFactory`, `SAXParserFactory`, `TransformerFactory`, `XMLInputFactory`): `ward:java/xml-external-entities`.
  - Java, SQL injection variants (JDBC, Spring, JBoss) and operating-system command, environment and file-path injection from request data: the existing `sql-injection`, `command-injection` and `path-injection` rules; no replacement for environment and session injection.
  - Java, reflected HTML and direct response writing: `ward:java/cross-site-scripting`, narrower.
  - Java, open redirect, response splitting, LDAP, XPath and log forging: `ward:java/untrusted-redirect`, `header-splitting`, `ldap-injection`, `xpath-injection` and `log-forging`; no replacement for URL host and session taint.
  - Java, MD5 as a password hash: `ward:java/weak-hash`.
  - JavaScript and TypeScript, code injection (`eval`, `vm`, `vm2`, sandbox runners, Puppeteer, Phantom, Chrome remote interface, `child_process`, Deno `run`, string-built code): `ward:javascript/script-injection` and `shell-option`; the existing `command-injection`.
  - JavaScript and TypeScript, SQL injection for Knex, MySQL, `pg` and Sequelize and DynamoDB filters: `ward:javascript/sql-injection` and `nosql-injection` (and the `typescript` ids of the same names), without the library-specific sinks Ward cannot confirm; DynamoDB filters are covered in part by `nosql-injection`.
  - JavaScript and TypeScript, XML external entities (expat, xml2json, libxml): `ward:javascript/xml-external-entities`.
  - JavaScript and TypeScript, unsafe deserialization: `ward:javascript/untrusted-deserialization`.
  - JavaScript and TypeScript, HTML and XSS sinks (`res.send`, raw HTML, template rendering, React unsanitized properties and methods, `dangerouslySetInnerHTML` variants, Angular trust bypasses and disabled SCE): `ward:javascript/dom-xss`, `template-injection` and `unsafe-framework-trust`, narrower; the existing `xss` and `no-dangerously-set-inner-html` stay.
  - JavaScript and TypeScript, open redirects and SSRF: `ward:javascript/untrusted-redirect` and `server-side-request-forgery`.
  - JavaScript and TypeScript, path traversal for `sendFile` and `path.join`: the existing `path-traversal`, narrower; no replacement for directory listing.
  - JavaScript and TypeScript, Express cookie and session settings: `ward:javascript/insecure-cookie`; no replacement for cookie name, domain, path and expiry checks.
  - JavaScript and TypeScript, JWT checks and hard-coded secrets (JWT, session, Passport): `ward:javascript/jwt-without-verification` and `hard-coded-secret`.
  - JavaScript and TypeScript, CORS misconfiguration and wildcard `postMessage`: `ward:javascript/permissive-cors` and `wildcard-post-message`; no replacement for `X-Frame-Options`.
  - JavaScript and TypeScript, TLS verification bypass and plain-HTTP requests: `ward:javascript/tls-verification-disabled` and `cleartext-transport`.
  - JavaScript and TypeScript, cipher IV and tag settings: `ward:javascript/static-iv`; no replacement for the missing `final` of an AEAD cipher or the GCM tag length.
  - JavaScript and TypeScript, unsafe Argon2 configuration, AWS CDK bucket, queue and CodeBuild settings, Intercom user hash: no replacement.
  - Removed Java ids: `blowfish-insufficient-key-size`, `cbc-padding-oracle`, `crlf-injection-logs`, `des-is-deprecated`, `desede-is-deprecated`, `documentbuilderfactory-disallow-doctype-decl-false`, `documentbuilderfactory-disallow-doctype-decl-missing`, `documentbuilderfactory-external-general-entities-true`, `documentbuilderfactory-external-parameter-entities-true`, `ecb-cipher`, `find-sql-string-concatenation`, `formatted-sql-string`, `gcm-nonce-reuse`, `http-response-splitting`, `httpget-http-request`, `httpservlet-path-traversal`, `insecure-jms-deserialization`, `insecure-smtp-connection`, `jackson-unsafe-deserialization`, `java-jwt-decode-without-verify`, `java-jwt-hardcoded-secret`, `java-jwt-none-alg`, `jax-rs-path-traversal`, `md5-used-as-password`, `no-direct-response-writer`, `no-null-cipher`, `no-static-initialization-vector`, `rsa-no-padding`, `saxparserfactory-disallow-doctype-decl-missing`, `servletresponse-writer-xss`, `spring-sqli`, `spring-unvalidated-redirect`, `tainted-cmd-from-http-request`, `tainted-env-from-http-request`, `tainted-file-path`, `tainted-html-string`, `tainted-ldapi-from-http-request`, `tainted-session-from-http-request`, `tainted-sql-from-http-request`, `tainted-sql-string`, `tainted-sqli`, `tainted-system-command`, `tainted-url-host`, `tainted-xpath-from-http-request`, `transformerfactory-dtds-not-disabled`, `unencrypted-socket`, `unvalidated-redirect`, `use-of-aes-ecb`, `use-of-blowfish`, `use-of-default-aes`, `use-of-md5`, `use-of-md5-digest-utils`, `use-of-rc2`, `use-of-rc4`, `use-of-sha1`, `use-of-sha224`, `use-of-weak-rsa-key`, `weak-ssl-context`, `xmlinputfactory-possible-xxe`.
  - Removed JavaScript and TypeScript ids: `aead-no-final`, `angular-bypasssecuritytrust`, `aws-cdk-bucket-enforcessl`, `awscdk-bucket-encryption`, `awscdk-bucket-grantpublicaccessmethod`, `awscdk-codebuild-project-public`, `awscdk-sqs-unencryptedqueue`, `bypass-tls-verification`, `chrome-remote-interface-compilescript-injection`, `code-string-concat`, `cors-misconfiguration`, `create-de-cipher-no-iv`, `deno-dangerous-run`, `detect-angular-element-taint`, `detect-angular-sce-disabled`, `detect-angular-trust-as-method`, `detect-child-process`, `detect-eval-with-expression`, `direct-response-write`, `dynamodb-request-object`, `express-check-directory-listing`, `express-cookie-session-default-name`, `express-cookie-session-no-domain`, `express-cookie-session-no-expires`, `express-cookie-session-no-httponly`, `express-cookie-session-no-path`, `express-cookie-session-no-secure`, `express-expat-xxe`, `express-insecure-template-usage`, `express-jwt-hardcoded-secret`, `express-jwt-not-revoked`, `express-libxml-noent`, `express-open-redirect`, `express-path-join-resolve-traversal`, `express-phantom-injection`, `express-puppeteer-injection`, `express-res-sendfile`, `express-sandbox-code-injection`, `express-sequelize-injection`, `express-session-hardcoded-secret`, `express-ssrf`, `express-third-party-object-deserialization`, `express-vm-injection`, `express-vm2-injection`, `express-xml2json-xxe`, `express-xml2json-xxe-event`, `gcm-no-tag-length`, `hardcoded-jwt-secret`, `hardcoded-passport-secret`, `intercom-settings-user-identifier-without-user-hash`, `js-open-redirect`, `jwt-none-alg`, `jwt-simple-noverify`, `knex-sqli`, `mysql-sqli`, `node-knex-sqli`, `pg-sqli`, `raw-html-concat`, `raw-html-format`, `react-dangerouslysetinnerhtml`, `react-insecure-request`, `react-unsanitized-method`, `react-unsanitized-property`, `require-request`, `res-render-injection`, `sequelize-sqli`, `tainted-html-response`, `tainted-html-string`, `unsafe-argon2-config`, `vm-runincontext-injection`, `wildcard-postmessage-configuration`, `x-frame-options-misconfiguration`.
- **Breaking (alpha):** `openapi-lint` runs these rules and no longer runs the vacuum binary. It takes no `command`: a `command` on it is a configuration error (exit `2`), so remove it from `ward.toml`. `vacuum.conf.yaml` is no longer read or refused, and init, the doctor, the install catalog and the generated CI workflow no longer mention vacuum. A Swagger 2.0 or OpenAPI 3.2 document, a syntax error or an unreadable `$ref` now ends the gate with an execution error.
- **Breaking:** the `gitleaks` gate is removed, with no migration flag. `uses = "gitleaks"` is rejected, a check that runs a `gitleaks` command is a plain `command` check with no findings, and `ward init --gate gitleaks` is an unknown gate: use `--gate secrets`. Init no longer offers or installs Gitleaks, `ward doctor` no longer probes for it, and the generated CI workflow no longer downloads it. The native check scans the working tree; the history is scanned only with `scan-history = true`.
- The coverage minimum lives in `ward.toml` and Ward enforces it for Maven, Gradle, Node and Python. A `coverage` check takes `minimum` (lines, required), `minimum-branches` and `report`, reads the JaCoCo XML or lcov report itself and fails below either minimum; it runs its `command` when it has one and only reads the report when it has none (Node, after the test gate). Branch coverage is read from JaCoCo XML and lcov. A missing, unparseable, truncated, stale or branchless report is an execution error, never a pass. Init writes no minimum into `pom.xml`, the Gradle build, a Vitest or Jest config, `bunfig.toml` or `.coveragerc`, and removes the Ward-written `jest.config.mjs` and `bunfig.toml` of earlier versions (with the marker) and rewrites a Ward-written `vitest.config.mjs` without its threshold. Node presets gain a `coverage` check after the test gate (not for Angular `ng-test`); Python's `coverage` command is now `coverage lcov -o .ward/reports/coverage.lcov`.
- **Breaking (alpha, no migration):** a `coverage` check without `minimum` is a configuration error, and a command that only contains `jacoco:check`, `jacocoTestCoverageVerification` or `coverage report` is a plain `command` check, no longer `coverage`; the generated commands are `jacoco:report`, `jacocoTestReport` and `coverage lcov`.
- **Breaking (alpha, no migration):** Python `strict` measures 85% of lines and 75% of branches separately, not coverage.py's combined 85% total, so a project at 85% combined can fail the branch minimum.
- **Breaking (alpha, no migration):** a multi-module Maven or Gradle build is checked as one total over every module's report, not per module.
- **Breaking (alpha, no migration):** the `trivy`, `dependency-check` and `pip-audit` gates are removed, with their init offers, the Trivy install and cache steps of the generated CI workflow, the `dependency-check` plugins of the Maven and Gradle tool configuration, the doctor checks of `NVD_API_KEY` and OSS Index, and their pages in the documentation. A check that runs one of those tools is now a plain `command` check; `uses = "trivy"`, `"dependency-check"` or `"pip-audit"` is a configuration error. Use `vulnerabilities` instead (#519).
- **Breaking (alpha, no migration):** the `license` gate no longer runs a build plugin. A `license` check that sets `command` is a configuration error, a license Ward cannot find or recognize now fails the gate (`unknown = "warn"` only warns), and init no longer writes `license-maven-plugin`, `com.github.jk1.dependency-license-report`, `licenseMerges` or `licenseReport` (#520).
- **Breaking (alpha, no migration):** `openapi-diff` and `openapi-compat` compare the spec with Ward's own rules instead of running `oasdiff`. A `command` on either gate is now a configuration error (exit `2`); remove it. Init no longer offers or installs oasdiff, `ward doctor` no longer looks for it, and `.oasdiff.*` files and `OASDIFF_CONFIG` are ignored. A spec, reference or Git read failure at either side of the comparison is an execution error (#523).

### Removed

- **Breaking (alpha):** `ward init` no longer adds the japicmp Maven plugin or the `me.champeau.gradle.japicmp` Gradle plugin, and `api-compat` no longer depends on `<distributionManagement>` or a published artifact: it compares sources with the latest release tag for Java and Kotlin (#526). Existing build files that already declare japicmp are left alone.
- **Breaking (alpha, no migration):** The OpenSpec integration. Ward no longer ships the `openspec`, `openspec-archived` and `openspec-junit-traceability` check types, the `root` setting of a check, the `ward spec` commands (`validate`, `guard`, `enforce`), `--exclude-spec` of `ward check` and `ward hook claude-code`, `--tool` and `--openspec-validation` of `ward init`, the OpenSpec installer and its step in the generated CI workflow, the OpenSpec checks of `ward doctor`, and the approved-artifact guard of every agent integration with its `PreToolUse` registrations. A `ward.toml` that still names a removed check type is rejected with the unknown-type error that lists the valid types. `ward integrate <agent>` and `--remove` delete the Ward-owned guard files that earlier versions wrote. To keep approved files from agent edits, use the agent's own permission rules or repository protection such as CODEOWNERS.

## [0.7.1] - 2026-11-12

### Fixed

- A `break` or `continue` outside a loop, or a jump to a label that does not exist, no longer aborts the `ward` gate with `unexpected error`: the jump leaves the body like a `return`, and the other files and rules still run (#530).

## [0.7.0] - 2026-11-11

### Added

- Five native Java rules that this repository's Checkstyle and PMD configuration enforces and Ward lacked: `ward:java/simplify-boolean-expression` (`x == true`, `x && true`, `c ? true : false`), `ward:java/unnecessary-parentheses` (precedence-clarifying parentheses are not reported), `ward:java/unused-private-field`, `ward:java/final-parameters` (a parameter the body uses and never assigns) and `ward:java/avoid-no-argument-super-constructor-call`. `ward:java/unnecessary-constructor` is now its own rule (#491).
- Thirteen native Python rules from the optional Pylint extensions, in the `strict` preset: `while-used`, `bad-builtin`, `bad-dunder-name`, `consider-math-not-float`, `consider-using-tuple`, `prefer-typing-namedtuple`, `consider-refactoring-into-while-condition`, `consider-ternary-expression`, `too-many-try-statements` (option `max-try-statements`, default 1), `overlapping-except`, `consider-using-assignment-expr`, `confusing-consecutive-elif` and `eq-without-hash`. Fourteen more Pylint checks are covered by existing rules (#336).
- Option `min-length` for the repeated string literal rules. `ward:java/multiple-string-literals` sets it to 3, so literals shorter than three characters are ignored, as PMD's `AvoidDuplicateLiterals` does; the default 0 keeps the other languages unchanged (#491).

### Changed

- **Breaking (alpha, no migration):** Rule categories are named after what a rule checks, not after the tool it came from. The 46 categories become 16: `accessibility`, `angular`, `architecture`, `complexity`, `concurrency`, `correctness`, `dead-code`, `docs`, `duplication`, `error-handling`, `experimental`, `naming`, `null-safety`, `performance`, `security` and `style`. For example `errorprone`, `bad-practice`, `problem` and `pylint` are now `correctness`; `sizes`, `metrics` and `design` are `complexity`; `javadoc` is `docs`; `mt-correctness` and `multithreading` are `concurrency`; `nullness` is `null-safety`; `a11y` is `accessibility`. Rule ids do not change. Update `categories` in `ward-rules.toml` and `ward rules list --category`; an old name is reported as an unknown category with the list of valid ones.
- The `ward` gate parses each source unit once per run. Type facts for Java, Node and Swift share the gate's parser, and the parse cache is bounded by source size instead of 64 entries. Measured on a 98-file TypeScript project: 457 parses become 98 and parse time drops from 209 s to 69 s, with an identical report (#502).
- The `ward` gate needs much less memory on large Java and Kotlin projects: the members of a class that every file shares are read once, not once per typed expression. On this repository the peak went from 9.0 GB to 3.3 GB with an identical report (#493). The members of a named TypeScript type are cached per file in the same way (#494).

### Fixed

- The `ward` gate no longer crashes with a `NullPointerException` on a JavaScript class that declares a `get` or `set` accessor. A getter is now a property of unknown type for the type-aware rules, and a setter declares nothing, as for TypeScript classes.
- Eleven Java rules reported code that the tool they mirror accepts. They now follow that tool's documented behaviour (#491):
  - `ward:java/avoid-instantiating-objects-in-loops` follows PMD's exemptions, such as `throw new` inside a loop.
  - `ward:java/avoid-literals-in-if-condition` reports only literals that are direct operands of the condition, and ignores `-1` and `0` by default.
  - `ward:java/unused-private-method` counts method references such as `Type::method`, and PMD's exemptions.
  - `ward:java/immutable-field` reports a field only when every write is in its initializer or a constructor, and leaves injected annotated fields alone.
  - `ward:java/magic-number` waives literals that initialize a `final` variable, as Checkstyle does.
  - `ward:java/public-member-in-non-public-type` follows the effective visibility of the member: interface members, `@Override` methods and `main` are not reported, and the finding is on the `public` modifier.
  - `ward:java/signature-declare-throws-exception` reports only an exact `Exception`, and not tests or overriding methods.
  - `ward:java/avoid-deeply-nested-if-stmts` no longer counts each `else if` of a chain as another nesting level; only a plain `else` block adds one, as in PMD.
  - `ward:java/confusing-argument-to-varargs-method` no longer reports a generic varargs method, whose erased parameter hides the inferred element type, or an array passed for a varargs type that an array cannot be an element of, such as `OpenOption...`.
  - `ward:java/declaration-order` now stands only for Checkstyle's `DeclarationOrder`; PMD's `FieldDeclarationsShouldBeAtStartOfClass`, which the same rule used to mirror, is the new rule `ward:java/field-declarations-should-be-at-start-of-class` (a field after a method, constructor or nested type; initializer blocks and nested enums may come first).
  - `ward:java/string-instantiation` reports only `new String(value)` with a `String` argument, as PMD does, and no longer a `String` built from bytes or characters.

## [0.6.0] - 2026-11-10

### Added

- Native TypeScript and JavaScript type resolution for the type-aware rules, with no `node` or TypeScript compiler process: types come from the project sources, the `.d.ts` files of the project and of the packages in `node_modules` (`types`, `typings`, `@types/*`) and the library of the installed `typescript` package, and what needs full checking is unknown. Package indexes are kept under `.ward/cache/types/node/`, named by the SHA-256 of the package. The TypeScript grammar now accepts ambient declarations (`declare function` and `declare class`, `declare global`, accessors and index signatures in declarations, labeled tuple elements and assertion signatures).
- Native Java rules `ward:java/no-field-di` and `ward:java/no-standard-streams` in the `strict` preset: a field annotated with `@Inject`, `@Autowired` or `@Resource` is reported, and so is any access to `System.out`, `System.err` or `System.in`. They replace the two checks that only the generated ArchUnit test enforced (#374).
- `ward-rules.toml`, an optional file that selects the rules of the `ward` gate (`[select]`: preset, languages, categories, include, exclude) and tunes them per rule (`max`, name patterns, `severity`, `exclude-paths`), validated with every problem reported by file, table and key and checked by `ward doctor`. `rules-file` in `[checks.ward]` points to another file. A `rules` list in `ward.toml` together with the file is an error. There are no suppressions of any kind (#280).
- `ward-frontend-antlr`, an internal analysis module with ANTLR parsers for Java, Kotlin, TypeScript, JavaScript, Python and Swift. Parsers are generated at build time from grammars pinned by commit, and syntax errors are returned as diagnostics next to a partial tree. No CLI behavior changes.
- `ward format [--check] [--format text|json] [<path>...]`, Ward's native formatter for the sources of Java, Kotlin, TypeScript, JavaScript, Python and Swift, in Ward style and with no external formatter. Each file is checked for syntax, tokens, syntax-tree shape and idempotence before it is written. It writes atomically and keeps file permissions; `--check` writes nothing and exits 1 when a file needs formatting. Settings come from `.editorconfig`. Symbolic links are reported and never rewritten.
- The `ward-format` quality gate (`uses = "ward-format"`, phase FAST), which reports one error per file that `ward format` would change, at its first differing line. Refused files and invalid `.editorconfig` settings are execution errors, never a pass. It supports partial runs and caches results by content and settings.
- `ward:architecture/cycles` and `ward:architecture/independence`, the slice cycle and feature independence rules of the `ward` gate, configured by `[checks.ward.cycles]` and `[[checks.ward.independence]]`. One meaning for Java, Python, TypeScript, JavaScript and Swift: a cycle is reported between two or more slices, the first directory under a source root (#246).

### Changed

- **Breaking (alpha, no migration):** Inside `ward-rules.toml` a rule is named `<language>.<rule-name>` only: `[rules.java.hidden-field]` for its table, and `"java.star-import"` in `[select]` `include` and `exclude`. A table named by the quoted rule id (`[rules."ward:java/hidden-field"]`) or a list entry written as the id (`"ward:java/star-import"`) is a configuration error that names the form to write. `ward init --preset strict` writes `[rules.java.hidden-field]`, `ward rules enable` and `disable` still take the printed id and write the dotted name, and the JSON schema lists only the dotted names. Rule ids are unchanged wherever Ward prints them (findings, JSON, SARIF, `ward rules`) and in the `rules` list of `ward.toml`.
- **Breaking (alpha, no migration):** `ward init` on a TypeScript or JavaScript project (npm, pnpm, Yarn and Bun, `standard` and `strict`, Angular, Turborepo members included) no longer generates ESLint (with typescript-eslint and angular-eslint), Biome's lint or Semgrep's `p/javascript` and `p/typescript` packs: no gate, no config file, no package entry, no script, no partial-run table, and no agent rule for them. It writes `ward-rules.toml` with the preset and the project's languages (`"typescript"`, `"javascript"`), so the `ward` gate applies Ward's rules instead. Ward's rules replace the standard rows of those tools; the Node compatibility matrix expects their findings from the `ward` gate (#343).
- **Breaking (alpha, no migration):** The `swiftlint` gate type is removed. A check with `uses = "swiftlint"` is rejected as an unknown gate type. A `swiftlint lint` command is a plain `command` check, with no structured findings and no learning. Init still removes the Ward-owned SwiftLint files that an earlier init wrote. Ward's own Swift rules (`ward:swift/*`) are unchanged.
- **Breaking (alpha, no migration):** `ward init` on a Swift project (SwiftPM and Xcode, `standard` and `strict`) no longer generates SwiftLint: no `swiftlint` gate, no `.swiftlint.yml`, no `.ward/swiftlint-strict.yml`, no `.ward/swift-tools/` package, no resolve or CI cache step, and no `.ward/swift-tools/.build/` ignore entry. The `ward` check applies Ward's Swift rules instead, and init writes `ward-rules.toml` with `languages = ["java", "swift"]`. Re-running init removes the Ward-owned SwiftLint files of an earlier init and keeps a `.swiftlint.yml` you wrote. Strict Swift runs no `--strict` SwiftLint (#345, #457).
- **Breaking (alpha, no migration):** `ward init` on a Java project (Maven and Gradle, `standard` and `strict`) no longer generates Checkstyle, PMD or SpotBugs: no gate, no rule file under `config/`, no build plugin or block, no partial-run table, and the agent rules no longer quote their limits. The `ward` check applies Ward's Java rules instead, and init writes an ownership-marked `ward-rules.toml` with `[select] preset = "<preset>"` and `languages = ["java"]`. The file is written for every project, so the Ward rules of Kotlin, TypeScript, JavaScript, Python and Swift stay off, and each of those keeps its external tools until its own migration lands (#342, #460). A Kotlin project still gets the `checkstyle`, `pmd` and `spotbugs` gates.
- **Breaking (alpha, no migration):** Ward no longer has a `ruff` gate type. A check with `uses = "ruff"` is rejected as an unknown gate type, and a `ruff check` command is a plain `command` check with no findings. Ruff, Pylint and vulture are removed from the docs as Ward gates; Ward's Python rules replace their lint checks.
- **Breaking (alpha, no migration):** `ward init` on a Python project (uv, Poetry or pip, `standard` and `strict`) no longer offers, selects, pins, configures or documents Ruff: the `ruff` gate is gone from both presets, and init writes no `ruff.toml`, no `ruff` package pin, no Ruff partial-run table and no Ruff agent rule. The `ward-rules.toml` that init writes lists `languages = ["java", "python"]`, so the `ward` check applies Ward's Python rules of the preset. A project's own `ruff.toml` is left unchanged. The `ruff` gate type stays until #452 (#344, #456).
- **Breaking (alpha, no migration):** Ward checks architecture with its own `ward:architecture/*` rules only. `ward init` always writes the `ward` layer, cycle and independence tables (`[[checks.ward.layers]]`, `[checks.ward.cycles]`, `[[checks.ward.independence]]`), and `--architecture-engine` is gone, with no `tool` engine or golden. Init no longer generates an ArchUnit test, a dependency-cruiser config, an Import Linter config or Harmonize rules, and no longer offers the `archunit`, `dependency-cruiser`, `import-linter` or `harmonize` gates; `uses = "archunit"`, `"dependency-cruiser"`, `"import-linter"` or `"harmonize"` is rejected. A `command` check that runs one of those tools still works as a custom check. The strict preset's ArchUnit coding rules are the native `ward:java/no-field-di` and `ward:java/no-standard-streams` rules (#374).
- **Breaking (alpha, no migration):** Ward formats with `ward-format` alone. `ward init` always selects the `ward-format` gate and writes an ownership-marked `.editorconfig` with Ward's defaults when the project has none. The external formatters are gone: the Spotless `format` gate (Maven and Gradle), `prettier`, `ruff-format` and `swift-format`, with their init generation, plugins, packages, config files, finding parsers and partial-run tables. Biome stays as the linter with its formatter off. `ward init` has no `--format-engine` option, and no `tool` engine or golden remains. A `ward.toml` that names one of the removed gates (`uses = "format"`, `"prettier"`, `"ruff-format"` or `"swift-format"`) is rejected with the list of supported types; a `command` check that runs the tool still works as a custom check.
- Ward's Kotlin rules replace detekt and ktlint. Ward never ran either tool, so nothing changes at run time; the docs no longer present them as tools Ward measures against, and a contract test keeps both out of the gates, finding sources, plugins and main classes (#450).
- `ward init --preset strict` writes the cycle and independence tables instead of ArchUnit's cycle rule, dependency-cruiser's `no-circular` and Import Linter's independence contract. Inside one slice, file cycles are not reported (#246, #374).
- The analysis API's `LanguageFrontend.parse` returns a `ParseResult` (syntax tree plus diagnostics) instead of a bare `SyntaxNode`. The analysis API is unreleased, so no released behavior changes.
- The Maven build is a parent project (`ward-parent`) with a `ward-cli` module. Build outputs moved to `ward-cli/target/` (`ward.jar`, `ward`, `ward.exe`); the released binaries and jar keep their names.

### Removed

- **BREAKING:** No macOS Intel (x86_64) binary is published, and `install.sh` stops on an Intel Mac with a message. Native builds use GraalVM Community 25.5, which has no macOS x64 release. On an Intel Mac, build `ward.jar` from source and run it on JDK 25.
- **BREAKING (alpha, no migration):** Ward no longer has the `eslint` and `biome` gate types, their finding sources, or the Semgrep `p/javascript` and `p/typescript` packs. A `ward.toml` that names `uses = "eslint"` or `uses = "biome"` is rejected with the list of supported types, and a `command` check that runs `eslint` or `biome` is a plain command check with no structured findings and no learning. The Semgrep gate stays for Python (#451, #455).
- **BREAKING:** Ward no longer wires Checkstyle, PMD, SpotBugs, Error Prone, NullAway or Semgrep's `p/java` pack. Their gates, finding sources, Maven and Gradle plugin declarations, preset rule files and init defaults are gone. Java and Kotlin projects use the `ward` gate and `ward-rules.toml`; Semgrep stays for TypeScript, JavaScript and Python, each with its own pack.

### Fixed

- Learning (`ward check --learn-after`) tracks the rule findings of the `ward` gate again, as documented. Since the external Java tools were removed it recorded nothing for Java, Kotlin and Swift projects.
- `ward init` on a Maven aggregator without its own sources places the architecture test in the module that owns them.

## [0.5.0] - 2026-11-02

### Added

- Swift support for SwiftPM and Xcode projects, with Swift quality gates and an Xcode version check.
- Android project detection and Android quality gates.
- Java quality gates placed in each module of multi-module builds, and in Kotlin module scripts for Gradle.

### Changed

- Feature code is split by toolchain across the application and infrastructure layers.

## [0.4.1] - 2026-10-31

### Added

- OpenAPI gate recommendations from the detected framework, with pinned tools installed by `init`.
- Turborepo support for other-language members, configured and checked by Ward.

### Changed

- Documentation positions Ward as the quality gate for AI coding agents.

## [0.4.0] - 2026-10-31

### Added

- Python support for `init`, gates, and frameworks, including Python detection and package managers.
- Turborepo workspaces: planning, member scripts, and `turbo.json` tasks written through `ward init`.

### Changed

- `ward.toml` moves to configuration version 2.

## [0.3.0] - 2026-10-29

### Added

- Free-named checks, tool types, and an edit profile.

### Changed

- **Breaking:** the configuration changes to free-named checks and tool types. Projects on the old
  format must migrate their `ward.toml`.

## [0.2.1] - 2026-10-29

### Added

- The `ward-fix` agent skill, which fixes failing gates in the code.
- Framework labels for Maven and Gradle projects, such as Spring Boot, Quarkus, Micronaut, and Helidon.
- Angular 21 and 22 support, and Vue and Svelte matrix cells.
- Explanations for gates that cannot run in the current environment.

### Changed

- Documentation covers TypeScript and JavaScript support.

## [0.2.0] - 2026-10-27

### Added

- TypeScript and JavaScript support: detection, `init`, and Node quality gates (ESLint, `tsc`, Stryker).
- Presets for TypeScript and JavaScript, including a `strict` preset.
- Angular, Vue, Svelte, and Astro setup, with each framework's own type checker.
- Biome support, and dependency-cruiser architecture rules for Node projects.
- A Node compatibility matrix, and a warning for Node versions below 22.
- A Java compatibility matrix, and Java tool versions chosen for the build's JDK.
- Junie CLI integration.

## [0.1.3] - 2026-10-24

### Added

- Windows native executable verification before release.

### Changed

- Mutation testing runs only on changed classes in pull requests. Native checks run on Windows.

### Fixed

- The license and gitleaks gates now catch what they claim to.

## [0.1.2] - 2026-10-24

### Added

- OpenAPI contract gates with vacuum and oasdiff.
- A PowerShell installer and Git Bash support for Windows.
- `init` support for Java projects nested in monorepos.

### Changed

- The website is easier to scan, and CI actions move to Node 24.

## [0.1.1] - 2026-10-22

### Changed

- **Breaking:** the project is renamed from Sentinel to Ward. Commands, configuration, and binaries use `ward`.

## [0.1.0] - 2026-10-21

### Added

- Generated GitHub CI workflow and `AGENTS.md` gate rules from `init`.
- Git quality hooks with JSON output.
- SARIF upload to GitHub code scanning.
- A notice when a newer release is available.
- Learning from recurring rule findings instead of gate failures.

### Fixed

- Compact JSON and SARIF reports from `check`.
- Java 25 compatible PIT configuration from `init`.

## [0.0.11] - 2026-10-19

### Added

- `check` runs only the gates for edited files after each agent edit.

### Fixed

- Partial checks match edited paths reached through symlinks.

## [0.0.10] - 2026-10-18

### Added

- The Sentinel agent skills plugin, with `sentinel-init` and `sentinel-tune`.
- Severity filtering for `check` output.
- Improved gate setup in `init`.

### Fixed

- Files generated by `init` work on real Maven and Gradle projects.

## [0.0.9] - 2026-10-17

### Added

- An exact change plan that `init` shows before it writes, with `--dry-run`.
- Reconfigure pre-fill, availability checks, and progress in the init wizard.

### Fixed

- Optional init questions are asked on a terminal, and unticked preset gates are honored.
- Maven tools are placed correctly, and `pom.xml` is validated before writing.

## [0.0.8] - 2026-10-17

### Added

- Verification of all agent harness integrations, with repaired adapters.
- Native-image compatibility checks in pull requests and releases.

### Fixed

- The build version is reported from native images.

## [0.0.7] - 2026-10-16

### Fixed

- Finding JSON codecs are initialized at build time for the native image.

## [0.0.6] - 2026-10-16

### Added

- Structured findings and SARIF output for `check`.
- Multi-agent harness integrations and OpenCode V2 integration.
- Gradle build support.
- Project-aware quality gate version compatibility.
- `init` quality profiles and Git hooks.
- Apache-2.0 license, with notices shipped in releases.
- A CI provider integration guide.

### Fixed

- The command reference link in the docs.

## [0.0.5] - 2026-10-14

### Added

- OpenSpec validation loop for agent integrations, and the OpenSpec JUnit traceability gate.
- OpenSpec change provenance enforcement, and guards for approved OpenSpec artifacts.
- OpenSpec validation tool integration.

## [0.0.4] - 2026-10-10

### Added

- Invocation-scoped verbose diagnostics.

### Changed

- Comprehensive PMD categories are enabled, and style findings are fixed.

## [0.0.3] - 2026-10-10

### Fixed

- The release publish job.

## [0.0.2] - 2026-10-10

### Added

- A release install script.

### Changed

- The README is rewritten, and docs are checked against the CLI.

### Fixed

- The retired macOS runner is replaced.

## [0.0.1] - 2026-10-08

Initial release, published as `sentinel-ai`.

### Added

- Quality gates for Java projects: format, Checkstyle, PMD, SpotBugs, JaCoCo coverage, PIT mutation
  testing, ArchUnit, Semgrep, gitleaks, Trivy, Enforcer, license, and API compatibility.
- Quality presets, named profiles, and a live quality check report.
- Agent hooks and an OpenCode plugin that run quality checks after agent edits.
- `init` with interactive setup, and native binaries published in releases.
- A bounded quality loop and a typed gate registry.
- Starlight documentation site.

[0.8.0]: https://github.com/RicardoRB/ward-ai/compare/v0.7.1...v0.8.0
[0.7.1]: https://github.com/RicardoRB/ward-ai/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/RicardoRB/ward-ai/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/RicardoRB/ward-ai/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/RicardoRB/ward-ai/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/RicardoRB/ward-ai/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/RicardoRB/ward-ai/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/RicardoRB/ward-ai/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/RicardoRB/ward-ai/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/RicardoRB/ward-ai/compare/v0.1.3...v0.2.0
[0.1.3]: https://github.com/RicardoRB/ward-ai/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/RicardoRB/ward-ai/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/RicardoRB/ward-ai/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/RicardoRB/ward-ai/compare/v0.0.11...v0.1.0
[0.0.11]: https://github.com/RicardoRB/ward-ai/compare/v0.0.10...v0.0.11
[0.0.10]: https://github.com/RicardoRB/ward-ai/compare/v0.0.9...v0.0.10
[0.0.9]: https://github.com/RicardoRB/ward-ai/compare/v0.0.8...v0.0.9
[0.0.8]: https://github.com/RicardoRB/ward-ai/compare/v0.0.7...v0.0.8
[0.0.7]: https://github.com/RicardoRB/ward-ai/compare/v0.0.6...v0.0.7
[0.0.6]: https://github.com/RicardoRB/ward-ai/compare/v0.0.5...v0.0.6
[0.0.5]: https://github.com/RicardoRB/ward-ai/compare/v0.0.4...v0.0.5
[0.0.4]: https://github.com/RicardoRB/ward-ai/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/RicardoRB/ward-ai/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/RicardoRB/ward-ai/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/RicardoRB/ward-ai/releases/tag/v0.0.1
