package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOptions;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.analysis.JvmDependencyAdapters;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The getters of an entity that a handler binds, read in a service of another file (#769). */
class BoundEntityGettersTest {
  private static final String RULE = "ward:java/path-injection";
  private static final String ENTITY =
      "package gen;\npublic class Table {\n  public String getGenPath() { return null; }\n}\n";
  private static final String SERVICE =
      "package gen;\nimport java.io.File;\npublic class TableService {\n"
          + "  File target(Table table) {\n    return new File(table.getGenPath());\n  }\n}\n";
  private static final String CONTROLLER =
      "package gen;\nimport org.springframework.web.bind.annotation.RequestBody;\n"
          + "public class TableController {\n  void save(@RequestBody Table table) {}\n}\n";
  private static final String PLAIN_CONTROLLER =
      "package gen;\npublic class TableController {\n  void save(Table table) {}\n}\n";

  @TempDir Path root;

  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());
  private final List<RuleDefinition> rules =
      new ClasspathRuleSource().load().rules().stream()
          .filter(rule -> RULE.equals(rule.id()))
          .map(rule -> RuleOptions.apply(rule, Map.of("min-confidence", "possible")))
          .toList();

  private List<AnalysisFinding> run(final String controller) {
    final List<SourceUnit> units =
        List.of(
            new SourceUnit("gen/Table.java", AnalysisLanguage.JAVA, ENTITY),
            new SourceUnit("gen/TableService.java", AnalysisLanguage.JAVA, SERVICE),
            new SourceUnit("gen/TableController.java", AnalysisLanguage.JAVA, controller)
        );
    return new UnitResultCache(
        new RuleEngine(new DeclarativeRuleMatcher()),
        AnalysisCacheFixtures.codec(),
        "test",
        new JvmDependencyAdapters().forProject(root, units)
    )
        .run(AnalysisCacheFixtures.NO_CACHE, units, frontends, rules)
        .findings();
  }

  @Test
  @DisplayName("a getter of a type that a handler of another file binds is a possible source")
  void aServiceReadsAGetterOfABoundType() {
    assertThat(run(CONTROLLER))
        .extracting(AnalysisFinding::file)
        .containsExactly("gen/TableService.java");
  }

  @Test
  @DisplayName("a type that no handler binds has no source getters")
  void aTypeThatNoHandlerBindsIsClean() {
    assertThat(run(PLAIN_CONTROLLER)).isEmpty();
  }
}
