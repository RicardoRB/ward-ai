package dev.ward.analysis.application.python;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.TypeFactsFixture;
import dev.ward.analysis.application.rule.TypeFactsRun;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.BuiltinNames;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.python.PythonScopes;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.scope.CachedScopes;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The frontends the fixture runner of this module gives the rule engine. */
final class PythonFixtureFrontends {

  private static final String BUILTINS = "/dev/ward/analysis/lang/python/builtins/python.txt";

  private PythonFixtureFrontends() {}

  static List<FrontendSupport> of(final AnalysisLanguage language) {
    return List.of(
        new FrontendSupport(
            language,
            new AntlrLanguageFrontend(),
            Set.of(RuleCapability.SYNTAX, RuleCapability.SYMBOLS, RuleCapability.SCOPES),
            (unit, tree) -> new ControlFlowResult(List.of(), List.of()),
            new CachedScopes(new PythonScopes(builtins()))));
  }

  private static BuiltinNames builtins() {
    try (InputStream stream = PythonFixtureFrontends.class.getResourceAsStream(BUILTINS)) {
      final Set<String> names =
          Set.copyOf(
              List.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")));
      return environments -> names;
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  /** A rule that needs types gets the facts of the fixture's {@code types.json}. */
  static List<FrontendSupport> forRule(
      final RuleDefinition rule, final AnalysisLanguage language, final Path directory) {
    final List<FrontendSupport> frontends = of(language);
    if (rule.capability() != RuleCapability.TYPES) {
      return frontends;
    }
    final Path types = directory.resolve("types.json");
    final UnitTypeFacts unitFacts =
        Files.isRegularFile(types) ? TypeFactsFixture.read(types) : UnitTypeFacts.none();
    final TypeFacts facts = (unit, spans, kinds) -> unitFacts;
    final Map<AnalysisLanguage, TypeFacts> sources = new EnumMap<>(AnalysisLanguage.class);
    sources.put(language, facts);
    return new TypeFactsRun(sources).withTypes(frontends);
  }
}
