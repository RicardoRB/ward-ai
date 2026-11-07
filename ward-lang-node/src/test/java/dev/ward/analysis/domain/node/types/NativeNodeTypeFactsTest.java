package dev.ward.analysis.domain.node.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import org.junit.jupiter.api.Test;

/** Checks the TypeScript and JavaScript facts the native resolver answers. */
class NativeNodeTypeFactsTest {
  private static final String APP =
      "interface Config {\n  port: number;\n  name: string;\n}\n\nconst config: Config = { port: 8080, name: \"x\" };\nconst port: number = config.port;\nconst missing = nope.value;\nabstract class Base {\n  abstract run(): number;\n}\nclass Impl extends Base {\n  run(): number {\n    return 1;\n  }\n}\nconst biggest = Math.max(1, 2);\nexport { port, missing, Impl, biggest };\n";

  private static TypeProject project() {
    return new TypeProject()
        .file("src/app.ts", APP)
        .file(
            "node_modules/typescript/lib/lib.d.ts",
            "interface Math { max(...values: number[]): number; }\ndeclare var Math: Math;\n");
  }

  @Test
  void answersTheFactsOfTheSidecarFixture() {
    final UnitTypeFacts facts =
        project().answer("src/app.ts", "Math.max(1, 2)", "config.port", "nope.value");
    final Span max = TypeProject.span(APP, "Math.max(1, 2)");
    assertThat(facts.typeOf(max)).map(TypeRef::name).contains("number");
    final var target = facts.callTarget(max).orElseThrow();
    assertThat(target.declaringType().name()).isEqualTo("Math");
    assertThat(target.name()).isEqualTo("max");
    assertThat(target.isVarargs()).isTrue();
    assertThat(target.isStatic()).isFalse();
    assertThat(facts.typeOf(TypeProject.span(APP, "config.port")))
        .map(TypeRef::name)
        .contains("number");
    assertThat(facts.typeOf(TypeProject.span(APP, "nope.value"))).isEmpty();
  }
}
