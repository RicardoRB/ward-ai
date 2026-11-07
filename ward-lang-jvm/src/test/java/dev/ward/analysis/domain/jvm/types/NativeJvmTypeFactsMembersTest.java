package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FieldRef;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NativeJvmTypeFactsMembersTest {

  private static final String SAFE_VARARGS = "java.lang.SafeVarargs";
  private static final String FUNCTIONAL = "java.lang.FunctionalInterface";

  private static String body(final String statements) {
    return "package app;\nimport java.util.*;\npublic class App implements Runnable {\n"
        + "  String name = \"n\";\n  Runnable task;\n"
        + "  public void run() {}\n"
        + "  void go() {\n"
        + statements
        + "\n  }\n}\n";
  }

  @Test
  void aCallTargetCarriesTheAnnotationsOfTheMethodFoundInTheJdk() {
    final Probe probe = Probe.of(body("    var a = Arrays.asList(\"a\");"));

    final CallTarget target = probe.call("Arrays.asList(\"a\")").orElseThrow();

    assertThat(target.annotations()).contains(SAFE_VARARGS);
    assertThat(target.parameterAnnotations()).allSatisfy(each -> assertThat(each).isEmpty());
  }

  @Test
  void aCallTargetCarriesTheAnnotationsOfItsDeclaringType() {
    final Probe probe = Probe.of(body("    task.run();"));

    assertThat(probe.call("task.run()").orElseThrow().declaringAnnotations()).contains(FUNCTIONAL);
  }

  @Test
  void theOverriddenMethodsCarryTheirAnnotationsToo() {
    final Probe probe = Probe.of(body(""));
    final Span span = probe.span("public void run() {}");

    final List<CallTarget> overridden = probe.at(span).at(span).overrides();

    assertThat(overridden)
        .singleElement()
        .satisfies(
            target -> {
              assertThat(target.declaringType().name()).isEqualTo("java.lang.Runnable");
              assertThat(target.declaringAnnotations()).contains(FUNCTIONAL);
            });
  }

  @Test
  void theMembersOfAJdkTypeIncludeItsMethodsFieldsAndEverySupertype() {
    final Probe probe = Probe.of(body("    var n = name;"));

    final TypeMembers members =
        probe.at(probe.span("name")).membersOf(probe.span("name")).orElseThrow();

    assertThat(members.methods())
        .anyMatch(method -> "length".equals(method.name()) && !method.isStatic());
    assertThat(members.fields())
        .anyMatch(field -> "CASE_INSENSITIVE_ORDER".equals(field.name()) && field.isStatic());
    assertThat(members.ancestors())
        .extracting(TypeRef::name)
        .contains("java.lang.Object", "java.lang.CharSequence", "java.lang.Comparable");
    assertThat(members.ancestors()).extracting(TypeRef::name).doesNotHaveDuplicates();
  }

  @Test
  void theMembersOfAProjectClassCarryTheirAnnotations() {
    final Probe probe =
        new Probe(
            body("    app.Helper h = null;\n    var n = h;"),
            Map.of(
                "app.Helper",
                "package app;\n@Deprecated\npublic class Helper {\n  public static final int SIZE = 3;\n  @Deprecated public void old() {}\n}\n"));

    final TypeMembers members = probe.at(probe.span("h")).membersOf(probe.span("h")).orElseThrow();

    assertThat(members.annotations()).containsExactly("java.lang.Deprecated");
    assertThat(members.fields()).extracting(FieldRef::name).containsExactly("SIZE");
    assertThat(members.methods())
        .filteredOn(method -> "old".equals(method.name()))
        .singleElement()
        .satisfies(
            method -> assertThat(method.annotations()).containsExactly("java.lang.Deprecated"));
  }

  @Test
  void aTypeNoLibraryOrSourceHoldsHasNoMembers() {
    final Probe probe = Probe.of(body("    missing.Lib lib = null;\n    var n = lib;"));

    assertThat(probe.at(probe.span("lib")).membersOf(probe.span("lib"))).isEmpty();
  }
}
