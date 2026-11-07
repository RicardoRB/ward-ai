package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeFromIntCheckTest {

  private static final TimeFromIntCheck CHECK = new TimeFromIntCheck("message");
  private static final String DATE =
      "final class C {\n  Object t(int s) {\n    return new Date(s * 1000);\n  }\n}\n";
  private static final String INSTANT = DATE.replace("new Date(", "Instant.ofEpochMilli(");

  private static List<String> run(final String source, final String type) {
    return new ParsedFacts(source)
        .at("expression", "s*1000", ParsedFacts.type(new TypeRef(type, List.of(), 0, true)))
        .run(CHECK);
  }

  @Test
  void anIntProductGivenToATimeIsReported() {
    assertThat(run(DATE, "int")).containsExactly("3:12");
    assertThat(run(INSTANT, "int")).hasSize(1);
    assertThat(run(DATE.replace("new Date(s * 1000)", "date.setTime(s * 1000)"), "int")).hasSize(1);
  }

  @Test
  void aLongProductAnotherShapeOrAnotherClassIsNotReported() {
    assertThat(run(DATE, "long")).isEmpty();
    assertThat(run(DATE.replace("*", "+"), "int")).isEmpty();
    assertThat(run(DATE.replace("new Date(", "new SqlDate("), "int")).isEmpty();
    assertThat(run(DATE.replace("new Date(s * 1000)", "new Date(s, 1)"), "int")).isEmpty();
    assertThat(run(INSTANT.replace("ofEpochMilli", "now"), "int")).isEmpty();
    assertThat(new ParsedFacts(DATE).run(CHECK)).isEmpty();
    assertThat(new ParsedFacts(DATE).plain(CHECK)).isEmpty();
  }
}
