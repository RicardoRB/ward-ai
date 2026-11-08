package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class ByteAddCheckTest {

  private static final ByteAddCheck CHECK = new ByteAddCheck("message");
  private static final TypeRef BYTE = new TypeRef("byte", List.of(), 0, true);
  private static final String SOURCE =
      "final class C {\n  int t(int high, byte low) {\n    return (high << 8) + low;\n  }\n}\n";

  private static List<String> run(final String source, final TypeRef low) {
    return new ParsedFacts(source).at("expression", "low", ParsedFacts.type(low)).run(CHECK);
  }

  @Test
  void aByteAddedToAValueShiftedByAByteOrMoreIsReportedInEitherOrder() {
    assertThat(CHECK.needs().facts()).containsExactly(FactKind.TYPE);
    assertThat(run(SOURCE, BYTE)).containsExactly("3:12");
    assertThat(run(SOURCE.replace("(high << 8) + low", "low + (high << 16)"), BYTE)).hasSize(1);
  }

  @Test
  void aSmallShiftAnotherTypeAnotherOperatorOrUnknownFactsAreNotReported() {
    assertThat(run(SOURCE.replace("<< 8", "<< 4"), BYTE)).isEmpty();
    assertThat(run(SOURCE, new TypeRef("int", List.of(), 0, true))).isEmpty();
    assertThat(run(SOURCE, new TypeRef("byte", List.of(), 1, true))).isEmpty();
    assertThat(run(SOURCE.replace(") + low", ") - low"), BYTE)).isEmpty();
    assertThat(run(SOURCE.replace("(high << 8) + low", "high + low"), BYTE)).isEmpty();
    assertThat(new ParsedFacts(SOURCE).run(CHECK)).isEmpty();
    assertThat(new ParsedFacts(SOURCE).plain(CHECK)).isEmpty();
  }
}
