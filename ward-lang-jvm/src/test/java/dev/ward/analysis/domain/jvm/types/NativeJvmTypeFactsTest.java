package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NativeJvmTypeFactsTest {

  private static String body(final String statements) {
    return "package app;\nimport java.util.*;\nimport java.util.function.*;\npublic class App {\n"
        + "  String name = \"n\";\n  static final int PORT = 8080;\n  int[] numbers = new int[2];\n"
        + "  void run(List<String> items, Map<String, Integer> counts, Object any) {\n"
        + statements
        + "\n  }\n}\n";
  }

  @Test
  void typesLiteralsLocalsAndFields() {
    final Probe probe =
        Probe.of(
            body(
                "    String a = name;\n    int b = PORT;\n    long c = numbers.length + 1L;\n    var d = \"x\" + b;\n    double e = 1.5 * b;"));

    assertThat(probe.type("name")).isEqualTo("java.lang.String");
    assertThat(probe.type("PORT")).isEqualTo("int");
    assertThat(probe.type("numbers.length")).isEqualTo("int");
    assertThat(probe.type("numbers.length + 1L")).isEqualTo("long");
    assertThat(probe.type("\"x\" + b")).isEqualTo("java.lang.String");
    assertThat(probe.type("1.5 * b")).isEqualTo("double");
  }

  @Test
  void resolvesALibraryCallTarget() {
    final Probe probe = Probe.of(body("    boolean same = Objects.equals(name, any);"));

    final CallTarget target = probe.call("Objects.equals(name, any)").orElseThrow();
    assertThat(target.declaringType().name()).isEqualTo("java.util.Objects");
    assertThat(target.name()).isEqualTo("equals");
    assertThat(target.parameters())
        .extracting(TypeRef::name)
        .containsExactly("java.lang.Object", "java.lang.Object");
    assertThat(target.returnType().name()).isEqualTo("boolean");
    assertThat(target.isStatic()).isTrue();
    assertThat(probe.type("Objects.equals(name, any)")).isEqualTo("boolean");
  }

  @Test
  void choosesOverloadsByPhase() {
    final Probe probe =
        Probe.of(
            body(
                "    var a = Math.max(1, 2L);\n    var b = Math.abs(1.5f);\n    var c = String.valueOf(any);\n    var d = Arrays.asList(1, 2);\n    var e = List.of(\"a\");"));

    assertThat(probe.call("Math.max(1, 2L)").orElseThrow().parameters())
        .extracting(TypeRef::name)
        .containsExactly("long", "long");
    assertThat(probe.type("Math.abs(1.5f)")).isEqualTo("float");
    assertThat(probe.call("String.valueOf(any)").orElseThrow().parameters())
        .extracting(TypeRef::name)
        .containsExactly("java.lang.Object");
    assertThat(probe.type("Arrays.asList(1, 2)")).isEqualTo("java.util.List<java.lang.Integer>");
    assertThat(probe.type("List.of(\"a\")")).isEqualTo("java.util.List<java.lang.String>");
  }

  @Test
  void substitutesGenericsAndInfersFromLambdas() {
    final Probe probe =
        Probe.of(
            body(
                "    var a = items.get(0);\n    var b = counts.get(\"k\");\n    var c = items.stream().map(s -> s.length()).toList();\n    for (String s : items) { var l = s.length(); }\n    var d = counts.entrySet().iterator().next().getValue();"));

    assertThat(probe.type("items.get(0)")).isEqualTo("java.lang.String");
    assertThat(probe.type("counts.get(\"k\")")).isEqualTo("java.lang.Integer");
    assertThat(probe.type("items.stream().map(s -> s.length()).toList()"))
        .isEqualTo("java.util.List<java.lang.Integer>");
    assertThat(probe.type("s.length()")).isEqualTo("int");
    assertThat(probe.type("counts.entrySet().iterator().next().getValue()"))
        .isEqualTo("java.lang.Integer");
  }

  @Test
  void typesCreationCastsTernariesAndPatterns() {
    final Probe probe =
        Probe.of(
            body(
                "    List<String> a = new ArrayList<>();\n    var b = new HashMap<String, Integer>();\n    String c = (String) any;\n    var d = any != null ? name : \"x\";\n    if (any instanceof String s) { var n = s.trim(); }\n    int[] e = new int[3];"));

    assertThat(probe.type("new ArrayList<>()")).isEqualTo("java.util.ArrayList<java.lang.String>");
    assertThat(probe.call("new ArrayList<>()")).isPresent();
    assertThat(probe.type("new HashMap<String, Integer>()"))
        .isEqualTo("java.util.HashMap<java.lang.String, java.lang.Integer>");
    assertThat(probe.type("(String) any")).isEqualTo("java.lang.String");
    assertThat(probe.type("any != null ? name : \"x\"")).isEqualTo("java.lang.String");
    assertThat(probe.type("s.trim()")).isEqualTo("java.lang.String");
    assertThat(probe.type("new int[3]")).isEqualTo("int[]");
  }

  @Test
  void usesProjectSourcesAndLeavesTheUnknownUnknown() {
    final Probe probe =
        new Probe(
            "package app;\npublic class App {\n  void run(Helper h) {\n    var a = h.size();\n    var b = Helper.make();\n    var c = missing.call();\n    Gone g = null;\n    var d = g.value();\n  }\n}\n",
            Map.of(
                "app.Helper",
                "package app;\npublic class Helper {\n  public int size() { return 1; }\n  public static Helper make() { return null; }\n}\n"));

    assertThat(probe.type("h.size()")).isEqualTo("int");
    assertThat(probe.type("Helper.make()")).isEqualTo("app.Helper");
    assertThat(probe.type("missing.call()")).isEqualTo("?");
    assertThat(probe.type("g.value()")).isEqualTo("?");
  }

  @Test
  void answersTheVisibleMembersATypeInherits() {
    final String base =
        "package app;\npublic class Base implements Runnable {\n  protected int count;\n  private int hidden;\n  public static String NAME = \"b\";\n  public void run() {}\n  static void util() {}\n  private void secret() {}\n}\n";
    final String text = "package app;\npublic class App extends Base {\n  int own;\n}\n";
    final Probe probe = new Probe(text, Map.of("app.Base", base));

    final String klass =
        text.substring(text.indexOf("public class App"), text.lastIndexOf('}') + 1);
    final TypeMembers members =
        probe.at(probe.span(klass)).membersOf(probe.span(klass)).orElseThrow();

    assertThat(members.inheritedFields())
        .extracting("name")
        .containsExactlyInAnyOrder("count", "NAME");
    assertThat(members.inheritedMethods())
        .extracting(CallTarget::name)
        .contains("run", "util", "hashCode", "toString")
        .doesNotContain("secret", "<init>");
    assertThat(members.inheritedMethods())
        .filteredOn(method -> "util".equals(method.name()))
        .singleElement()
        .satisfies(method -> assertThat(method.isStatic()).isTrue());
    assertThat(members.fields()).extracting("name").containsExactly("own");
  }

  @Test
  void answersShapesOverridesThrowsAndConstants() {
    final String text =
        "package app;\nimport java.io.IOException;\n@Deprecated\npublic class App implements Comparable<App> {\n  @Override public int compareTo(App other) { return 0; }\n  @Override public String toString() { return \"a\"; }\n  public void read() throws IOException {}\n  public static final String KIND = \"user\";\n}\n";
    final Probe probe = Probe.of(text);

    final String klass = text.substring(text.indexOf("@Deprecated"), text.lastIndexOf('}') + 1);
    final TypeShape shape = probe.at(probe.span(klass)).shapeOf(probe.span(klass)).orElseThrow();
    assertThat(shape.supertypes())
        .extracting(TypeRef::name)
        .containsExactly("java.lang.Object", "java.lang.Comparable");
    assertThat(probe.at(probe.span(klass)).at(probe.span(klass)).annotations())
        .containsExactly("java.lang.Deprecated");

    final String compare = "@Override public int compareTo(App other) { return 0; }";
    assertThat(probe.at(probe.span(compare)).at(probe.span(compare)).overrides())
        .extracting(CallTarget::name, t -> t.declaringType().name())
        .containsExactly(org.assertj.core.groups.Tuple.tuple("compareTo", "java.lang.Comparable"));
    final String toString = "@Override public String toString() { return \"a\"; }";
    assertThat(probe.at(probe.span(toString)).at(probe.span(toString)).overrides()).hasSize(1);
    final String read = "public void read() throws IOException {}";
    assertThat(probe.at(probe.span(read)).at(probe.span(read)).thrown())
        .extracting(TypeRef::name)
        .containsExactly("java.io.IOException");
    assertThat(probe.at(probe.span("\"user\"")).constant(probe.span("\"user\""))).isPresent();
  }
}
