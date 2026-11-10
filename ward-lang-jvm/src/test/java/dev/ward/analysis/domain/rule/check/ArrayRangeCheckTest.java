package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArrayRangeCheckTest {

  private static List<String> run(final ArrayRangeCheck.Mode mode, final String body) {
    return new ParsedFacts("final class C {\n  void t(OutputStream out) {\n" + body + "  }\n}\n")
        .run(new ArrayRangeCheck(mode, "message"));
  }

  private static String indexed(final String index) {
    return "    int[] a = new int[3];\n    int x = a[" + index + "];\n";
  }

  private static String transfer(final String arguments) {
    return "    byte[] b = new byte[10];\n    out.write(b, " + arguments + ");\n";
  }

  @Test
  void anIndexOutsideALocalArrayIsReported() {
    assertThat(run(ArrayRangeCheck.Mode.INDEX, indexed("3"))).containsExactly("4:13");
    assertThat(run(ArrayRangeCheck.Mode.INDEX, indexed("-1"))).hasSize(1);
    assertThat(run(ArrayRangeCheck.Mode.INDEX, "    int[] a = {1, 2};\n    int x = a[2];\n"))
        .hasSize(1);
  }

  @Test
  void anIndexInsideTheArrayAnUnknownOneOrAReassignedArrayIsNotReported() {
    assertThat(run(ArrayRangeCheck.Mode.INDEX, indexed("2"))).isEmpty();
    assertThat(run(ArrayRangeCheck.Mode.INDEX, indexed("i"))).isEmpty();
    assertThat(run(ArrayRangeCheck.Mode.INDEX, indexed("99999999999"))).isEmpty();
    assertThat(run(ArrayRangeCheck.Mode.INDEX, indexed("3") + "    a = other;\n")).isEmpty();
    assertThat(run(ArrayRangeCheck.Mode.INDEX, "    int x = b[3];\n")).isEmpty();
    assertThat(run(ArrayRangeCheck.Mode.INDEX, "    int[] a = make();\n    int x = a[3];\n"))
        .isEmpty();
    assertThat(run(ArrayRangeCheck.Mode.INDEX, "    int x = 1;\n")).isEmpty();
  }

  @Test
  void anOffsetOutsideTheArrayIsReportedAsAnOffset() {
    assertThat(run(ArrayRangeCheck.Mode.OFFSET, transfer("11, 1"))).containsExactly("4:9");
    assertThat(run(ArrayRangeCheck.Mode.OFFSET, transfer("-1, 1"))).hasSize(1);
    assertThat(run(ArrayRangeCheck.Mode.LENGTH, transfer("11, 1"))).isEmpty();
    assertThat(run(ArrayRangeCheck.Mode.OFFSET, transfer("2, 8"))).isEmpty();
  }

  @Test
  void aLengthThatDoesNotFitIsReportedAsALength() {
    assertThat(run(ArrayRangeCheck.Mode.LENGTH, transfer("2, 10"))).containsExactly("4:9");
    assertThat(run(ArrayRangeCheck.Mode.LENGTH, transfer("0, -1"))).hasSize(1);
    assertThat(run(ArrayRangeCheck.Mode.LENGTH, transfer("2, 8"))).isEmpty();
    assertThat(run(ArrayRangeCheck.Mode.LENGTH, transfer("n, 8"))).isEmpty();
    assertThat(
            run(
                ArrayRangeCheck.Mode.LENGTH,
                "    byte[] b = new byte[10];\n    out.close(b, 0, 99);\n"))
        .isEmpty();
    assertThat(
            run(
                ArrayRangeCheck.Mode.LENGTH,
                "    byte[] b = new byte[10];\n    out.write(b, 0);\n"))
        .isEmpty();
    assertThat(run(ArrayRangeCheck.Mode.LENGTH, "    out.write(c, 0, 99);\n")).isEmpty();
  }
}
