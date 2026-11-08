package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CastedArgumentCheckTest {

  private static final CastedArgumentCheck CHECK =
      new CastedArgumentCheck("Math", "round", "float", Set.of("int", "long"), "message");
  private static final String SOURCE =
      "final class C {\n  int t(int n) {\n    return Math.round((float) n);\n  }\n}\n";

  private static List<String> run(final String source, final String operand) {
    return new ParsedFacts(source)
        .at("expression", "n", ParsedFacts.type(new TypeRef(operand, List.of(), 0, true)))
        .run(CHECK);
  }

  @Test
  void anIntegralValueCastToFloatAndRoundedIsReported() {
    assertThat(run(SOURCE, "int")).containsExactly("3:12");
    assertThat(run(SOURCE.replace("(float) n", "(float) n"), "long")).hasSize(1);
  }

  @Test
  void otherOperandsCastsMethodsReceiversAndUnknownFactsAreNotReported() {
    assertThat(run(SOURCE, "double")).isEmpty();
    assertThat(run(SOURCE.replace("(float)", "(double)"), "int")).isEmpty();
    assertThat(run(SOURCE.replace("round", "floor"), "int")).isEmpty();
    assertThat(run(SOURCE.replace("Math", "Other"), "int")).isEmpty();
    assertThat(run(SOURCE.replace("(float) n", "n"), "int")).isEmpty();
    assertThat(run(SOURCE.replace("(float) n", "(float) n, 1"), "int")).isEmpty();
    assertThat(new ParsedFacts(SOURCE).run(CHECK)).isEmpty();
    assertThat(new ParsedFacts(SOURCE).plain(CHECK)).isEmpty();
  }
}
