package dev.ward.analysis.domain.node.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FieldRef;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import org.junit.jupiter.api.Test;

/** Checks that a class declares its members once per unit and that subclasses still inherit. */
class NativeNodeMemberCacheTest {
  private static final String UNIT =
      """
      declare class Base { run(): string; }
      declare class Svc extends Base { size: number; }
      declare class Sub extends Svc {}
      declare const firstSvc: Svc;
      declare const secondSvc: Svc;
      declare const sub: Sub;
      use(firstSvc);
      use(secondSvc);
      use(sub);
      """;

  @Test
  void twoExpressionsOfOneClassShareTheirMembersAndSubclassesKeepInherited() {
    final UnitTypeFacts facts =
        TypeScenarios.project(UNIT).answer(TypeScenarios.UNIT, "firstSvc", "secondSvc", "sub");
    final String text = UNIT;
    final TypeMembers first = members(facts, text, "firstSvc");
    final TypeMembers second = members(facts, text, "secondSvc");
    final TypeMembers sub = members(facts, text, "sub");

    assertThat(first).isNotNull().isSameAs(second);
    assertThat(sub).isNotSameAs(first);
    assertThat(sub.inheritedMethods()).extracting(CallTarget::name).contains("run");
    assertThat(sub.inheritedFields()).extracting(FieldRef::name).contains("size");
  }

  private static TypeMembers members(
      final UnitTypeFacts facts, final String text, final String token) {
    return facts.membersOf(TypeProject.span(text, token)).orElse(null);
  }
}
