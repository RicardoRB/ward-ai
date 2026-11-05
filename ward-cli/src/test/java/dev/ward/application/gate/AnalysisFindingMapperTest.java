package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalysisFindingMapperTest {

  private static final int LINE = 4;
  private static final int COLUMN = 2;
  private static final int LATER_COLUMN = 5;

  private final AnalysisFindingMapper mapper = new AnalysisFindingMapper();

  @Test
  void carriesGateToolAndLocation() {
    final AnalysisFinding source =
        new AnalysisFinding(
            "rule.one",
            AnalysisSeverity.WARNING,
            "msg",
            "a/B.java",
            4,
            2,
            "https://help",
            List.of());

    final Finding mapped = mapper.map("gate-x", "engine", List.of(source)).getFirst();

    assertThat(mapped.gateId()).isEqualTo("gate-x");
    assertThat(mapped.tool()).isEqualTo("engine");
    assertThat(mapped.ruleId()).isEqualTo("rule.one");
    assertThat(mapped.file()).isEqualTo("a/B.java");
    assertThat(mapped.line()).isEqualTo(LINE);
    assertThat(mapped.column()).isEqualTo(COLUMN);
    assertThat(mapped.helpUri()).isEqualTo("https://help");
    assertThat(mapped.severity()).isEqualTo(FindingSeverity.WARNING);
  }

  @Test
  void mapsEverySeverityConstant() {
    final List<AnalysisFinding> sources =
        Arrays.stream(AnalysisSeverity.values())
            .map(
                severity ->
                    new AnalysisFinding(
                        "r", severity, "m", "a.java", LINE, COLUMN, null, List.of()))
            .toList();

    assertThat(mapper.map("g", "t", sources))
        .extracting(Finding::severity)
        .containsExactlyInAnyOrder(FindingSeverity.values());
  }

  @Test
  void sortsByFileLineColumnRuleAndMessageWithNullsLast() {
    final AnalysisFinding late = finding("b.java", 1, 1, "r", "m");
    final AnalysisFinding noLocation = finding(null, null, null, "r", "m");
    final AnalysisFinding sameLineLaterColumn = finding("a.java", 2, 5, "r", "m");
    final AnalysisFinding first = finding("a.java", 2, 1, "r", "m");

    final List<Finding> ordered =
        mapper.map("g", "t", List.of(noLocation, late, sameLineLaterColumn, first));

    assertThat(ordered)
        .extracting(Finding::file)
        .containsExactly("a.java", "a.java", "b.java", null);
    assertThat(ordered).extracting(Finding::column).containsExactly(1, LATER_COLUMN, 1, null);
  }

  @Test
  void outputOrderDoesNotDependOnInputOrder() {
    final List<AnalysisFinding> input =
        new ArrayList<>(
            List.of(finding("x.java", 3, 1, "a", "m"), finding("x.java", 1, 1, "b", "m")));
    final List<Finding> forward = mapper.map("g", "t", input);
    Collections.reverse(input);

    assertThat(mapper.map("g", "t", input)).isEqualTo(forward);
  }

  @Test
  void fakeFrontendFindingsAreMappedInOrder() {
    final LanguageFrontend frontend =
        unit ->
            new ParseResult(
                new SyntaxNode("file", new Span(1, 1, 1, 2, 0, 1), Map.of(), List.of()), List.of());
    final SyntaxNode root =
        frontend.parse(new SourceUnit("a.py", AnalysisLanguage.PYTHON, "x")).tree();
    final List<AnalysisFinding> found =
        List.of(finding("a.py", 1, 1, "z", "m"), finding("a.py", 1, 1, "y", "m"));

    assertThat(root.kind()).isEqualTo("file");
    assertThat(mapper.map("g", "t", found)).extracting(Finding::ruleId).containsExactly("y", "z");
  }

  private static AnalysisFinding finding(
      final String file,
      final Integer line,
      final Integer column,
      final String rule,
      final String message) {
    return new AnalysisFinding(
        rule, AnalysisSeverity.ERROR, message, file, line, column, null, List.of());
  }
}
