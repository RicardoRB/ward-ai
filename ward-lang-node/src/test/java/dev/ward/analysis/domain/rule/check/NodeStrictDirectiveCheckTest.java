package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The strict directive check reads each file's source type from its path, as ESLint does. */
class NodeStrictDirectiveCheckTest {

  private static final String TOP = "\"use strict\";\nconst a = 1;\n";
  private static final String NONE = "const a = 1;\n";

  @Test
  void moduleFileWithoutDirectiveIsSilent() {
    assertThat(run("src/app.js", NodeStrictDirectiveCheck.Mode.SAFE, NONE)).isEmpty();
    assertThat(run("src/app.ts", NodeStrictDirectiveCheck.Mode.SAFE, NONE)).isEmpty();
  }

  @Test
  void redundantDirectiveInModuleIsReportedAsModule() {
    assertThat(messages(run("src/app.js", NodeStrictDirectiveCheck.Mode.SAFE, TOP)))
        .containsExactly("module");
    assertThat(messages(run("src/app.mts", NodeStrictDirectiveCheck.Mode.SAFE, TOP)))
        .containsExactly("module");
  }

  @Test
  void commonjsFileWithoutDirectiveGetsTheGlobalForm() {
    final List<RuleMatch> found = run("lib/tool.cjs", NodeStrictDirectiveCheck.Mode.SAFE, NONE);

    assertThat(messages(found)).containsExactly("global");
    assertThat(found.get(0).span().startLine()).isEqualTo(1);
  }

  @Test
  void commonjsFileWithGlobalDirectiveIsSilent() {
    assertThat(run("lib/tool.cjs", NodeStrictDirectiveCheck.Mode.SAFE, TOP)).isEmpty();
  }

  @Test
  void commonjsFileWithImportIsAModule() {
    final String withImport = "\"use strict\";\nimport x from \"y\";\n";

    assertThat(messages(run("lib/tool.cjs", NodeStrictDirectiveCheck.Mode.SAFE, withImport)))
        .containsExactly("module");
  }

  @Test
  void commonjsFileFollowsAnExplicitNeverMode() {
    assertThat(messages(run("lib/tool.cts", NodeStrictDirectiveCheck.Mode.NEVER, TOP)))
        .containsExactly("never");
  }

  private static List<RuleMatch> run(
      final String path, final NodeStrictDirectiveCheck.Mode mode, final String source) {
    final SourceUnit unit = new SourceUnit(path, AnalysisLanguage.JAVASCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    return new NodeStrictDirectiveCheck(mode, messages()).matches(root, null, unit);
  }

  /** Each message id maps to itself, so a match names the id it was reported under. */
  private static Map<String, String> messages() {
    return Map.of(
        "module", "module",
        "global", "global",
        "function", "function",
        "never", "never",
        "multiple", "multiple",
        "unnecessary", "unnecessary",
        "unnecessaryInClasses", "unnecessaryInClasses",
        "nonSimpleParameterList", "nonSimpleParameterList",
        "wrap", "wrap");
  }

  private static List<String> messages(final List<RuleMatch> found) {
    return found.stream().map(RuleMatch::message).toList();
  }
}
