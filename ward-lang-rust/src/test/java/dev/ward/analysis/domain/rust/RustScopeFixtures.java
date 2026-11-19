package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.SoftAssertions;

/** Parses Rust source and builds its scopes, for tests. */
final class RustScopeFixtures {
  private static final String UNRESOLVED = "unresolved";

  private RustScopeFixtures() {}

  /** Parses {@code source}, which must have no syntax diagnostic. */
  static SyntaxNode tree(final String source) {
    final ParseResult result = new AntlrLanguageFrontend().parse(unit(source));
    if (!result.diagnostics().isEmpty()) {
      throw new IllegalArgumentException("does not parse: " + result.diagnostics());
    }
    return result.tree();
  }

  /** Parses {@code source} and builds its scopes, with no built-in names. */
  static UnitScopes scopes(final String source) {
    return new RustScopeAnalysis(environments -> Set.of()).analyze(unit(source), tree(source));
  }

  static SourceUnit unit(final String source) {
    return new SourceUnit("m.rs", AnalysisLanguage.RUST, source);
  }

  /**
   * Checks where the reads resolve. Each expectation reads {@code name@line=declLine} or {@code
   * name@line=declLine:declColumn}: the first read of {@code name} on {@code line} is declared on
   * that line (and column); {@code name@line=none} says it resolves to nothing.
   */
  static void assertResolves(final UnitScopes scopes, final String... expectations) {
    final SoftAssertions soft = new SoftAssertions();
    for (final String expectation : expectations) {
      final String name = expectation.substring(0, expectation.indexOf('@'));
      final String line = expectation.substring(
          expectation.indexOf('@') + 1,
          expectation.indexOf('=')
      );
      final String wanted = expectation.substring(expectation.indexOf('=') + 1);
      final String found = declaredAt(scopes, name, Integer.parseInt(line));
      soft.assertThat(wanted.contains(":") ? found : found.replaceAll(":.*", ""))
          .as(expectation)
          .isEqualTo("none".equals(wanted) ? UNRESOLVED : wanted);
    }
    soft.assertAll();
  }

  /** Where the first read of {@code name} on {@code line} is declared, as {@code line:column}. */
  private static String declaredAt(final UnitScopes scopes, final String name, final int line) {
    return scopes.references().stream()
        .filter(use -> use.name().equals(name) && use.span().startLine() == line)
        .filter(use -> use.access() == ReferenceAccess.READ)
        .findFirst()
        .map(RustScopeFixtures::target)
        .orElse("no read of " + name + " on line " + line);
  }

  private static String target(final NameReference use) {
    return use.resolved()
        .map(
            binding ->
                binding.declaration().startLine() + ":" + binding.declaration().startColumn()
        )
        .orElse(UNRESOLVED);
  }

  /** Every binding named {@code name}. */
  static List<Binding> named(final UnitScopes scopes, final String name) {
    return scopes.bindings().stream().filter(binding -> binding.name().equals(name)).toList();
  }

  /** The number of references named {@code name}. */
  static long uses(final UnitScopes scopes, final String name) {
    return scopes.references().stream().filter(use -> use.name().equals(name)).count();
  }
}
