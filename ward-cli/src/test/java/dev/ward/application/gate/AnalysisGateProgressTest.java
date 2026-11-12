package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateSection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnalysisGateProgressTest {
  private static final Project PROJECT =
      new Project(Path.of("/repo"), Language.JAVA, BuildTool.GRADLE, Framework.SPRING_BOOT);
  private static final String LENGTH = "ward:java/method-length";
  private static final String NAMING = "ward:java/type-name";
  private static final Span AT = new Span(3, 5, 3, 9, 20, 24);

  @Test
  void listsOneSectionPerRuleCategoryAndReportsItsSteps() {
    final List<String> steps = new ArrayList<>();

    final GateResult result =
        AnalysisGateTest.gate(
                List.of(categorized(LENGTH, "complexity"), categorized(NAMING, "naming")),
                Map.of(LENGTH, List.of(new RuleMatch(AT, "too long")), NAMING, List.of()),
                Set.of(RuleCapability.SYNTAX))
            .execute(PROJECT, steps::add);

    assertThat(result.sections())
        .containsExactly(new GateSection("complexity", 1, 0), new GateSection("naming", 0, 0));
    assertThat(steps)
        .containsExactly(
            "loading rules",
            "reading sources",
            "resolving dependencies",
            "analyzing 1 of 1 files with 2 rules",
            "project-wide rules");
  }

  private static RuleDefinition categorized(final String id, final String category) {
    return new RuleDefinition(
        id,
        Set.of(AnalysisLanguage.JAVA),
        RuleCapability.SYNTAX,
        AnalysisSeverity.ERROR,
        "body",
        category,
        Set.of(),
        List.of(),
        0);
  }
}
