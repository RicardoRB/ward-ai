package dev.ward.analysis.domain.node.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import org.junit.jupiter.api.Test;

/** Checks the facts the native resolver computes for expressions and declarations of a unit. */
class NativeNodeExpressionFactsTest {
  private static final String UNIT = TypeScenarios.UNIT;
  private static final String NUMBER = "number";

  @Test
  void typesVariablesCallsAndMembersFromDeclarationsAndInitializers() {
    final String unit =
        """
        interface Config { port: number; name?: string }
        const config: Config = { port: 1 };
        const port = config.port;
        let count = 1;
        const label = "a";
        const names = ["a", "b"];
        const first = names[0];
        const lengths = names.map((n) => n.length);
        const obj = { x: 1, y: "s" };
        const sum = count + 2;
        const text = label + count;
        const same = count === 2;
        const big = Math.max(1, 2);
        function twice(x: number) { return x * 2; }
        const t = twice(3);
        const arrow = (n: number) => n + 1;
        const a = arrow(2);
        function id<T>(v: T): T { return v; }
        const i = id(5);
        """;
    final TypeProject project = TypeScenarios.project(unit);

    assertThat(TypeScenarios.type(project, "config.port")).isEqualTo(NUMBER);
    assertThat(TypeScenarios.type(project, "config")).isEqualTo("Config");
    assertThat(TypeScenarios.type(project, "count")).isEqualTo(NUMBER);
    assertThat(TypeScenarios.type(project, "label")).isEqualTo("\"a\"");
    assertThat(TypeScenarios.type(project, "names")).isEqualTo("string[]");
    assertThat(TypeScenarios.type(project, "names[0]")).isEqualTo("string");
    assertThat(TypeScenarios.type(project, "names.map((n) => n.length)")).isEqualTo("number[]");
    assertThat(TypeScenarios.type(project, "{ x: 1, y: \"s\" }"))
        .isEqualTo("{ x: number; y: string; }");
    assertThat(TypeScenarios.type(project, "count + 2")).isEqualTo(NUMBER);
    assertThat(TypeScenarios.type(project, "label + count")).isEqualTo("string");
    assertThat(TypeScenarios.type(project, "count === 2")).isEqualTo("boolean");
    assertThat(TypeScenarios.type(project, "Math.max(1, 2)")).isEqualTo(NUMBER);
    assertThat(TypeScenarios.type(project, "twice(3)")).isEqualTo(NUMBER);
    assertThat(TypeScenarios.type(project, "arrow(2)")).isEqualTo(NUMBER);
    assertThat(TypeScenarios.type(project, "id(5)")).isEqualTo(NUMBER);
  }

  @Test
  void leavesWhatNeedsFullCheckingUnknown() {
    final String unit =
        """
        import { gone } from "missing";
        import { cond } from "./cond";
        const a = gone();
        const b = cond;
        const c = nope.value;
        const d = (window as any).anything;
        const e = JSON.parse("1");
        """;
    final TypeProject project =
        TypeScenarios.project(unit)
            .file("src/cond.d.ts", "export declare const cond: T extends string ? 1 : 2;\n");

    assertThat(TypeScenarios.type(project, "gone()")).isEqualTo("-");
    assertThat(TypeScenarios.type(project, "b")).isEqualTo("-");
    assertThat(TypeScenarios.type(project, "nope.value")).isEqualTo("-");
    assertThat(TypeScenarios.type(project, "(window as any).anything")).isEqualTo("-");
    assertThat(TypeScenarios.type(project, "JSON.parse(\"1\")")).isEqualTo("-");
  }

  @Test
  void libraryTypesAreUnknownWithoutTheTypeScriptPackage() {
    final String unit = "const a = Math.max(1, 2);\nconst b = [1].length;\n";
    final TypeProject project = new TypeProject().file(UNIT, unit);

    assertThat(TypeScenarios.type(project, "Math.max(1, 2)")).isEqualTo("-");
    assertThat(TypeScenarios.type(project, "[1].length")).isEqualTo("-");
  }

  @Test
  void shapesAndOverridesFollowDeclaredHeritage() {
    final String unit =
        """
        interface Named { name: string }
        abstract class Base { abstract run(): number; }
        class Impl extends Base implements Named {
          name = "x";
          run(): number { return 1; }
        }
        """;
    final TypeProject project = TypeScenarios.project(unit);
    final String base =
        unit.substring(unit.indexOf("abstract class Base"), unit.indexOf("class Impl") - 1);
    final String impl = unit.substring(unit.indexOf("class Impl"), unit.lastIndexOf('}') + 1);
    final String run = "run(): number { return 1; }";

    final UnitTypeFacts facts =
        project.answer(UNIT, base, impl, run, "interface Named { name: string }");

    final TypeShape baseShape = facts.shapeOf(TypeProject.span(unit, base)).orElseThrow();
    assertThat(baseShape.kind()).isEqualTo(TypeShape.Kind.CLASS);
    assertThat(baseShape.isAbstract()).isTrue();
    final TypeShape implShape = facts.shapeOf(TypeProject.span(unit, impl)).orElseThrow();
    assertThat(implShape.supertypes())
        .extracting(TypeRef::display)
        .containsExactly("Base", "Named");
    assertThat(
            facts
                .shapeOf(TypeProject.span(unit, "interface Named { name: string }"))
                .orElseThrow()
                .kind())
        .isEqualTo(TypeShape.Kind.INTERFACE);
    assertThat(facts.at(TypeProject.span(unit, run)).overrides())
        .extracting(target -> target.declaringType().name() + "." + target.name())
        .containsExactly("Base.run");
  }

  @Test
  void readsDecoratorsAndConstants() {
    final String unit =
        "@Injectable()\nclass Service {\n  @Input() value = 1;\n}\nconst limit = 10;\nconst name = \"svc\";\nconst ratio = 1.5;\nconst on = true;\n";
    final TypeProject project = TypeScenarios.project(unit);
    final String cls = unit.substring(0, unit.indexOf("const limit") - 1);
    final UnitTypeFacts facts = project.answer(UNIT, cls, "10", "\"svc\"", "1.5", "true");

    assertThat(facts.at(TypeProject.span(unit, cls)).annotations()).containsExactly("Injectable");
    assertThat(facts.constant(TypeProject.span(unit, "10")).orElseThrow().value()).isEqualTo(10L);
    assertThat(facts.constant(TypeProject.span(unit, "\"svc\"")).orElseThrow().value())
        .isEqualTo("svc");
    assertThat(facts.constant(TypeProject.span(unit, "1.5")).orElseThrow().value()).isEqualTo(1.5);
    assertThat(facts.constant(TypeProject.span(unit, "true")).orElseThrow().value())
        .isEqualTo(true);
  }

  @Test
  void answersJavaScriptUnitsFromInitializers() {
    final String unit =
        "const items = [1, 2];\nconst n = items.length;\nfunction add(a, b) { return a + b; }\nconst s = add(1, 2);\n";
    final TypeProject project =
        new TypeProject()
            .file("src/app.js", unit)
            .file(TypeScenarios.LIB + "lib.d.ts", "/// <reference lib=\"es5\" />\n")
            .file(TypeScenarios.LIB + "lib.es5.d.ts", TypeScenarios.ES5);
    final String path = "src/app.js";

    final UnitTypeFacts facts = project.answer(path, "items.length", "add(1, 2)");

    final Span length = TypeProject.span(unit, "items.length");
    assertThat(facts.typeOf(length)).map(TypeRef::display).contains(NUMBER);
    assertThat(facts.typeOf(TypeProject.span(unit, "add(1, 2)"))).isEmpty();
  }
}
