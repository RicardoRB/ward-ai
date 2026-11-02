# Spike: native parser runtime

Decision note for [#185](https://github.com/RicardoRB/ward-ai/issues/185). Source change: `openspec/changes/spike-native-parser-runtime` (local, gitignored).


## Context

Roadmap #179 builds an analysis engine that runs inside the GraalVM native image. Phase 1 (#181) needs a parser for Java and for the other languages. Two candidates are open: Tree-sitter through `jtreesitter` (FFM, grammars as native libraries) and, for Java only, Eclipse JDT or javac. The image rules in `AGENTS.md` apply: no runtime class generation, no reflection-based loading, and every FFM downcall or upcall must be registered in `reachability-metadata.json`. `PosixRawTerminal` already uses FFM with `--enable-native-access=ALL-UNNAMED`.

This document is the decision note. Its measurement sections are filled during the spike; the planning artifacts only fix the questions and the method.

## Goals / Non-Goals

**Goals**
- Decide grammar packaging for Tree-sitter in the native image.
- Decide the Java L2 parser (JDT `ASTParser` or javac Tree API).
- Give Phase 1 a fallback if Tree-sitter cannot run in the image.

**Non-Goals**
- No production code, no `pom.xml` change on `main`.
- Kotlin Analysis API and TypeScript Compiler API are out of scope; both run out of process by design.

### Scratch `ward` build (4.2)

A scratch copy of the CLI, built natively from the spike branch with `jtreesitter` 0.26.1, the merged Tree-sitter reachability metadata, `-H:+SharedArenaSupport` and a hidden `--spike-parse` entry point, measured against the baseline `ward` (65,144,808 bytes, `--version` peak footprint 29,016,544 bytes):

| Build | Binary size | Delta vs baseline | `--version` peak footprint |
|---|---|---|---|
| Baseline `ward` | 65,144,808 bytes | 0 | 29,016,544 bytes |
| `ward` + `jtreesitter` (grammars loaded at run time) | 65,326,760 bytes | +181,952 bytes (0.18 MB) | 29,196,792 to 29,229,560 bytes (about +0.2 MB) |

The parser adds about 0.2 MB to the CLI. The Tree-sitter core and both grammars are loaded at run time, so they are not in the binary. Their cost sits beside the binary instead: `libtree-sitter` plus the Java and TypeScript grammar libraries, about 2.0 MB for the two grammars on macOS arm64.

A parse-only run of the same build (Java grammar, one file, before the Dagger graph loads) peaks at about 3.2 MB, so the parse itself is cheap; the cost is in the startup footprint above.

### Static versus shared grammars (2.4)

- **macOS arm64, static:** `libtree_sitter_java.a` and `libtree_sitter_typescript.a` were built with clang and force-loaded into the probe, which resolved the grammars through `dlsym(RTLD_DEFAULT)`. The binary is 18,388,848 bytes, against 16,481,656 bytes for the shared-grammar probe: +1.91 MB in the binary, with no grammar files beside it. Throughput was 67,000 to 70,000 files/s against about 57,000 files/s for shared, and peak RSS was 14.06 MB against 12.96 MB. Shared grammars cost about the same space, but on disk instead of in the binary.
- **Linux aarch64, static:** not achieved. The grammar archives built and the binary grew by about 2 MB, but the grammar entry points were not exported, and identical binary sizes across three linker-option variants suggest the options did not reach the link. Not resolved.


### Alternative: ANTLR (pure Java)

ANTLR 4.13.2 with the `grammars-v4` Java and TypeScript grammars (plus their base classes) was tested as a pure-Java alternative. It needs no FFM, no reachability metadata and no symbol lookup. It builds as a native image on macOS arm64 (14.9 MB) and Linux aarch64 (15.1 MB) and parses both samples.

Throughput on real JDK sources (`String.java`, `HashMap.java`, `Collectors.java`, `ConcurrentHashMap.java`, 16,000 lines in total, same four files for both):

| Parser | Iterations | Files/s | MB/s |
|---|---|---|---|
| Tree-sitter (native, reused parser) | 200 | 111.9 | 18.51 |
| ANTLR (native, new parser per file) | 3 | 8.5 | 1.41 |

On the two tiny samples, Tree-sitter ran at about 57,000 files/s and ANTLR at about 540 files/s, with ANTLR peaking at about 95 MB of memory. Caveats: ANTLR ran only three iterations on the large set, and it builds the full parse tree in Java while Tree-sitter's measured work is the C parse only. The grammars were tested only on these samples and on real JDK sources, not on a full project. The Kotlin grammar from `grammars-v4` was tested on its 17 example files: 15 parse with no errors, and the two that report errors are invalid Kotlin on purpose (`bad_property.kt`, and `fun_on_same_line.kt`, which puts two declarations on one line without a separator). Kotlin throughput was not measured. ANTLR does not parse incrementally, which matters for the incremental engine in #179.

## Decisions

**D1. Prototype outside `main`.** A throwaway module in a scratch directory parses one Java file and one TypeScript file with `jtreesitter`. It is discarded after measurement. Alternative considered: a branch in the repo. Rejected, because a branch invites accidental merge and the decision is a document, not code.

**D2. Measure on both macOS and Linux.** Native-image behavior differs by platform for FFM library loading. One platform proves nothing for CI.

**D3. Compare grammar packaging.** Static link into the image versus shared libraries beside the binary. The measurement records binary-size delta and whether the image starts and parses without a runtime library search path.

**D4. Java L2: JDT and javac both built.** Both are built in a native image with the same two Java files. The comparison records build success, reachability metadata needed, parse throughput, and RSS.

**D5. Baseline.** Current `target/ward` is 65 MB. All size and RSS deltas are reported against a fresh build of `main` on the same machine.

**D6. Fallback.** If Tree-sitter fails in the native image, the note names pure-Java parsers per language and stops the Tree-sitter path before Phase 1 starts.

## Measurement method

| Measure | How |
|---|---|
| Build success | `native-maven-plugin` build of the prototype with the same GraalVM 25 used by CI |
| Reachability | List every FFM downcall, upcall and reflective type the build needs |
| Throughput | Parse the same Java and TypeScript files 1000 times, record files per second |
| RSS | Peak resident set of one parse run |
| Binary size | Size of the prototype image minus the `main` baseline, both on the same machine |

## Risks / Trade-offs

- A native build may succeed while the first parse fails at run time. The measurement therefore parses in the built image, not only builds it.
- JDT is large and may dominate binary size. That is a cost to record, not a reason to reject it.
- Results depend on the GraalVM build. The note records the exact version.

## Open Questions

- Which grammar set does Phase 1 need first? Java and TypeScript are the spike inputs; the others are not measured here.
- Does the CI native job need a Linux grammar build step? The spike answers this if Linux parsing needs an extra library.

## Measurements

Environment: macOS arm64, GraalVM CE 25.0.2 (`native-image` 25.0.2), Temurin 25 for JVM runs, jtreesitter 0.26.1, tree-sitter core 0.27.1 (Homebrew `libtree-sitter.dylib`), grammars from `io.github.bonede:tree-sitter-java:0.23.5` and `tree-sitter-typescript:0.23.2` (`aarch64-macos` dylibs), JDT `org.eclipse.jdt.core:3.47.0`, javac from the JDK. Samples: one Java file and one TypeScript file, both a few lines long.

Baseline: fresh native `ward` built from `0.6.0` on the same machine (`./mvnw -Pnative -DskipTests native:compile`): **65,144,808 bytes**, `--version` peak footprint about 29 MB.

| Candidate | Native build | Parses in native image | Binary size | Max RSS (one parse) | Throughput (1000 parses) |
|---|---|---|---|---|---|
| Tree-sitter (`jtreesitter`, Java + TS grammars shared, beside binary) | yes, with reachability metadata and `-H:+SharedArenaSupport` | yes, Java and TypeScript | 16.4 MB (probe, not `ward`) | about 13 MB | 68k files/s (2000 parses, tiny samples; indicative only) |
| Eclipse JDT `ASTParser` with bindings | yes, with unsigned jars and reachability metadata | yes, method binding resolves | 32.4 MB (probe) | about 25 MB (one parse), 77 MB after 1000 parses | 8,151 files/s |
| javac Tree API (parse + attribute) | yes, with `java.home` at run time and resource-bundle metadata | yes, method symbol resolves | 34.6 MB (probe) | about 15 MB (one parse) | 80 files/s |

Size deltas against the 65 MB baseline are not meaningful for the probes: each probe contains only its own parser, not the `ward` CLI. They show each parser's own cost, not the cost added to `ward`.

### Tree-sitter reachability (2.5)

The native-image agent, run on the GraalVM JVM, recorded 11 FFM downcalls (`returnType` and `parameterTypes`), 42 resource globs and 26 reflection entries for the probe. Its merged metadata is the list Phase 1 must register under `META-INF/native-image/...`. Two build-time requirements apply beyond the metadata:
- `-H:+SharedArenaSupport` is required, because jtreesitter uses shared arenas.
- The grammar library is loaded with `SymbolLookup.libraryLookup` from a path at run time, so the binary needs the grammar file beside it (or at a known path).

### Linux (2.3) and grammar packaging (2.4)

Linux, done: the same Tree-sitter probe built as a native image in the `ghcr.io/graalvm/native-image-community:25` container (aarch64 Linux, GraalVM 25.0.2) and parsed both samples. The core library was `libtree-sitter.so` 0.26.6 from `io.github.bonede:tree-sitter:0.26.6`, and the grammars were the `aarch64-linux-gnu` shared objects from the same bonede grammar jars. The binary is 16.6 MB. One process run (startup plus one Java parse) took 10 to 12 ms after the first run, which was 50 ms. The Linux build needed the same reachability metadata and `-H:+SharedArenaSupport` as macOS.

Not done, 2.4: static grammar linking. It needs grammar object files or sources, which are not available offline, so only the shared-library option was measured on both platforms. The core library version differs between macOS (0.27.1, Homebrew) and Linux (0.26.6, bonede), so the two platforms are not an exact parity comparison. x86_64 Linux was not built.

### Java L2 constraints found

- **JDT** resolves bindings natively only with compliance set to Java 8. With compliance 9 or later, `ASTParser` requires a JDK system library on the classpath (`checkForSystemLibrary`), and a native image has none. With `includeRunningVMBootclasspath=true` it fails the same way. JDK types were not verified to resolve under compliance 1.8 natively. Signed Eclipse jars must be unsigned, because their certificates become image-heap constants that fail analysis.
- **javac** needs `java.home` set at run time and the `compiler` and `javac` resource bundles registered. It also needs a JDK on disk: `--release 25` reads `lib/ct.sym`, which is missing when `java.home` points to a nonexistent directory.

## Decision

**Tree-sitter runs in the native image on macOS.** No fallback is needed on macOS. Grammars are loaded as shared libraries beside the binary. Linux and static linking are not yet measured, so the Phase 1 plan must keep the pure-Java fallback named below until those two are measured.

**Java L2 parser: deferred. Phase 1 is syntax-only with ANTLR; bindings are left for a later change.** It needs no JDK on disk at run time, and a self-contained native binary cannot assume one. javac would need a JDK installed beside the binary, which breaks that assumption. The conditions:
1. Compliance is pinned to Java 8 in the probe. Phase 1 must confirm that Java 25 syntax (records, pattern matching, sealed types) produces the expected AST under that compliance, and that JDK types resolve without a system library.
2. Only the unsigned copies of the Eclipse jars are built natively, which Phase 1 must reproduce in its build.

If Phase 1 finds that JDT cannot resolve JDK types or parse Java 25 syntax under these constraints, the choice must be revisited in favor of javac with the JDK-on-disk constraint stated.

**Fallback per language (5.2):** not triggered on macOS, because Tree-sitter runs. If Linux or static linking fails in Phase 1, the fallback is pure-Java parsers: JDT for Java; for TypeScript, a pure-Java parser that Phase 1 must still select, since none is measured here.

**Phase 1 path (proposed).** Start with ANTLR as the first syntax-tree adapter, behind a parser port in the domain layer (port and adapter, as `AGENTS.md` requires). The analysis code depends only on the port, so the adapter can be replaced later without rewriting analysis. Switch to Tree-sitter when at least one of these holds: incremental parsing becomes a requirement; full-project analysis misses the time budget that Phase 1 sets; or the Linux static-linking and symbol-lookup limits are resolved, which would let Tree-sitter ship as a single binary. Until then, ANTLR is the default for Java, TypeScript and Kotlin. Decision: Phase 1 is syntax-only, so no JDK is required on disk. Bindings (JDT or javac) are deferred to a later change, which will decide the JDK-on-disk requirement at that point.

### JDT verification (run after the decision above)

A Java 25 sample (records, sealed interfaces, pattern `switch`, `var`, text blocks) was parsed with JDT on the JVM:

| Compliance | Runtime library | Problems | Result |
|---|---|---|---|
| 1.8 (used by the native probe) | none | 39 | Modern syntax is not parsed; records, sealed types and pattern switch fail |
| 25 | running JVM library | 0 | All types resolve (`String`, `Object`, `List`, the records) |

The Java 8 compliance workaround therefore fails condition 1. Compliance 25 is correct, but it needs a JDK library on disk at run time, which the self-contained native binary cannot assume. The native build with compliance 25 was not run; it already fails at `checkForSystemLibrary` without the JDK. Result: no JDT configuration is verified to satisfy both syntax and self-contained native deployment. JDT is not the Java L2 parser until this is resolved. Chosen option: limit Phase 1 to syntax with ANTLR and leave bindings for a later change.

**Fallback per language (5.2).** ANTLR with the `grammars-v4` grammars is the named pure-Java fallback for TypeScript, and the default for Java syntax (JDT stays for bindings). Kotlin is covered by the same grammar family.

## Open items

- Static grammar linking on Linux (2.4): paused, not pursued further. The archives build, but the grammar entry points are not exported from the binary, likely the same symbol-lookup limit as musl. macOS static linking is measured.
- JDK type resolution and Java 25 syntax under JDT compliance 1.8 (see decision condition 1).

