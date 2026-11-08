package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class NullDataflowCheckTest {

  private static List<String> run(final NullDataflowCheck.Mode mode, final String body) {
    return new ParsedFacts("class C {\n" + body + "}\n")
        .run(new NullDataflowCheck(mode, "message"));
  }

  private static List<String> run(
      final NullDataflowCheck.Mode mode,
      final String body,
      final String call,
      final SpanFacts facts) {
    return new ParsedFacts("class C {\n" + body + "}\n")
        .at("expression", call, facts)
        .run(new NullDataflowCheck(mode, "message"));
  }

  private static SpanFacts target(
      final String owner, final String name, final int params, final String... annotated) {
    final List<TypeRef> types = java.util.Collections.nCopies(params, ParsedFacts.OBJECT);
    return new SpanFacts(
        null,
        null,
        new CallTarget(
            TypeRef.of(owner),
            name,
            types,
            null,
            false,
            false,
            List.of(),
            java.util.Collections.nCopies(params, List.of(annotated)),
            List.of(),
            List.of(),
            List.of(),
            List.of()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  @Test
  void aVariableThatIsNullWhereItIsDereferencedIsReported() {
    final String body = "  void t() {\n    String s = null;\n    s.length();\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.ALWAYS_NULL, body)).containsExactly("4:5");
    assertThat(
            run(
                NullDataflowCheck.Mode.ALWAYS_NULL,
                body.replace("s.length();", "s = \"x\";\n    s.length();")))
        .isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.ALWAYS_NULL,
                body.replace("s.length();", "if (s == null) {\n      s.length();\n    }")))
        .containsExactly("5:7");
    assertThat(
            run(
                NullDataflowCheck.Mode.ALWAYS_NULL,
                body.replace("s.length();", "if (s != null) {\n      s.length();\n    }")))
        .isEmpty();
    assertThat(
            run(NullDataflowCheck.Mode.ALWAYS_NULL, body.replace("String s = null;", "String s;")))
        .isEmpty();
  }

  @Test
  void closeOnANullVariableIsAClosingNullNotAnAlwaysNull() {
    final String body =
        "  void t() throws Exception {\n    java.io.Closeable c = null;\n    c.close();\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.CLOSING_NULL, body)).containsExactly("4:5");
    assertThat(run(NullDataflowCheck.Mode.ALWAYS_NULL, body)).isEmpty();
  }

  @Test
  void aNullOnlyAnExceptionPathHoldsIsAnExceptionPattern() {
    final String body =
        "  void t() {\n    String s = null;\n    try {\n      s = read();\n    } catch (RuntimeException e) {\n"
            + "      s.length();\n    }\n  }\n  String read() { return \"\"; }\n";
    assertThat(run(NullDataflowCheck.Mode.ALWAYS_NULL_EXCEPTION, body)).containsExactly("7:7");
    assertThat(run(NullDataflowCheck.Mode.ALWAYS_NULL, body)).isEmpty();
  }

  @Test
  void aParameterMarkedPossiblyNullIsReportedUntilItIsChecked() {
    final String body = "  void t(@CheckForNull String s) {\n    s.length();\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.ARGUMENT_MIGHT_BE_NULL, body)).containsExactly("3:5");
    assertThat(
            run(
                NullDataflowCheck.Mode.ARGUMENT_MIGHT_BE_NULL,
                body.replace("s.length();", "if (s != null) {\n      s.length();\n    }")))
        .isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.ARGUMENT_MIGHT_BE_NULL,
                body.replace("@CheckForNull", "@Nullable")))
        .isEmpty();
  }

  @Test
  void aNullComparedAfterTheValueWasDereferencedIsReported() {
    final String body =
        "  void t(String s) {\n    s.length();\n    if (s == null) {\n      return;\n    }\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.WOULD_HAVE_BEEN_NPE, body)).containsExactly("4:9");
    assertThat(
            run(
                NullDataflowCheck.Mode.WOULD_HAVE_BEEN_NPE,
                body.replace("s.length();", "s = other();")))
        .isEmpty();
  }

  @Test
  void aNullThatIsCertainToBeDereferencedAfterAMergeIsReported() {
    final String body =
        "  void t(boolean b) {\n    String s = null;\n    if (b) {\n      s = \"x\";\n    }\n    s.length();\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH, body)).containsExactly("7:5");
    assertThat(run(NullDataflowCheck.Mode.GUARANTEED_DEREF, body)).isEmpty();
    final String test =
        "  void t(String s, boolean b) {\n    if (s == null) {\n      b = false;\n    }\n    s.length();\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH, test)).containsExactly("6:5");
    final String two =
        "  void t(String s, boolean b) {\n    if (s == null) {\n      b = false;\n    }\n"
            + "    if (b) {\n      s.length();\n    } else {\n      s.trim();\n    }\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.GUARANTEED_DEREF, two)).containsExactly("7:7");
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH, two)).isEmpty();
  }

  @Test
  void aNullThatSomePathLeavesUnusedOrReturnsFromIsNotReported() {
    final String body =
        "  void t(String s, boolean b) {\n    if (s == null) {\n      return;\n    }\n    s.length();\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH, body)).isEmpty();
    final String branch =
        "  void t(String s, boolean b) {\n    if (s == null) {\n      b = false;\n    }\n    if (b) {\n      s.length();\n    }\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH, branch)).isEmpty();
  }

  @Test
  void aNullReachedOnlyThroughACatchIsAnExceptionPathPattern() {
    final String body =
        "  void t(String s) {\n    try {\n      read();\n    } catch (RuntimeException e) {\n"
            + "      if (s == null) {\n        read();\n      }\n      s.length();\n    }\n  }\n  void read() {}\n";
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH_EXCEPTION, body))
        .containsExactly("9:7");
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH, body)).isEmpty();
    final String two =
        body.replace(
                "s.length();",
                "if (flag()) {\n        s.trim();\n      } else {\n        s.strip();\n      }")
            + "  boolean flag() { return true; }\n";
    assertThat(run(NullDataflowCheck.Mode.GUARANTEED_DEREF_ON_EXCEPTION_PATH, two))
        .containsExactly("10:9");
    assertThat(run(NullDataflowCheck.Mode.GUARANTEED_DEREF, two)).isEmpty();
  }

  @Test
  void aNullPassedForAParameterThatMustNotBeNullIsReported() {
    final String body = "  void t(Api api) {\n    api.take(null);\n  }\n";
    final SpanFacts annotated = target("Api", "take", 1, "javax.annotation.Nonnull");
    assertThat(
            run(NullDataflowCheck.Mode.NONNULL_PARAM_VIOLATION, body, "api.take(null)", annotated))
        .containsExactly("3:5");
    assertThat(
            run(
                NullDataflowCheck.Mode.NONNULL_PARAM_VIOLATION,
                body,
                "api.take(null)",
                target("Api", "take", 1)))
        .isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.NONNULL_PARAM_VIOLATION,
                body.replace("null", "\"x\""),
                "api.take(\"x\")",
                annotated))
        .isEmpty();
    assertThat(run(NullDataflowCheck.Mode.NONNULL_PARAM_VIOLATION, body)).isEmpty();
  }

  @Test
  void aNullPassedToAParameterTheCalleeDereferencesIsReportedByHowTheCalleeIsBound() {
    final String body =
        "  private void use(String s) {\n    s.length();\n  }\n  void t() {\n    use(null);\n  }\n";
    final SpanFacts call = target("C", "use", 1);
    assertThat(run(NullDataflowCheck.Mode.NULL_PARAM_DEREF_NONVIRTUAL, body, "use(null)", call))
        .containsExactly("6:5");
    final String open = body.replace("private ", "");
    assertThat(run(NullDataflowCheck.Mode.NULL_PARAM_DEREF_NONVIRTUAL, open, "use(null)", call))
        .isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS,
                open,
                "use(null)",
                call))
        .isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS,
                open.replace("void use", "final void use"),
                "use(null)",
                call))
        .containsExactly("6:5");
    assertThat(
            run(
                NullDataflowCheck.Mode.NULL_PARAM_DEREF_NONVIRTUAL,
                body.replace("s.length();", "if (s != null) {\n      s.length();\n    }"),
                "use(null)",
                call))
        .isEmpty();
    assertThat(run(NullDataflowCheck.Mode.NULL_PARAM_DEREF_NONVIRTUAL, body)).isEmpty();
  }

  @Test
  void aNullReturnedOrStoredWhereItMustNotBeIsReported() {
    final String returned = "  @NonNull String t() {\n    String s = null;\n    return s;\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.NONNULL_RETURN_VIOLATION, returned))
        .containsExactly("4:12");
    assertThat(
            run(NullDataflowCheck.Mode.NONNULL_RETURN_VIOLATION, returned.replace("@NonNull ", "")))
        .isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.NONNULL_RETURN_VIOLATION,
                returned.replace("String t()", "String toString()")))
        .isEmpty();
    final String stored = "  @NonNull String f;\n  void t() {\n    this.f = null;\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.STORE_INTO_NONNULL_FIELD, stored)).containsExactly("4:5");
    assertThat(
            run(NullDataflowCheck.Mode.STORE_INTO_NONNULL_FIELD, stored.replace("@NonNull ", "")))
        .isEmpty();
  }

  @Test
  void aNullThatIsCertainToBeReturnedOrPassedAfterAMergeIsReportedWithTheMatchingPattern() {
    final String returned =
        "  @NonNull String t(boolean b) {\n    String s = null;\n    if (b) {\n      s = \"x\";\n    }\n    return s;\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.NONNULL_RETURN_VIOLATION, returned))
        .containsExactly("7:12");
    final String passed =
        "  void t(Api api, boolean b) {\n    String s = null;\n    if (b) {\n      s = \"x\";\n    }\n    api.take(s);\n  }\n";
    assertThat(
            run(
                NullDataflowCheck.Mode.NULL_PARAM_DEREF,
                passed,
                "api.take(s)",
                target("Api", "take", 1, "javax.annotation.Nonnull")))
        .containsExactly("7:14");
  }

  @Test
  void aTryThatCatchesNullPointerExceptionAndUnknownFactsAreSilent() {
    final String body =
        "  void t() {\n    String s = null;\n    try {\n      s.length();\n    } catch (NullPointerException e) {\n      return;\n    }\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.ALWAYS_NULL, body)).isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.ALWAYS_NULL,
                "  void t(java.util.function.Supplier<String> g) {\n    String s = null;\n    Runnable r = () -> g.get();\n"
                    + "    s = g.get();\n    s.length();\n  }\n"))
        .isEmpty();
    assertThat(
            new ParsedFacts("class C {}")
                .plain(new NullDataflowCheck(NullDataflowCheck.Mode.ALWAYS_NULL, "m")))
        .isEmpty();
  }
}
