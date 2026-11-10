package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Symbol;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks which public declarations the module-scoped Swift accessibility rule reports. */
final class SwiftModuleAccessibilityTest {

  @Test
  void aPublicDeclarationOnlyItsOwnModuleUsesIsReported() {
    final Map<String, FileSummary> files =
        Map.of(
            SwiftAccessibilityFixtures.DECLARING,
            SwiftAccessibilityFixtures.exporting(
                SwiftAccessibilityFixtures.DECLARING, SwiftAccessibilityFixtures.SHAPE),
            SwiftAccessibilityFixtures.CORE_USER,
            SwiftAccessibilityFixtures.using(
                SwiftAccessibilityFixtures.CORE_USER, SwiftAccessibilityFixtures.SHAPE));

    assertThat(SwiftAccessibilityFixtures.findings(files, SwiftAccessibilityFixtures.CORE))
        .extracting(AnalysisFinding::file)
        .containsExactly(SwiftAccessibilityFixtures.DECLARING);
  }

  @Test
  void aUseFromAnotherModuleKeepsTheDeclarationPublic() {
    final Map<String, FileSummary> files =
        Map.of(
            SwiftAccessibilityFixtures.DECLARING,
                SwiftAccessibilityFixtures.exporting(
                    SwiftAccessibilityFixtures.DECLARING, SwiftAccessibilityFixtures.SHAPE),
            SwiftAccessibilityFixtures.CORE_USER,
                SwiftAccessibilityFixtures.using(
                    SwiftAccessibilityFixtures.CORE_USER, SwiftAccessibilityFixtures.SHAPE),
            SwiftAccessibilityFixtures.APP_USER,
                SwiftAccessibilityFixtures.using(
                    SwiftAccessibilityFixtures.APP_USER, SwiftAccessibilityFixtures.SHAPE));

    assertThat(SwiftAccessibilityFixtures.findings(files, SwiftAccessibilityFixtures.CORE))
        .isEmpty();
  }

  @Test
  void aConformanceFromAnotherModuleCountsAsAUse() {
    final Map<String, FileSummary> files =
        Map.of(
            SwiftAccessibilityFixtures.DECLARING,
                SwiftAccessibilityFixtures.exporting(
                    SwiftAccessibilityFixtures.DECLARING, SwiftAccessibilityFixtures.SHAPE),
            SwiftAccessibilityFixtures.CORE_USER,
                SwiftAccessibilityFixtures.using(
                    SwiftAccessibilityFixtures.CORE_USER, SwiftAccessibilityFixtures.SHAPE),
            SwiftAccessibilityFixtures.APP_USER,
                SwiftAccessibilityFixtures.using(
                    SwiftAccessibilityFixtures.APP_USER,
                    SwiftUnusedDefinitions.CONFORMANCE_PREFIX + SwiftAccessibilityFixtures.SHAPE));

    assertThat(SwiftAccessibilityFixtures.findings(files, SwiftAccessibilityFixtures.CORE))
        .isEmpty();
  }

  @Test
  void aUseFromAFileOfUnknownModuleBlocksTheFinding() {
    final Map<String, FileSummary> files =
        Map.of(
            SwiftAccessibilityFixtures.DECLARING,
                SwiftAccessibilityFixtures.exporting(
                    SwiftAccessibilityFixtures.DECLARING, SwiftAccessibilityFixtures.SHAPE),
            SwiftAccessibilityFixtures.CORE_USER,
                SwiftAccessibilityFixtures.using(
                    SwiftAccessibilityFixtures.CORE_USER, SwiftAccessibilityFixtures.SHAPE),
            SwiftAccessibilityFixtures.LOOSE,
                SwiftAccessibilityFixtures.using(
                    SwiftAccessibilityFixtures.LOOSE, SwiftAccessibilityFixtures.SHAPE));

    assertThat(SwiftAccessibilityFixtures.findings(files, SwiftAccessibilityFixtures.CORE))
        .isEmpty();
  }

  @Test
  void nothingIsReportedWithoutTheModuleOfTheDeclaringFileOrWithoutAModuleLayout() {
    final Map<String, FileSummary> files =
        Map.of(
            SwiftAccessibilityFixtures.DECLARING,
            SwiftAccessibilityFixtures.exporting(
                SwiftAccessibilityFixtures.DECLARING, SwiftAccessibilityFixtures.SHAPE),
            SwiftAccessibilityFixtures.CORE_USER,
            SwiftAccessibilityFixtures.using(
                SwiftAccessibilityFixtures.CORE_USER, SwiftAccessibilityFixtures.SHAPE));

    assertThat(
            SwiftAccessibilityFixtures.findings(
                files, Map.of(SwiftAccessibilityFixtures.CORE_USER, "Core")))
        .isEmpty();
    assertThat(UnusedDefinitionFindings.findings(List.of(SwiftAccessibilityFixtures.RULE), files))
        .isEmpty();
  }

  @Test
  void anUnusedDeclarationIsLeftToTheUnusedRule() {
    final Map<String, FileSummary> files =
        Map.of(
            SwiftAccessibilityFixtures.DECLARING,
            SwiftAccessibilityFixtures.exporting(
                SwiftAccessibilityFixtures.DECLARING, SwiftAccessibilityFixtures.SHAPE));

    assertThat(SwiftAccessibilityFixtures.findings(files, SwiftAccessibilityFixtures.CORE))
        .isEmpty();
  }

  @Test
  void aQualifiedPublicTypeIsMatchedByItsLastComponent() {
    final Map<String, FileSummary> files =
        Map.of(
            SwiftAccessibilityFixtures.DECLARING,
            SwiftAccessibilityFixtures.summary(
                SwiftAccessibilityFixtures.DECLARING,
                List.of(SwiftAccessibilityFixtures.publicSymbol("Outer.Inner"))),
            SwiftAccessibilityFixtures.CORE_USER,
            SwiftAccessibilityFixtures.using(SwiftAccessibilityFixtures.CORE_USER, "Inner"));

    assertThat(SwiftAccessibilityFixtures.findings(files, SwiftAccessibilityFixtures.CORE))
        .extracting(AnalysisFinding::message)
        .singleElement()
        .asString()
        .contains("'Outer.Inner'");
  }

  @Test
  void anExportThatIsNotPublicIsIgnored() {
    final Map<String, FileSummary> files =
        Map.of(
            SwiftAccessibilityFixtures.DECLARING,
            SwiftAccessibilityFixtures.summary(
                SwiftAccessibilityFixtures.DECLARING,
                List.of(
                    new Symbol(
                        SwiftAccessibilityFixtures.SHAPE,
                        new dev.ward.analysis.domain.ir.Span(1, 1, 1, 1, 0, 1),
                        ""))),
            SwiftAccessibilityFixtures.CORE_USER,
            SwiftAccessibilityFixtures.using(
                SwiftAccessibilityFixtures.CORE_USER, SwiftAccessibilityFixtures.SHAPE));

    assertThat(SwiftAccessibilityFixtures.findings(files, SwiftAccessibilityFixtures.CORE))
        .isEmpty();
  }

  @Test
  void aNonSwiftFileDoesNotBlockTheFinding() {
    final Map<String, FileSummary> files =
        Map.of(
            SwiftAccessibilityFixtures.DECLARING,
            SwiftAccessibilityFixtures.exporting(
                SwiftAccessibilityFixtures.DECLARING, SwiftAccessibilityFixtures.SHAPE),
            SwiftAccessibilityFixtures.CORE_USER,
            SwiftAccessibilityFixtures.using(
                SwiftAccessibilityFixtures.CORE_USER, SwiftAccessibilityFixtures.SHAPE),
            "tools/gen.py",
            SwiftAccessibilityFixtures.using("tools/gen.py", SwiftAccessibilityFixtures.SHAPE));

    assertThat(SwiftAccessibilityFixtures.findings(files, SwiftAccessibilityFixtures.CORE))
        .hasSize(1);
  }
}
