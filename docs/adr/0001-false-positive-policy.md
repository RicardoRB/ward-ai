# ADR 0001: How Ward keeps false positives down

- Status: accepted
- Date: 2026-11-23
- Issues: #724, #726, #759, #760, #762

## Context

Users of the `strict` preset keep reporting false positives, mostly in Java's taint rules (#704, #712, #759). The corpus tests did not catch them, for three reasons.

The first reason is coverage. The pinned corpora contain no code where those rules fire. In the Java corpus, `server-side-request-forgery` and `log-forging` report 0 findings, and about 500 of the 1,057 Java rules report nothing. A rule that never fires has no false-positive rate that anyone can measure.

The second reason is the size of the sample. The corpus tests label a sample of findings: the first 3 to 5 findings of each rule, in path order. In Java that is 755 labels for about 90,000 findings. Labelling more findings does not scale with the number of rules and projects.

The third reason is how a rule gets fixed. Each fix covered only the shape that was reported. #704 taught `server-side-request-forgery` to recognise an allow-list guard inside the method that holds the sink. The same guard in a called method, which is the usual shape in a layered project, is still reported (#759).

Other tools deal with this in other ways:

- **PMD** (`pmdtester`) and **Checkstyle** (`checkstyle-tester`) run the base version and the changed version of the tool over a list of large real projects on every pull request. A reviewer then reads only the findings that the change adds or removes. Nobody labels the full output.
- **SpotBugs** gives every finding a confidence. A detector lowers the confidence when it cannot prove the case, and by default the tool reports only medium confidence and above. The tool prefers to miss a bug rather than report a false one.
- **All three tools** turn each reported false positive into a permanent regression test.

## Decision

Ward keeps false positives down with four mechanisms, one for each way a false positive gets in.

1. **Every reported false positive becomes a permanent test.** The fix adds the reported shape to the `passing` fixture of the rule, and the `violating` fixture must still report. When a fix covers only one shape, it also checks the near variants: the same guard in a called method, in a helper, or behind a local.

2. **Corpus tests run `strict` and fail on an unlisted false positive.** Every language runs the `strict` preset plus every security rule over its pinned corpora. A finding labelled `fp` fails the test unless `benchmark/<corpus>-approximations.txt` lists it together with an open issue. These lists should only get shorter (#724).

3. **A differential corpus** (#760):
   - Each language has many pinned real projects, and at least one of them is a layered web application or service whose request input reaches URLs, logs, files, queries, redirects and templates.
   - Every change to a rule produces a report of the findings it adds and removes over those projects, and the change is not merged until each added finding has been reviewed.
   - Full labelling is kept for security rules only. Those rules are few and report few findings.

4. **Confidence for taint findings** (#762):
   - A taint flow that crosses something the engine assumed rather than proved gets `low` confidence. Examples: a call into project code that the engine does not follow, a guard it cannot evaluate, a type it cannot resolve, or a non-final collection read as an allow-list.
   - By default, presets report only `high` findings, and a rule option shows the `low` ones as well.
   - The differential report of mechanism 3 measures the change. No labelled true positive may be lost without a listed reason and an issue to make the engine follow that path.

The rule set is never made quieter by any of these means:

- suppressions, exclusions, or turning a rule off in a preset;
- weakening a security rule to make a corpus pass, such as treating a non-sanitizer as a sanitizer or exempting a whole class of sinks;
- options named after a tool. A behaviour that differs between tools becomes a separate Ward rule.

## Consequences

- Rules that never fire in the corpus get measured once the differential corpus adds projects in which they fire.
- The cost of review grows with the size of a change, not with the size of the corpus, so the project list can grow to dozens of projects per language.
- Every pull request that changes a rule states its differential report. The orchestrator of a release reviews that report before merging.
- Mechanism 4 can hide a true positive whose flow the engine does not yet follow. That is a deliberate trade, and the issue opened for each lost true positive records it.
- The corpus projects are only measured. None of their code, text or tests goes into a rule, fixture or document, as AGENTS.md requires.

## Alternatives considered

- **Label more findings per rule.** This does not scale (90,000 findings in Java), and it still says nothing about rules that never fire.
- **Rely on parity with the original tools (#725).** This helps to find cases where Ward reports and the original tool does not. It cannot tell whether either of them is right, and some Ward rules have no counterpart, so it supports the other mechanisms and does not replace them.
- **Fix only the reported shape.** This is what happened before, and it is why #759 followed #704.
