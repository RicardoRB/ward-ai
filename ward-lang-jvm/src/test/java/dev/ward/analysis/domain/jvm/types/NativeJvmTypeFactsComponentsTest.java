package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.TypeComponent;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import org.junit.jupiter.api.Test;

class NativeJvmTypeFactsComponentsTest {

  private static String body(final String statements) {
    return "package app;\nimport java.util.*;\npublic class App {\n  void go() {\n"
        + statements
        + "\n  }\n}\n";
  }

  @Test
  void theMembersOfAParameterizedTypeCarryTheMembersOfItsTypeArguments() {
    final Probe probe = Probe.of(body("    List<String> names = null;\n    var n = names;"));

    final TypeMembers members =
        probe.at(probe.span("names")).membersOf(probe.span("names")).orElseThrow();

    assertThat(members.components()).hasSize(1);
    final TypeComponent element = members.components().get(0);
    assertThat(element.known()).isTrue();
    assertThat(element.type().name()).isEqualTo("java.lang.String");
    assertThat(element.shape().isFinal()).isTrue();
    assertThat(element.members().ancestors())
        .extracting(TypeRef::name)
        .contains("java.lang.CharSequence", "java.lang.Object");
  }

  @Test
  void theMembersOfAnArrayTypeCarryTheMembersOfItsElementType() {
    final Probe probe = Probe.of(body("    Integer[] numbers = null;\n    var n = numbers;"));

    final TypeMembers members =
        probe.at(probe.span("numbers")).membersOf(probe.span("numbers")).orElseThrow();

    assertThat(members.components())
        .singleElement()
        .satisfies(
            element -> {
              assertThat(element.type().name()).isEqualTo("java.lang.Integer");
              assertThat(element.members().ancestors())
                  .extracting(TypeRef::name)
                  .contains("java.lang.Number");
            });
  }

  @Test
  void aTypeArgumentThatIsNotAClassIsAnUnknownComponent() {
    final Probe probe = Probe.of(body("    List<int[]> arrays = null;\n    var n = arrays;"));

    final TypeMembers members =
        probe.at(probe.span("arrays")).membersOf(probe.span("arrays")).orElseThrow();

    assertThat(members.components()).containsExactly(TypeComponent.UNKNOWN);
  }
}
