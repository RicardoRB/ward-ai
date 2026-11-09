package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BiomeMemberPropertyCheckTest {

  private static final Pattern INNER =
      Pattern.compile("^(innerText|[\\x22\\x27]innerText[\\x22\\x27])$");

  private static final BiomeMemberPropertyCheck CHECK =
      new BiomeMemberPropertyCheck(Set.of("identifier", "StringLiteral"), INNER, "message");

  @Test
  void reportsADottedBracketedAndDestructuredReadOfTheName() {
    assertThat(reported(CHECK, "const a = node.innerText;\n")).containsExactly("innerText");
    assertThat(reported(CHECK, "const a = node['innerText'];\n")).containsExactly("'innerText'");
    assertThat(reported(CHECK, "const { innerText } = node;\n")).containsExactly("innerText");
  }

  @Test
  void ignoresAPlainVariableWithTheSameName() {
    assertThat(reported(CHECK, "const innerText = 1;\nconsole.log(innerText);\n")).isEmpty();
  }

  private static List<String> reported(final RuleCheck check, final String source) {
    final SyntaxNode root = parse(source);
    return check.matches(root).stream()
        .map(RuleMatch::span)
        .map(span -> source.substring(span.startOffset(), span.endOffset()))
        .toList();
  }

  private static SyntaxNode parse(final String source) {
    final SourceUnit unit = new SourceUnit("src/a.ts", AnalysisLanguage.TYPESCRIPT, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    return parsed.tree();
  }
}
