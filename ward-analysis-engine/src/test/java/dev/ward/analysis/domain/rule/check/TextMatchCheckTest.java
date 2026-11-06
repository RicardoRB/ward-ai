package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TextMatchCheckTest {

  private static final String COMMENT = "comment";
  private static final String UNIT = "unit";
  private static final SourceUnit FILE =
      new SourceUnit(
          "src/main/Foo.java",
          AnalysisLanguage.JAVA,
          "// TODO: remove\nint a;\nint b; // TODO: x\n");

  private final TreeLayout gen = new TreeLayout();

  private static TextMatchCheck text(
      final TextMatchCheck.Target target,
      final String pattern,
      final boolean required,
      final int limit,
      final Set<String> kinds) {
    return new TextMatchCheck(
        NodeFilter.of(kinds),
        new TextMatchCheck.Rule(target, Pattern.compile(pattern), required, limit, 1),
        "{value} / {max}");
  }

  @Test
  void textPatternReportsTheLineOfAForbiddenComment() {
    final SyntaxNode root =
        gen.node(UNIT, gen.leaf(COMMENT, "// TODO: remove"), gen.leaf("tok", "int"));

    final List<RuleMatch> matches =
        text(TextMatchCheck.Target.COMMENTS, "TODO:", false, 0, Set.of())
            .matches(root, FlowView.NONE, FILE);

    assertThat(TreeLayout.messages(matches)).containsExactly("// TODO: remove / 0");
    assertThat(matches.get(0).span().startLine()).isEqualTo(1);
  }

  @Test
  void textPatternOnLinesHonoursTheCountLimit() {
    final SyntaxNode root = gen.node(UNIT, gen.leaf("tok", "x"));

    final List<RuleMatch> matches =
        text(TextMatchCheck.Target.LINES, "TODO", false, 1, Set.of())
            .matches(root, FlowView.NONE, FILE);

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).span().startLine())
        .isEqualTo(FILE.text().lines().toList().indexOf("int b; // TODO: x") + 1);
  }

  @Test
  void textPatternOnStringsUsesTheFiltersKinds() {
    final SyntaxNode root =
        gen.node(UNIT, gen.leaf("str", "\"\\u00e9\""), gen.leaf("str", "\"ok\""));

    assertThat(text(TextMatchCheck.Target.STRINGS, "\\\\u", false, 0, Set.of("str")).matches(root))
        .hasSize(1);
  }

  @Test
  void textPatternOnFilenameAndHeaderCanRequireAMatch() {
    final SyntaxNode root = gen.node(UNIT, gen.leaf("tok", "x"));

    assertThat(
            text(TextMatchCheck.Target.FILENAME, "^[A-Z]\\w*\\.java$", true, 1, Set.of())
                .matches(root, FlowView.NONE, FILE))
        .isEmpty();
    assertThat(
            text(TextMatchCheck.Target.FILENAME, "^[a-z]", true, 1, Set.of())
                .matches(root, FlowView.NONE, FILE))
        .hasSize(1);
    assertThat(
            text(TextMatchCheck.Target.HEADER, "Copyright", true, 1, Set.of())
                .matches(root, FlowView.NONE, FILE))
        .hasSize(1);
    assertThat(
            text(TextMatchCheck.Target.HEADER, "TODO", true, 1, Set.of())
                .matches(root, FlowView.NONE, FILE))
        .isEmpty();
  }

  @Test
  void checksRunOnABareTreeWithoutAFile() {
    final SyntaxNode root = gen.node(UNIT, gen.leaf("tok", "x"));

    assertThat(text(TextMatchCheck.Target.LINES, "x", false, 0, Set.of()).matches(root)).isEmpty();
    assertThat(text(TextMatchCheck.Target.FILENAME, "x", true, 1, Set.of()).matches(root))
        .hasSize(1);
  }
}
