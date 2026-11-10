package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintAnalysis;
import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintFinding;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** An HTML-string sink on a TypeScript or JavaScript string built from an untrusted parameter. */
class NodeStringBuildTaintTest {

  private static final Pattern HTML_TAG = Pattern.compile("<[a-zA-Z]");

  private static final TaintSpec HTML_SPEC =
      new TaintSpec(
          List.of(
              new TaintEntry(
                  FlowSiteKind.PARAMETER,
                  "Untrusted",
                  Optional.empty(),
                  Optional.empty(),
                  List.of())),
          List.of(
              new TaintEntry(
                  FlowSiteKind.STRING_BUILD,
                  "",
                  Optional.empty(),
                  Optional.empty(),
                  List.of(),
                  Optional.of(HTML_TAG))),
          List.of(),
          List.of());

  /** Scenario: an untrusted value in a {@code +} string with an HTML tag is reported once. */
  @Test
  void untrustedValueInAnHtmlStringIsReported() {
    final List<TaintFinding> found =
        run(
            "class Handler {\n  handle(@Untrusted input: string) {\n"
                + "    return '<p>' + input + '</p>';\n  }\n}\n");

    assertThat(found).hasSize(1);
  }

  /** Scenario: a template with an HTML tag and an untrusted substitution is reported. */
  @Test
  void untrustedSubstitutionInAnHtmlTemplateIsReported() {
    final List<TaintFinding> found =
        run(
            "class Handler {\n  handle(@Untrusted input: string) {\n"
                + "    return `<div>${input}</div>`;\n  }\n}\n");

    assertThat(found).hasSize(1);
  }

  /** Scenario: a string without an HTML tag is not a sink for the pattern. */
  @Test
  void untrustedValueInAPlainStringIsNotReported() {
    final List<TaintFinding> found =
        run(
            "class Handler {\n  handle(@Untrusted input: string) {\n"
                + "    return 'id: ' + input;\n  }\n}\n");

    assertThat(found).isEmpty();
  }

  private static List<TaintFinding> run(final String source) {
    return TaintAnalysis.run(
        NodeTaintFixtures.graph(AnalysisLanguage.TYPESCRIPT, source, "handle"), HTML_SPEC);
  }
}
