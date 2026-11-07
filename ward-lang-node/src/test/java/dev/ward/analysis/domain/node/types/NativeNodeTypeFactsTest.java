package dev.ward.analysis.domain.node.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import org.junit.jupiter.api.Test;

/** Checks the TypeScript and JavaScript facts the native resolver answers. */
class NativeNodeTypeFactsTest {
  private static final String UNIT = "src/app.ts";
  private static final String LIB = "node_modules/typescript/lib/";
  private static final String ES5 =
      """
      interface Math { max(...values: number[]): number; }
      declare var Math: Math;
      interface Array<T> {
        length: number;
        map<U>(callback: (value: T, index: number) => U): U[];
        [n: number]: T;
      }
      interface Promise<T> { then<R>(onfulfilled: (value: T) => R): Promise<R>; }
      interface String { length: number; toUpperCase(): string; }
      interface Number { toFixed(digits?: number): string; }
      interface Boolean { valueOf(): boolean; }
      interface Function { call(thisArg: any): any; }
      """;

  private static TypeProject project(final String unit) {
    return new TypeProject()
        .file(UNIT, unit)
        .file(LIB + "lib.d.ts", "/// <reference lib=\"es5\" />\n")
        .file(LIB + "lib.es5.d.ts", ES5);
  }

  /** The type of the last occurrence of {@code token} in the unit, or {@code -}. */
  private static String type(final TypeProject project, final String token) {
    final UnitTypeFacts facts = project.answer(UNIT, token);
    final String text = project.files().read(UNIT).orElseThrow();
    return facts.typeOf(TypeProject.span(text, token)).map(TypeRef::display).orElse("-");
  }

  @Test
  void callOfAFunctionFromAPackageHasItsDeclaredReturnType() {
    final String unit = "import { load } from \"pkg\";\nconst r = load(1);\n";
    final TypeProject project =
        project(unit)
            .file(
                "node_modules/pkg/package.json",
                "{\"name\":\"pkg\",\"types\":\"./dist/index.d.ts\"}")
            .file(
                "node_modules/pkg/dist/index.d.ts",
                "export declare function load(id: number): Promise<string>;\n");

    assertThat(type(project, "load(1)")).isEqualTo("Promise<string>");
    final CallTarget target =
        project.answer(UNIT, "load(1)").callTarget(TypeProject.span(unit, "load(1)")).orElseThrow();
    assertThat(target.name()).isEqualTo("load");
    assertThat(target.parameters()).extracting(TypeRef::display).containsExactly("number");
    assertThat(target.returnType().display()).isEqualTo("Promise<string>");
  }

  @Test
  void findsPackageTypesByTypingsMainAndAtTypes() {
    final String unit =
        "import a from \"one\";\nimport b from \"two\";\nimport c from \"three\";\nconst x = a();\nconst y = b();\nconst z = c();\n";
    final TypeProject project =
        project(unit)
            .file("node_modules/one/package.json", "{\"typings\":\"t.d.ts\"}")
            .file("node_modules/one/t.d.ts", "declare function f(): number;\nexport default f;\n")
            .file("node_modules/two/package.json", "{\"main\":\"./lib/main.js\"}")
            .file(
                "node_modules/two/lib/main.d.ts",
                "declare function f(): string;\nexport default f;\n")
            .file(
                "node_modules/@types/three/index.d.ts",
                "declare function f(): boolean;\nexport default f;\n");

    assertThat(type(project, "a()")).isEqualTo("number");
    assertThat(type(project, "b()")).isEqualTo("string");
    assertThat(type(project, "c()")).isEqualTo("boolean");
  }

  @Test
  void readsDeclarationsFromProjectFilesAcrossImportsAndReExports() {
    final String unit =
        "import { total, Box } from \"./lib\";\nconst copy = total;\nconst b = new Box().size;\n";
    final TypeProject project =
        project(unit)
            .file("src/lib.ts", "export * from \"./inner\";\n")
            .file(
                "src/inner.ts",
                "export const total: number = 3;\nexport class Box { size: number = 1; }\n");

    assertThat(type(project, "total")).isEqualTo("number");
    assertThat(type(project, "new Box().size")).isEqualTo("number");
  }

  @Test
  void mergesInterfacesAndReadsAmbientGlobals() {
    final String unit = "const a = shared.one;\nconst b = shared.two;\nconst c = flag;\n";
    final TypeProject project =
        project(unit)
            .file(
                "types/a.d.ts", "interface Shared { one: string }\ndeclare const shared: Shared;\n")
            .file("types/b.d.ts", "interface Shared { two: number }\ndeclare var flag: boolean;\n");

    assertThat(type(project, "shared.one")).isEqualTo("string");
    assertThat(type(project, "shared.two")).isEqualTo("number");
    assertThat(type(project, "flag")).isEqualTo("boolean");
  }

  @Test
  void readsAmbientModulesNamespacesAndGlobalAugmentations() {
    final String unit =
        "import { read } from \"fake\";\nconst a = read();\nconst b = Util.size;\nconst c = augmented;\n";
    final TypeProject project =
        project(unit)
            .file(
                "types/env.d.ts",
                """
                declare module "fake" { export function read(): string; }
                declare namespace Util { const size: number; }
                """)
            .file("types/mod.d.ts", "export {};\ndeclare global { var augmented: bigint; }\n");

    assertThat(type(project, "read()")).isEqualTo("string");
    assertThat(type(project, "Util.size")).isEqualTo("number");
    assertThat(type(project, "augmented")).isEqualTo("bigint");
  }

  @Test
  void readsAliasesUnionsLiteralsAndGenericsAtDeclarationLevel() {
    final String unit =
        "import { pick, Mode, Pair, Opts } from \"./types\";\nconst a = pick(\"x\");\nconst mode: Mode = null as any;\nconst copy = mode;\nconst d: Pair<string> = null as any;\nconst e = d.first;\nconst o: Opts = null as any;\nconst depth = o.depth;\n";
    final TypeProject project =
        project(unit)
            .file(
                "src/types.d.ts",
                """
                export type Mode = "fast" | "slow";
                export type Opts = { depth: number } & { name?: string };
                export interface Pair<T> { first: T; second: T[] }
                export declare function pick<K extends string>(key: K): Pair<K>;
                """);

    assertThat(type(project, "pick(\"x\")")).isEqualTo("Pair<string>");
    assertThat(type(project, "mode")).isEqualTo("Mode");
    assertThat(type(project, "d.first")).isEqualTo("string");
    assertThat(type(project, "o.depth")).isEqualTo("number");
  }

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
    final TypeProject project = project(unit);

    assertThat(type(project, "config.port")).isEqualTo("number");
    assertThat(type(project, "config")).isEqualTo("Config");
    assertThat(type(project, "count")).isEqualTo("number");
    assertThat(type(project, "label")).isEqualTo("\"a\"");
    assertThat(type(project, "names")).isEqualTo("string[]");
    assertThat(type(project, "names[0]")).isEqualTo("string");
    assertThat(type(project, "names.map((n) => n.length)")).isEqualTo("number[]");
    assertThat(type(project, "{ x: 1, y: \"s\" }")).isEqualTo("{ x: number; y: string; }");
    assertThat(type(project, "count + 2")).isEqualTo("number");
    assertThat(type(project, "label + count")).isEqualTo("string");
    assertThat(type(project, "count === 2")).isEqualTo("boolean");
    assertThat(type(project, "Math.max(1, 2)")).isEqualTo("number");
    assertThat(type(project, "twice(3)")).isEqualTo("number");
    assertThat(type(project, "arrow(2)")).isEqualTo("number");
    assertThat(type(project, "id(5)")).isEqualTo("number");
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
        project(unit)
            .file("src/cond.d.ts", "export declare const cond: T extends string ? 1 : 2;\n");

    assertThat(type(project, "gone()")).isEqualTo("-");
    assertThat(type(project, "b")).isEqualTo("-");
    assertThat(type(project, "nope.value")).isEqualTo("-");
    assertThat(type(project, "(window as any).anything")).isEqualTo("-");
    assertThat(type(project, "JSON.parse(\"1\")")).isEqualTo("-");
  }

  @Test
  void libraryTypesAreUnknownWithoutTheTypeScriptPackage() {
    final String unit = "const a = Math.max(1, 2);\nconst b = [1].length;\n";
    final TypeProject project = new TypeProject().file(UNIT, unit);

    assertThat(type(project, "Math.max(1, 2)")).isEqualTo("-");
    assertThat(type(project, "[1].length")).isEqualTo("-");
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
    final TypeProject project = project(unit);
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
    final TypeProject project = project(unit);
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
            .file(LIB + "lib.d.ts", "/// <reference lib=\"es5\" />\n")
            .file(LIB + "lib.es5.d.ts", ES5);
    final String path = "src/app.js";

    final UnitTypeFacts facts = project.answer(path, "items.length", "add(1, 2)");

    final Span length = TypeProject.span(unit, "items.length");
    assertThat(facts.typeOf(length)).map(TypeRef::display).contains("number");
    assertThat(facts.typeOf(TypeProject.span(unit, "add(1, 2)"))).isEmpty();
  }

  @Test
  void keepsPackageIndexesBetweenRunsByContentHash() {
    final String unit = "import { load } from \"pkg\";\nconst r = load(1);\n";
    final TypeProject project =
        project(unit)
            .file("node_modules/pkg/package.json", "{\"types\":\"index.d.ts\"}")
            .file(
                "node_modules/pkg/index.d.ts",
                "export declare function load(id: number): Promise<string>;\n");

    final String first = type(project, "load(1)");
    final int stored = project.cacheEntries();
    final int readsBefore = project.files().reads();
    final String second = type(project, "load(1)");

    assertThat(first).isEqualTo("Promise<string>").isEqualTo(second);
    assertThat(stored).isPositive();
    assertThat(project.cacheHits()).isPositive();
    assertThat(project.cacheEntries()).isEqualTo(stored);
    assertThat(project.files().reads() - readsBefore).isLessThan(readsBefore);
  }
}
