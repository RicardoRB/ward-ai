package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the whole-project rule for unwritten public fields over several files at once. */
class ProjectFieldRuleTest {

  private static final int SELECT_LINE = 3;
  private static final String RULE_ID = "ward:java/np-unwritten-public-or-protected-field";
  private static final String OWNER =
      "public class Owner {\n  public String name;\n  protected Object token;\n}\n";
  private static final String READER =
      "class Reader {\n  int size(Owner owner) {\n    return owner.name.length();\n  }\n}\n";

  private final RuleDefinition rule =
      CatalogRuleDefinitions.all().stream()
          .filter(definition -> RULE_ID.equals(definition.id()))
          .findFirst()
          .orElseThrow();
  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());
  private final RuleEngine engine = new RuleEngine(BundledRuleMatchers.create());

  private static SourceUnit unit(final String path, final String text) {
    return new SourceUnit(path, AnalysisLanguage.JAVA, text);
  }

  @Test
  void aFieldNoFileWritesIsReportedWhereAnotherFileSelectsFromIt() {
    final List<AnalysisFinding> findings =
        engine.runProject(
            List.of(unit("Owner.java", OWNER), unit("Reader.java", READER)),
            frontends,
            List.of(rule));

    assertThat(findings)
        .extracting(AnalysisFinding::file, AnalysisFinding::line)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("Reader.java", SELECT_LINE));
  }

  @Test
  void aWriteInAnotherFileClearsTheField() {
    final String writer =
        "class Writer {\n  void set(Owner owner) {\n    owner.name = \"a\";\n  }\n}\n";

    assertThat(
            engine.runProject(
                List.of(
                    unit("Owner.java", OWNER),
                    unit("Reader.java", READER),
                    unit("Writer.java", writer)),
                frontends,
                List.of(rule)))
        .isEmpty();
  }

  @Test
  void aWriteOfNullDoesNotClearTheField() {
    final String writer =
        "class Writer {\n  void set(Owner owner) {\n    owner.name = null;\n  }\n}\n";

    assertThat(
            engine.runProject(
                List.of(
                    unit("Owner.java", OWNER),
                    unit("Reader.java", READER),
                    unit("Writer.java", writer)),
                frontends,
                List.of(rule)))
        .hasSize(1);
  }

  @Test
  void aUnitRunAloneFindsNothingBecauseTheRuleIsAFactAboutTheProject() {
    assertThat(
            engine
                .run(
                    List.of(unit("Owner.java", OWNER), unit("Reader.java", READER)),
                    frontends,
                    List.of(rule))
                .findings())
        .isEmpty();
  }
}
