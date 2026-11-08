package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class UncallableAnonymousMethodCheckTest {

  private static final UncallableAnonymousMethodCheck CHECK =
      new UncallableAnonymousMethodCheck("message");
  private static final TypeRef RUNNABLE = TypeRef.of("java.lang.Runnable");
  private static final String SOURCE =
      "class C {\n  Object t() {\n    return new Runnable() {\n      public void run() {}\n"
          + "      void extra(int x) {}\n    };\n  }\n}\n";

  private static CallTarget run() {
    return ParsedFacts.method("java.lang.Runnable", "run", List.of());
  }

  private static SpanFacts supertype(final TypeRef type, final List<CallTarget> inherited) {
    return new SpanFacts(
        type,
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        ParsedFacts.withMethods(List.of(run()), inherited));
  }

  private static List<String> report(final String source, final SpanFacts facts) {
    return new ParsedFacts(source).at("createdName", "Runnable", facts).run(CHECK);
  }

  @Test
  void aMethodThatOverridesNothingAndIsNeverCalledIsReported() {
    assertThat(CHECK.needs().facts()).containsExactlyInAnyOrder(FactKind.TYPE, FactKind.MEMBERS);
    assertThat(report(SOURCE, supertype(RUNNABLE, List.of()))).containsExactly("5:7");
  }

  @Test
  void aMethodThatIsCalledReferencedOverriddenOrSpecialIsNotReported() {
    final SpanFacts facts = supertype(RUNNABLE, List.of());
    assertThat(report(SOURCE.replace("run() {}", "run() { extra(1); }"), facts)).isEmpty();
    assertThat(report(SOURCE.replace("run() {}", "run() { f(this::extra); }"), facts)).isEmpty();
    assertThat(report(SOURCE.replace("extra(int x)", "extra()"), facts)).hasSize(1);
    assertThat(report(SOURCE.replace("void extra", "private void extra"), facts)).isEmpty();
    assertThat(report(SOURCE.replace("void extra", "@Inject void extra"), facts)).isEmpty();
    assertThat(report(SOURCE.replace("void extra(int x)", "void x(int y)"), facts)).isEmpty();
    assertThat(report(SOURCE.replace("void extra(int x)", "void a$b(int y)"), facts)).isEmpty();
    assertThat(report(SOURCE.replace("void extra(int x)", "Object readResolve()"), facts))
        .isEmpty();
    assertThat(report(SOURCE.replace("void extra(int x)", "public String toString()"), facts))
        .isEmpty();
    final CallTarget inherited = ParsedFacts.method("a.Base", "extra", List.of(TypeRef.of("int")));
    assertThat(report(SOURCE, supertype(RUNNABLE, List.of(inherited)))).isEmpty();
    assertThat(
            report(
                SOURCE.replace("void extra", "static void extra"),
                supertype(RUNNABLE, List.of(inherited))))
        .hasSize(1);
  }

  @Test
  void unknownMembersAndPlainObjectAreNotReported() {
    assertThat(report(SOURCE, SpanFacts.UNKNOWN)).isEmpty();
    final SpanFacts onlyType = ParsedFacts.type(RUNNABLE);
    assertThat(report(SOURCE, onlyType)).isEmpty();
    assertThat(report(SOURCE, supertype(TypeRef.of("java.lang.Object"), List.of()))).isEmpty();
    assertThat(new ParsedFacts(SOURCE).plain(CHECK)).isEmpty();
    final TypeMembers none = ParsedFacts.withMethods(List.of(), List.of());
    assertThat(none.methods()).isEmpty();
  }
}
