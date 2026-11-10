package dev.ward.analysis.domain.node.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FieldRef;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the methods, fields and enum shapes that the native resolver answers for expressions. */
class NativeNodeMembersTest {

  private static final String UNIT = TypeScenarios.UNIT;

  @Test
  void methodsAndFieldsAreSplitAndSupertypeMembersAreInherited() {
    final String unit =
        """
        interface Named { name: string; describe(): string; }
        interface Shape extends Named { area(): number; }
        declare const box: Shape;
        declare const lit: { size: number; grow(): void };
        const copy = box;
        const other = lit;
        """;
    final TypeProject project = TypeScenarios.project(unit);
    final UnitTypeFacts facts = project.answer(UNIT, "box", "lit");

    final TypeMembers shape = facts.membersOf(TypeProject.span(unit, "box")).orElseThrow();
    assertThat(names(shape.methods())).containsExactly("area");
    assertThat(shape.fields()).isEmpty();
    assertThat(names(shape.inheritedMethods())).containsExactly("describe");
    assertThat(fieldNames(shape.inheritedFields())).containsExactly("name");

    final TypeMembers object = facts.membersOf(TypeProject.span(unit, "lit")).orElseThrow();
    assertThat(names(object.methods())).containsExactly("grow");
    assertThat(fieldNames(object.fields())).containsExactly("size");
    assertThat(object.inheritedMethods()).isEmpty();
  }

  @Test
  void aClassDeclaresItsOwnToStringAndItsCallTargetOwner() {
    final String unit =
        """
        class Point { x = 1; toString(): string { return "p"; } }
        declare const point: Point;
        const copy = point;
        """;
    final TypeProject project = TypeScenarios.project(unit);
    final UnitTypeFacts facts = project.answer(UNIT, "point");

    final TypeMembers members = facts.membersOf(TypeProject.span(unit, "point")).orElseThrow();
    assertThat(names(members.methods())).containsExactly("toString");
    assertThat(members.methods().get(0).declaringType().name()).isEqualTo("Point");
    assertThat(fieldNames(members.fields())).containsExactly("x");
  }

  @Test
  void aTypeWithoutAKnownDeclarationHasNoMembers() {
    final String unit = "declare const mystery: Missing;\n";
    final UnitTypeFacts facts = TypeScenarios.project(unit).answer(UNIT, "mystery");

    assertThat(facts.membersOf(TypeProject.span(unit, "mystery"))).isEmpty();
  }

  @Test
  void theShapeOfAnEnumTypedValueIsAnEnum() {
    final String unit =
        "enum Color { Red, Green }\ndeclare const color: Color;\nconst copy = color;\n";
    final UnitTypeFacts facts = TypeScenarios.project(unit).answer(UNIT, "color");

    assertThat(facts.shapeOf(TypeProject.span(unit, "color")))
        .map(TypeShape::kind)
        .contains(TypeShape.Kind.ENUM);
  }

  @Test
  void aToStringDeclaredByAnAncestorIsInheritedAndAnUnresolvedBaseIsUnknown() {
    final String unit =
        """
        class Base { toString(): string { return "b"; } }
        class Child extends Base { x = 1; }
        class Orphan extends Missing {}
        declare const child: Child;
        declare const orphan: Orphan;
        const copy = child;
        const other = orphan;
        """;
    final UnitTypeFacts facts = TypeScenarios.project(unit).answer(UNIT, "child", "orphan");

    final TypeMembers child = facts.membersOf(TypeProject.span(unit, "child")).orElseThrow();
    assertThat(names(child.inheritedMethods())).containsExactly("toString");
    assertThat(facts.membersOf(TypeProject.span(unit, "orphan"))).isEmpty();
  }

  private static List<String> names(final List<CallTarget> methods) {
    return methods.stream().map(CallTarget::name).toList();
  }

  private static List<String> fieldNames(final List<FieldRef> fields) {
    return fields.stream().map(FieldRef::name).toList();
  }
}
