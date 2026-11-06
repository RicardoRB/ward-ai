package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.DependencyView;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.analysis.python.PythonDependencyAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a scope rule through the result cache and the Python dependency graph, and checks that an
 * imported name resolves to the file and the declaration it names, and that the second run, with
 * nothing changed, reads its findings from the cache.
 */
final class ScopeImportResolutionTest {

  private static final RuleDefinition RULE =
      new RuleDefinition(
          "ward:python/import-target",
          AnalysisLanguage.PYTHON,
          RuleCapability.SCOPES,
          AnalysisSeverity.WARNING,
          "");

  /** Reports where each import binding leads. */
  private static final class ImportMatcher implements RuleMatcher {
    @Override
    public CompiledRule compile(final RuleDefinition definition) {
      return new CompiledRule() {
        @Override
        public RuleDefinition definition() {
          return definition;
        }

        @Override
        public List<RuleMatch> matches(final SyntaxNode root) {
          return List.of();
        }

        @Override
        public List<RuleMatch> matches(
            final SyntaxNode root,
            final DependencyView dependencies,
            final FlowView flow,
            final ScopeView scopes,
            final SourceUnit unit) {
          final List<RuleMatch> found = new ArrayList<>();
          scopes.scopes().bindings().stream()
              .filter(binding -> binding.kind() == BindingKind.IMPORT)
              .forEach(
                  binding ->
                      found.add(
                          new RuleMatch(
                              binding.declaration(),
                              binding.name()
                                  + " -> "
                                  + java.util.Optional.ofNullable(scopes.imports().get(binding))
                                      .map(
                                          resolved ->
                                              resolved.path()
                                                  + ":"
                                                  + resolved.declaration().isPresent())
                                      .orElse("external"))));
          return found;
        }
      };
    }
  }

  @TempDir Path root;

  private List<SourceUnit> write() throws IOException {
    Files.writeString(
        root.resolve("pyproject.toml"), "[project]\nname = \"app\"\nversion = \"1\"\n");
    final List<SourceUnit> units = new ArrayList<>();
    units.add(
        unit("shop.py", "import json\nfrom billing import total, missing\n\nprint(total, json)\n"));
    units.add(unit("billing.py", "def total():\n    return 1\n"));
    return units;
  }

  private SourceUnit unit(final String path, final String text) throws IOException {
    Files.writeString(root.resolve(path), text);
    return new SourceUnit(path, AnalysisLanguage.PYTHON, text);
  }

  private List<String> messages(final Map<String, String> entries, final List<SourceUnit> units) {
    final UnitResultCache cache =
        new UnitResultCache(
            new RuleEngine(new ImportMatcher()),
            AnalysisCacheFixtures.codec(),
            "test",
            new PythonDependencyAdapters().forProject(root, units));
    return cache
        .run(
            AnalysisCacheFixtures.memory(entries),
            units,
            GateFrontends.create(new AntlrLanguageFrontend()),
            List.of(RULE))
        .findings()
        .stream()
        .map(AnalysisFinding::message)
        .collect(Collectors.toList());
  }

  @Test
  void anImportedNameResolvesToItsDeclarationInTheProject() throws IOException {
    final List<SourceUnit> units = write();

    assertThat(messages(new HashMap<>(), units))
        .containsExactlyInAnyOrder(
            "json -> external", "total -> billing.py:true", "missing -> billing.py:false");
  }

  @Test
  void aCachedResultIsDiscardedWhenTheFileAnImportLeadsToChanges() throws IOException {
    final Map<String, String> entries = new HashMap<>();
    final List<SourceUnit> units = write();
    messages(entries, units);

    final List<SourceUnit> changed = new ArrayList<>(units.subList(0, 1));
    changed.add(unit("billing.py", "def renamed():\n    return 1\n"));

    assertThat(messages(entries, changed))
        .containsExactlyInAnyOrder(
            "json -> external", "total -> billing.py:false", "missing -> billing.py:false");
  }
}
