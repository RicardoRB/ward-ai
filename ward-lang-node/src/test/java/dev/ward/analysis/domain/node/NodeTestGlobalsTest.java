package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Test-framework globals resolve in a test file by its path, and nowhere else. */
final class NodeTestGlobalsTest {

  private static final String JEST_TEST =
      "describe('a', () => { it('b', () => expect(1).toBe(1)); });\n";

  @Test
  void jestStyleTestFileUsesItsGlobalsWithoutImports() {
    assertThat(unresolved("src/app.test.js", JEST_TEST)).isEmpty();
  }

  @Test
  void sameCodeOutsideTheTestPatternsStillReports() {
    assertThat(unresolved("src/app.js", JEST_TEST))
        .containsExactlyInAnyOrder("describe", "it", "expect");
  }

  @Test
  void specFilesAndTestFoldersAreTestFiles() {
    assertThat(unresolved("src/app.spec.ts", JEST_TEST)).isEmpty();
    assertThat(unresolved("src/__tests__/app.tsx", JEST_TEST)).isEmpty();
    assertThat(unresolved("test/unit/app.mjs", JEST_TEST)).isEmpty();
    assertThat(unresolved("tests/app.cts", JEST_TEST)).isEmpty();
  }

  @Test
  void nestedTestFolderIsNotATestFolder() {
    assertThat(unresolved("src/test/app.js", JEST_TEST)).isNotEmpty();
  }

  @Test
  void vitestGlobalsAreTestGlobals() {
    assertThat(unresolved("src/app.test.ts", "vi.fn(); bench(() => {});")).isEmpty();
  }

  @Test
  void nonDeclaredNameStillReportsInATestFile() {
    assertThat(unresolved("src/app.test.js", "missingName();")).containsExactly("missingName");
  }

  /** The names no declaration or built-in resolves, once the unit's own globals are declared. */
  private static List<String> unresolved(final String path, final String source) {
    final SourceUnit unit = new SourceUnit(path, AnalysisLanguage.JAVASCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    final UnitScopes scopes = new NodeScopeAnalysis(environments -> Set.of()).analyze(unit, root);
    return scopes.unresolved().stream().map(reference -> reference.name()).distinct().toList();
  }
}
