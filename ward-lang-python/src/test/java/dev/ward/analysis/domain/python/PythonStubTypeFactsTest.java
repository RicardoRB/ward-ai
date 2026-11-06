package dev.ward.analysis.domain.python;

import static dev.ward.analysis.domain.python.PythonTypeFactsFixtures.facts;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.FactKind;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests the literal types and the unknown answers of the Python type facts. */
class PythonStubTypeFactsTest {

  private static final Set<FactKind> TYPE = Set.of(FactKind.TYPE);

  @Test
  void numbersAndStringsHaveTheirBuiltinTypes() {
    assertThat(type("x = 1\n", "1")).isEqualTo("int");
    assertThat(type("x = 1.5\n", "1.5")).isEqualTo("float");
    assertThat(type("x = 2j\n", "2j")).isEqualTo("complex");
    assertThat(type("x = 'a'\n", "'a'")).isEqualTo("str");
    assertThat(type("x = b'a'\n", "b'a'")).isEqualTo("bytes");
    assertThat(type("x = True\n", "True")).isEqualTo("bool");
  }

  @Test
  void displaysHaveTheirBuiltinTypes() {
    assertThat(type("x = [1]\n", "[1]")).isEqualTo("list");
    assertThat(type("x = {}\n", "{}")).isEqualTo("dict");
    assertThat(type("x = {1}\n", "{1}")).isEqualTo("set");
    assertThat(type("x = (1, 2)\n", "(1, 2)")).isEqualTo("tuple");
  }

  @Test
  void unknownKindIsNotAnswered() {
    assertThat(facts("x = 1\n", "1", Set.of(FactKind.SHAPE)).type()).isNull();
  }

  private static String type(final String source, final String snippet) {
    return facts(source, snippet, TYPE).type().name();
  }
}
