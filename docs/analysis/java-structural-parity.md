# Java structural rules: parity with Checkstyle and PMD

This report compares the thirteen `ward:java/*` rules with the Checkstyle and PMD checks they
mirror. Each tool ran on the same fixtures in `ward-cli/src/test/resources/analysis/java/`, using the
repository's configuration: `config/checkstyle.xml` (strict) for Checkstyle 10.20.2 through
`maven-checkstyle-plugin` 3.6.0, and `config/pmd-ruleset.xml` for PMD 7.17.0 through
`maven-pmd-plugin` 3.28.0. Ward ran through the `DeclarativeRuleMatcher` on the same files.

Checkstyle's `MagicNumber` and PMD's rules without a Ward counterpart are not listed.

## Results per rule

| Ward rule | Checkstyle | PMD | Ward (violating) | Ward (passing) | Result |
|---|---|---|---|---|---|
| `file-length` | `FileLength`: 505 lines | none | 505 lines | none | agree |
| `method-length` | `MethodLength`: 62 lines | `NcssCount`: 61 (a different metric) | 62 lines | none | agree with Checkstyle |
| `parameter-count` | `ParameterNumber`: 6 | none | 6 | none | agree |
| `cyclomatic-complexity` | not flagged (10) | `CyclomaticComplexity`: 10, not flagged | 11, flagged | none | **Ward differs** (see below) |
| `cognitive-complexity` | none | `CognitiveComplexity`: 17 | 17 | none | agree |
| `star-import` | `AvoidStarImport` | none | flagged | none | agree |
| `unused-import` | `UnusedImports` | `UnnecessaryImport` | flagged | none | agree |
| `missing-javadoc` | `MissingJavadocMethod` | none | flagged | none | agree |
| `type-name` | `TypeName` | `ClassNamingConventions` | flagged | none | agree |
| `method-name` | `MethodName` | `MethodNamingConventions` | flagged | none | agree |
| `member-name` | `MemberName` | `FieldNamingConventions` | flagged | none | agree |
| `parameter-name` | `ParameterName` | `FormalParameterNamingConventions` | flagged | none | agree |
| `local-variable-name` | `LocalVariableName` | `LocalVariableNamingConventions` | flagged | none | agree |

No tool flags a passing fixture for any of these rules.

## Differences

**`cyclomatic-complexity`: Ward counts a `do … while` loop twice.** The `while` that ends a
`do` statement is counted as a `while` loop as well as the `do`. Checkstyle counts it once, PMD
reports 10 for the fixture `Branchy.classify`, and so does the McCabe count. Ward reports 11, so a
method at the limit that contains a `do … while` is reported falsely. The rule counts terminal kinds
with no way to exclude a `while` that belongs to a `do`. Fixing it needs either a check parameter
or a parent-aware count; until then, this is a known false positive.

**`method-length` counts body lines.** The rule measures the `methodBody` node, from its opening
brace to its closing brace. Checkstyle counts the same lines, and gets 62 for the fixture. PMD's
`NcssCount` counts statements, so it reports 61 for the same method.

**`cognitive-complexity` uses a simplified form.** Each `&&` and `||` adds one, and `else` adds one
without nesting. The fixture scores 17 under both Ward and PMD.

**`missing-javadoc` covers classes, interfaces, enums, records, methods and constructors.** It does
not check interface methods, and it skips methods with an `@Override` annotation. Checkstyle's
`MissingJavadocMethod` with scope `public` gives the same result on the fixtures.

**`unused-import` is textual.** A name counts as used if it appears as a token or in a comment.
A name used only as a fully qualified reference counts as unused, and a name that matches another
identifier counts as used. Neither case is in the fixtures; both are limits of the rule.

**Java 25 syntax.** The Java 25 sample uses a guarded pattern in a `switch`. Checkstyle 10.20.2
cannot parse it (`14:36: mismatched input`), so Checkstyle reports nothing for the sample, and Ward
parses it without an execution error. This is a limit of Checkstyle's parser, not of the rules.

## Fixes found by this report

- The `file-length` rule counted the end-of-file line: a 505-line file was reported as 506 lines,
  so a file of exactly 500 lines would have failed. A rule that ends at the end of the file now ends
  on the last physical line. Covered by `RuleSpanTest`.

## Reproducing

Checkstyle and PMD ran through their Maven plugins on a copy of the fixture tree, with the
repository's configuration files. Ward's findings come from `DeclarativeRuleMatcher` over the same
files, using the `ClasspathRuleSource` rule set.
