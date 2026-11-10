package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.ConstantValue;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class FloatPrecisionCheckTest {

  private static final FloatPrecisionCheck CHECK = new FloatPrecisionCheck("message");
  private static final TypeRef FLOAT = new TypeRef("float", List.of(), 0, true);
  private static final String SOURCE =
      "class C {\n  float t(float a) {\n    return a * 2;\n  }\n}\n";

  private static List<String> run(final String source, final SpanFacts facts) {
    return new ParsedFacts(source).at("expression", "a*2", facts).run(CHECK);
  }

  @Test
  void floatArithmeticIsReportedOutsideCodeNamedFloat() {
    assertThat(CHECK.needs().facts()).containsExactlyInAnyOrder(FactKind.TYPE, FactKind.CONSTANT);
    assertThat(run(SOURCE, ParsedFacts.type(FLOAT))).containsExactly("3:12");
    assertThat(
            new ParsedFacts(SOURCE.replace("a * 2", "a *= 2"))
                .at("expression", "a*=2", ParsedFacts.type(FLOAT))
                .run(CHECK))
        .hasSize(1);
  }

  @Test
  void floatNamedCodeOtherTypesConstantsAndUnknownFactsAreSilent() {
    assertThat(run(SOURCE.replace("class C", "class FloatC"), ParsedFacts.type(FLOAT))).isEmpty();
    assertThat(run(SOURCE.replace("float t", "float toFLOAT"), ParsedFacts.type(FLOAT))).isEmpty();
    assertThat(run("package a.floats;\n" + SOURCE, ParsedFacts.type(FLOAT))).isEmpty();
    assertThat(run(SOURCE.replace("float a", "Float a"), ParsedFacts.type(FLOAT))).isEmpty();
    assertThat(run(SOURCE, ParsedFacts.type(new TypeRef("double", List.of(), 0, true)))).isEmpty();
    assertThat(run(SOURCE, ParsedFacts.type(new TypeRef("float", List.of(), 1, true)))).isEmpty();
    assertThat(run(SOURCE.replace("a * 2", "a < 2"), ParsedFacts.type(FLOAT))).isEmpty();
    final SpanFacts folded =
        new SpanFacts(
            FLOAT,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new ConstantValue(2L),
            null);
    assertThat(run(SOURCE, folded)).isEmpty();
    assertThat(run(SOURCE, SpanFacts.UNKNOWN)).isEmpty();
    assertThat(new ParsedFacts(SOURCE).plain(CHECK)).isEmpty();
  }
}
