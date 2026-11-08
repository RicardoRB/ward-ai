package dev.ward.analysis.domain.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypeMembersTest {

  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);

  @Test
  void theListsAreCopied() {
    final List<String> annotations = new ArrayList<>(List.of("a.A"));
    final TypeMembers members = new TypeMembers(List.of(), List.of(), List.of(), annotations);
    annotations.add("b.B");

    assertThat(members.annotations()).containsExactly("a.A");
    assertThat(new FieldRef("f", null, true, annotations).annotations()).hasSize(2);
  }

  @Test
  void aCallTargetWithEmptyAnnotationsHasNoneAndOneWithThemKeepsThem() {
    final CallTarget plain =
        new CallTarget(
            TypeRef.of("a.T"),
            "m",
            List.of(),
            null,
            false,
            false,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            Map.of(),
            Map.of(),
            null);
    final CallTarget annotated =
        new CallTarget(
            TypeRef.of("a.T"),
            "m",
            List.of(),
            null,
            false,
            false,
            List.of("a.A"),
            List.of(List.of("a.N")),
            List.of("a.D"),
            List.of(),
            List.of(),
            List.of(),
            null,
            Map.of(),
            Map.of(),
            null);

    assertThat(plain.annotations()).isEmpty();
    assertThat(plain.parameterAnnotations()).isEmpty();
    assertThat(plain.declaringAnnotations()).isEmpty();
    assertThat(annotated.parameterAnnotations()).containsExactly(List.of("a.N"));
    assertThat(annotated.declaringAnnotations()).containsExactly("a.D");
  }

  @Test
  void membersAreReadFromTheUnitFactsAndAreUnknownWhenAbsent() {
    final TypeMembers members =
        new TypeMembers(List.of(), List.of(), List.of(TypeRef.of("java.lang.Object")), List.of());
    final SpanFacts known =
        new SpanFacts(null, null, null, List.of(), List.of(), List.of(), List.of(), null, members);
    final UnitTypeFacts facts = new UnitTypeFacts(Map.of(UnitTypeFacts.key(SPAN), known));

    assertThat(facts.membersOf(SPAN)).contains(members);
    assertThat(UnitTypeFacts.none().membersOf(SPAN)).isEmpty();
    assertThat(SpanFacts.UNKNOWN.members()).isNull();
  }
}
