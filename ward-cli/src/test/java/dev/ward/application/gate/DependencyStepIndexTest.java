package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.DependencyResolver;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A cache filled by a rule set without dependency rules serves a later set that has them. */
class DependencyStepIndexTest {
  private static final Span AT = new Span(1, 1, 1, 2, 0, 1);
  private static final String DEPENDENCY = "src/A.java";
  private static final String DEPENDENT = "src/B.java";

  private final Map<String, String> entries = new HashMap<>();
  private final Set<String> parsed = new HashSet<>();
  private final RuleMatcher matcher =
      definition ->
      new CompiledRule() {
        @Override
        public RuleDefinition definition() {
          return definition;
        }

        @Override
        public List<RuleMatch> matches(final SyntaxNode root) {
          return List.of();
        }
      };
  private final List<FrontendSupport> frontends =
      List.of(
          new FrontendSupport(
              AnalysisLanguage.JAVA,
              unit -> {
                parsed.add(unit.path());
                return new ParseResult(
                    new SyntaxNode(unit.path(), AT, Map.of(), List.of()),
                    List.of()
                );
              },
              Set.of(RuleCapability.SYNTAX, RuleCapability.SYMBOLS)
          )
      );
  private final UnitResultCache results =
      new UnitResultCache(
          new RuleEngine(matcher),
          AnalysisCacheFixtures.codec(),
          "0.11.0",
          List.of(
              new DependencySupport(AnalysisLanguage.JAVA, (unit, tree) -> summary(unit), edges())
          )
      );

  @Test
  void aSetWithDependencyRulesRebuildsWhatASetWithoutThemSkipped() {
    final RuleDefinition plain = rule("plain", RuleCapability.SYNTAX);
    final RuleDefinition reader = rule("reader", RuleCapability.SYMBOLS);
    results.run(
        AnalysisCacheFixtures.memory(entries),
        units("export a"),
        frontends,
        List.of(plain)
    );
    results.run(
        AnalysisCacheFixtures.memory(entries),
        units("export a"),
        frontends,
        List.of(plain, reader)
    );
    parsed.clear();

    results.run(
        AnalysisCacheFixtures.memory(entries),
        units("export changed"),
        frontends,
        List.of(plain, reader)
    );

    assertThat(parsed).containsExactlyInAnyOrder(DEPENDENCY, DEPENDENT);
  }

  private static List<SourceUnit> units(final String dependencyText) {
    return List.of(
        new SourceUnit(DEPENDENCY, AnalysisLanguage.JAVA, dependencyText),
        new SourceUnit(DEPENDENT, AnalysisLanguage.JAVA, "import a")
    );
  }

  private static FileSummary summary(final SourceUnit unit) {
    final boolean importer = unit.text().startsWith("import");
    return new FileSummary(
        unit.path(),
        importer ? List.of() : List.of(new Symbol(unit.text(), AT)),
        importer ? List.of(new Reference("a", AT)) : List.of(),
        List.of()
    );
  }

  private static DependencyResolver edges() {
    return summaries -> {
      final List<DependencyEdge> edges = new ArrayList<>();
      for (final FileSummary summary : summaries) {
        for (final Reference reference : summary.imports()) {
          edges.add(new DependencyEdge(summary.path(), DEPENDENCY, reference));
        }
      }
      return edges;
    };
  }

  private static RuleDefinition rule(final String name, final RuleCapability capability) {
    return new RuleDefinition(
        "ward:java/" + name,
        AnalysisLanguage.JAVA,
        capability,
        AnalysisSeverity.ERROR,
        "body"
    );
  }
}
