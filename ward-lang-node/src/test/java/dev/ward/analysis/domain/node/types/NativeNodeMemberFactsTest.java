package dev.ward.analysis.domain.node.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Checks member access, calls and generics through declared types. */
class NativeNodeMemberFactsTest {
  private static final String NUMBER = "number";
  private static final String STRING = "string";
  private static final String UNKNOWN = "-";
  private static final String UNIT =
      """
      interface Box<T> { value: T; list: T[]; get(): T; maybe?: T }
      interface Named { name: string }
      interface Person extends Named { age: number }
      declare const box: Box<string>;
      declare const maybeBox: Box<number> | null;
      declare const tuple: [string, number];
      declare const dict: { [k: string]: boolean };
      declare const person: Person;
      declare const shapes: { kind: 1 } | { kind: 2 };
      declare const callback: (x: number) => string;
      enum Level { Low, High }
      declare class Svc { static make(): Svc; run(x: number): string; size: number; }
      declare class Sub extends Svc {}
      declare namespace NS { const size: number; function f(): string; }
      declare function over(a: string): string;
      declare function over(a: number): boolean;
      declare function wrap<T>(v: T): T[];
      declare function pair<A, B>(a: A, b: B): [A, B];
      const later = async () => 1;
      function noop() {}
      function pick(flag: boolean) { if (flag) { return 1; } return "x"; }
      const a1 = box.value;
      const a2 = box.list[0];
      const a3 = box.get();
      const a4 = maybeBox?.value;
      const a5 = tuple[1];
      const a6 = dict["k"];
      const a7 = Level.High;
      const a8 = Svc.make();
      const a9 = new Sub().run(1);
      const b1 = NS.size;
      const b2 = NS.f();
      const b3 = box.value.length;
      const b4 = box.nope;
      const b5 = over(1);
      const b6 = over("a");
      const b7 = over(true);
      const b8 = wrap(1);
      const b9 = pair(1, "a");
      const c1 = person.name;
      const c2 = shapes.kind;
      const c3 = callback(1);
      const c4 = later();
      const c5 = noop();
      const c6 = pick(true);
      const c7 = new Sub().size;
      const c8 = maybeBox!.value;
      """;

  private static String type(final String token) {
    return TypeScenarios.type(TypeScenarios.project(UNIT), token);
  }

  @Test
  void membersComeFromDeclaredTypesAndTheirHeritage() {
    assertThat(type("box.value")).isEqualTo(STRING);
    assertThat(type("box.list[0]")).isEqualTo(STRING);
    assertThat(type("box.get()")).isEqualTo(STRING);
    assertThat(type("box.value.length")).isEqualTo(NUMBER);
    assertThat(type("person.name")).isEqualTo(STRING);
    assertThat(type("new Sub().run(1)")).isEqualTo(STRING);
    assertThat(type("new Sub().size")).isEqualTo(NUMBER);
    assertThat(type("box.nope")).isEqualTo(UNKNOWN);
  }

  @Test
  void optionalChainsTuplesIndexesAndUnionsKeepTheirMemberTypes() {
    assertThat(type("maybeBox?.value")).isEqualTo("number | undefined");
    assertThat(type("maybeBox!.value")).isEqualTo(NUMBER);
    assertThat(type("tuple[1]")).isEqualTo(NUMBER);
    assertThat(type("dict[\"k\"]")).isEqualTo("boolean");
    assertThat(type("shapes.kind")).isEqualTo("1 | 2");
  }

  @Test
  void staticMembersEnumsAndNamespacesAreValues() {
    assertThat(type("Level.High")).isEqualTo("Level.High");
    assertThat(type("Svc.make()")).isEqualTo("Svc");
    assertThat(type("NS.size")).isEqualTo(NUMBER);
    assertThat(type("NS.f()")).isEqualTo(STRING);
  }

  @Test
  void callsPickOverloadsAndInferTypeArguments() {
    assertThat(type("over(1)")).isEqualTo("boolean");
    assertThat(type("over(\"a\")")).isEqualTo(STRING);
    assertThat(type("over(true)")).isEqualTo(UNKNOWN);
    assertThat(type("wrap(1)")).isEqualTo("number[]");
    assertThat(type("pair(1, \"a\")")).isEqualTo("[number, string]");
    assertThat(type("callback(1)")).isEqualTo(STRING);
  }

  @Test
  void returnTypesAreInferredFromBodies() {
    assertThat(type("later()")).isEqualTo("Promise<number>");
    assertThat(type("noop()")).isEqualTo("void");
    assertThat(type("pick(true)"))
        .isEqualTo("1 | \"x\"".replace("1", NUMBER).replace("\"x\"", STRING));
  }
}
