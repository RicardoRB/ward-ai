# Accuracy corpora

`ward-analysis-bench --accuracy <dir>` measures how often Ward's rules report real vulnerabilities.
It runs `ward check --format json` on each corpus and counts, per rule, true positives, false
positives, false negatives, precision and recall. The results are reported as measured; they are
not used to tune the rules.

## Checked-in corpora

Each subdirectory that holds a `labels.csv` is a corpus. Its labels name files relative to the
subdirectory:

```csv
file,rule,vulnerable
src/main/java/accuracy/sql/SqlServletGetParameter.java,ward:java/sql-injection,true
src/main/java/accuracy/sql/SqlParameterizedQuery.java,ward:java/sql-injection,false
```

- `vulnerable` is `true` or `false`.
- A file with a finding of a rule and no `true` label for that rule is a false positive.
- A `true` label with no finding is a false negative.
- Ward reads each corpus as a project, so each corpus holds a build file such as a `pom.xml`.

## External corpora

`external.csv` lists corpora fetched at run time, each pinned by its full commit SHA:

```csv
name,url,commit,labels,format,root
owasp-benchmark,https://github.com/OWASP-Benchmark/BenchmarkJava.git,<sha>,expectedresults-1.2beta.csv,owasp-benchmark,src/main/java/org/owasp/benchmark/testcode
```

The external repository is fetched into the cache and is never checked in or shipped. OWASP
Benchmark is GPL-licensed; it is not a dependency of Ward, so the license gate does not apply.
A commit that cannot be fetched fails the run; no other revision is used.

Ward's JSON lists at most 50 findings per check, so each corpus is split into shards of at most 40
source files, and a truncated shard fails the run.
