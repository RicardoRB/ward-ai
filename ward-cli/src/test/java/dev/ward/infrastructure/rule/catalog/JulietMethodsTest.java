package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.rule.catalog.JulietMethods.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the method mapping on a two-case sample written by hand in the shape of Juliet. */
class JulietMethodsTest {

  private static final String SAMPLE =
      """
      public class Sample_01 extends AbstractTestCase {
          public void bad() throws Throwable {
              String data = request.getParameter("a");
              statement.execute(data);
          }

          private void goodG2B() throws Throwable {
              String data = "fixed";
              statement.execute(data);
          }

          public void good() throws Throwable {
              goodG2B();
          }

          public static void main(String[] args) {
              run();
          }
      }
      """;

  @Test
  void aLineTakesTheKindOfTheMemberThatHoldsIt() {
    final List<Kind> kinds = JulietMethods.kinds(SAMPLE);

    assertThat(kinds.get(2)).isEqualTo(Kind.BAD);
    assertThat(kinds.get(3)).isEqualTo(Kind.BAD);
    assertThat(kinds.get(7)).isEqualTo(Kind.GOOD);
    assertThat(kinds.get(12)).isEqualTo(Kind.GOOD);
    assertThat(kinds.get(16)).isEqualTo(Kind.OTHER);
  }

  @Test
  void aCaseWithABadMemberIsAPositive() {
    assertThat(JulietMethods.hasBad(SAMPLE)).isTrue();
    assertThat(JulietMethods.hasBad("class Helper {\n    public void run() {}\n}\n")).isFalse();
  }
}
