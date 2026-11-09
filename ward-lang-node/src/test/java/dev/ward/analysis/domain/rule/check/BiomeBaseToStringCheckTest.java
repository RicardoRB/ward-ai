package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.TypeFactsFixture;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class BiomeBaseToStringCheckTest {

  private static final BiomeBaseToStringCheck CHECK = new BiomeBaseToStringCheck("message");

  @Test
  void reportsAPlainObjectOrAClassWithoutToStringThatIsStringified() {
    final String json =
        "{\"2:8\": {\"type\": {\"name\": \"{}\"}},"
            + " \"4:8\": {\"type\": {\"name\": \"Plain\"}, \"shape\": {\"kind\": \"class\"},"
            + " \"members\": {\"methods\": []}}}";
    assertThat(reported("const v = {};\nString(v);\nconst p = new Plain();\nString(p);\n", json))
        .containsExactly("v", "p");
  }

  @Test
  void keepsAClassThatDeclaresToString() {
    final String json =
        "{\"2:8\": {\"type\": {\"name\": \"Custom\"}, \"shape\": {\"kind\": \"class\"},"
            + " \"members\": {\"methods\": [{\"declaring\": {\"name\": \"Custom\"},"
            + " \"name\": \"toString\", \"params\": [], \"returns\": {\"name\": \"string\"}}]}}}";
    assertThat(reported("const c = new Custom();\nString(c);\n", json)).isEmpty();
  }

  @Test
  void staysSilentWithoutTypeFacts() {
    assertThat(reported("const v = {};\nString(v);\n", "{}")).isEmpty();
  }

  private static List<String> reported(final String source, final String json) {
    final SourceUnit unit = new SourceUnit("m", AnalysisLanguage.JAVASCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    final UnitTypeFacts types = TypeFactsFixture.parse(json);
    return CHECK.matches(root, null, null, types, unit).stream()
        .map(RuleMatch::span)
        .map(span -> source.substring(span.startOffset(), span.endOffset()))
        .toList();
  }
}
