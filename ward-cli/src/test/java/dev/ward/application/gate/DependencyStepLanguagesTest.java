package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DependencyStepLanguagesTest {
  private static final Span AT = new Span(1, 1, 1, 9, 0, 8);
  private static final Set<RuleCapability> ALL =
      Set.of(RuleCapability.SYNTAX, RuleCapability.SYMBOLS);
  private static final List<SourceUnit> UNITS =
      List.of(
          new SourceUnit("src/A.java", AnalysisLanguage.JAVA, "class A {}"),
          new SourceUnit("web/a.ts", AnalysisLanguage.TYPESCRIPT, "export const a = 1;"),
          new SourceUnit("web/b.js", AnalysisLanguage.JAVASCRIPT, "export const b = 2;"),
          new SourceUnit("tools/c.py", AnalysisLanguage.PYTHON, "c = 3"),
          new SourceUnit("src/D.kt", AnalysisLanguage.KOTLIN, "class D")
      );

  private final Map<AnalysisLanguage, Integer> parses = new HashMap<>();
  private final Map<String, String> entries = new HashMap<>();
  private final AnalysisCache cache =
      new AnalysisCache() {
        @Override
        public Optional<String> read(final String key) {
          return Optional.ofNullable(entries.get(key));
        }

        @Override
        public void write(final String key, final String value) {
          entries.put(key, value);
        }
      };
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

  @Test
  @DisplayName("only the selected languages are parsed")
  void onlyTheSelectedLanguagesAreParsed() {
    // Given a rule set that selects Java only, in a repository with TypeScript, JavaScript and
    // Python files
    final UnitResultCache results = results(DependencyStepFakes.separateResolvers(languages()));

    // When the dependency step of the ward gate runs
    results.run(cache, UNITS, frontends(), List.of(javaRule()));

    // Then it parses no TypeScript, JavaScript or Python file
    assertThat(parses).containsOnlyKeys(AnalysisLanguage.JAVA);
  }

  @Test
  @DisplayName("a language that shares a resolver with a selected language is still parsed")
  void sharedResolverLanguageIsStillParsed() {
    // Given a rule set that selects Java only, and one resolver for Java and Kotlin
    final UnitResultCache results = results(DependencyStepFakes.sharedJvmResolver(languages()));

    // When the dependency step of the ward gate runs
    results.run(cache, UNITS, frontends(), List.of(javaRule()));

    // Then the Kotlin file is parsed, because edges from Java may reach it
    assertThat(parses).containsOnlyKeys(AnalysisLanguage.JAVA, AnalysisLanguage.KOTLIN);
  }

  private UnitResultCache results(final List<DependencySupport> dependencies) {
    return new UnitResultCache(
        new RuleEngine(matcher),
        AnalysisCacheFixtures.codec(),
        "0.11.0",
        dependencies
    );
  }

  private static RuleDefinition javaRule() {
    return new RuleDefinition(
        "ward:java/cross-file",
        AnalysisLanguage.JAVA,
        RuleCapability.SYMBOLS,
        AnalysisSeverity.ERROR,
        "body"
    );
  }

  private List<FrontendSupport> frontends() {
    return languages().stream().map(this::frontend).toList();
  }

  private FrontendSupport frontend(final AnalysisLanguage language) {
    final LanguageFrontend frontend =
        unit -> {
          parses.merge(unit.language(), 1, Integer::sum);
          return new ParseResult(new SyntaxNode(unit.path(), AT, Map.of(), List.of()), List.of());
        };
    return new FrontendSupport(language, frontend, ALL);
  }

  private static List<AnalysisLanguage> languages() {
    return List.of(
        AnalysisLanguage.JAVA,
        AnalysisLanguage.KOTLIN,
        AnalysisLanguage.TYPESCRIPT,
        AnalysisLanguage.JAVASCRIPT,
        AnalysisLanguage.PYTHON
    );
  }
}
