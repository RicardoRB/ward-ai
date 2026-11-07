package dev.ward.analysis.domain.node.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;
import org.junit.jupiter.api.Test;

/** Checks the facts the native resolver reads from declaration files. */
class NativeNodeDeclarationFactsTest {
  private static final String UNIT = TypeScenarios.UNIT;
  private static final String NUMBER = "number";

  @Test
  void callOfAFunctionFromAPackageHasItsDeclaredReturnType() {
    final String unit = "import { load } from \"pkg\";\nconst r = load(1);\n";
    final TypeProject project =
        TypeScenarios.project(unit)
            .file(
                "node_modules/pkg/package.json",
                "{\"name\":\"pkg\",\"types\":\"./dist/index.d.ts\"}")
            .file(
                "node_modules/pkg/dist/index.d.ts",
                "export declare function load(id: number): Promise<string>;\n");

    assertThat(TypeScenarios.type(project, "load(1)")).isEqualTo("Promise<string>");
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
        TypeScenarios.project(unit)
            .file("node_modules/one/package.json", "{\"typings\":\"t.d.ts\"}")
            .file("node_modules/one/t.d.ts", "declare function f(): number;\nexport default f;\n")
            .file("node_modules/two/package.json", "{\"main\":\"./lib/main.js\"}")
            .file(
                "node_modules/two/lib/main.d.ts",
                "declare function f(): string;\nexport default f;\n")
            .file(
                "node_modules/@types/three/index.d.ts",
                "declare function f(): boolean;\nexport default f;\n");

    assertThat(TypeScenarios.type(project, "a()")).isEqualTo(NUMBER);
    assertThat(TypeScenarios.type(project, "b()")).isEqualTo("string");
    assertThat(TypeScenarios.type(project, "c()")).isEqualTo("boolean");
  }

  @Test
  void readsDeclarationsFromProjectFilesAcrossImportsAndReExports() {
    final String unit =
        "import { total, Box } from \"./lib\";\nconst copy = total;\nconst b = new Box().size;\n";
    final TypeProject project =
        TypeScenarios.project(unit)
            .file("src/lib.ts", "export * from \"./inner\";\n")
            .file(
                "src/inner.ts",
                "export const total: number = 3;\nexport class Box { size: number = 1; }\n");

    assertThat(TypeScenarios.type(project, "total")).isEqualTo(NUMBER);
    assertThat(TypeScenarios.type(project, "new Box().size")).isEqualTo(NUMBER);
  }

  @Test
  void mergesInterfacesAndReadsAmbientGlobals() {
    final String unit = "const a = shared.one;\nconst b = shared.two;\nconst c = flag;\n";
    final TypeProject project =
        TypeScenarios.project(unit)
            .file(
                "types/a.d.ts", "interface Shared { one: string }\ndeclare const shared: Shared;\n")
            .file("types/b.d.ts", "interface Shared { two: number }\ndeclare var flag: boolean;\n");

    assertThat(TypeScenarios.type(project, "shared.one")).isEqualTo("string");
    assertThat(TypeScenarios.type(project, "shared.two")).isEqualTo(NUMBER);
    assertThat(TypeScenarios.type(project, "flag")).isEqualTo("boolean");
  }

  @Test
  void readsAmbientModulesNamespacesAndGlobalAugmentations() {
    final String unit =
        "import { read } from \"fake\";\nconst a = read();\nconst b = Util.size;\nconst c = augmented;\n";
    final TypeProject project =
        TypeScenarios.project(unit)
            .file(
                "types/env.d.ts",
                """
                declare module "fake" { export function read(): string; }
                declare namespace Util { const size: number; }
                """)
            .file("types/mod.d.ts", "export {};\ndeclare global { var augmented: bigint; }\n");

    assertThat(TypeScenarios.type(project, "read()")).isEqualTo("string");
    assertThat(TypeScenarios.type(project, "Util.size")).isEqualTo(NUMBER);
    assertThat(TypeScenarios.type(project, "augmented")).isEqualTo("bigint");
  }

  @Test
  void readsAliasesUnionsLiteralsAndGenericsAtDeclarationLevel() {
    final String unit =
        "import { pick, Mode, Pair, Opts } from \"./types\";\nconst a = pick(\"x\");\nconst mode: Mode = null as any;\nconst copy = mode;\nconst d: Pair<string> = null as any;\nconst e = d.first;\nconst o: Opts = null as any;\nconst depth = o.depth;\n";
    final TypeProject project =
        TypeScenarios.project(unit)
            .file(
                "src/types.d.ts",
                """
                export type Mode = "fast" | "slow";
                export type Opts = { depth: number } & { name?: string };
                export interface Pair<T> { first: T; second: T[] }
                export declare function pick<K extends string>(key: K): Pair<K>;
                """);

    assertThat(TypeScenarios.type(project, "pick(\"x\")")).isEqualTo("Pair<string>");
    assertThat(TypeScenarios.type(project, "mode")).isEqualTo("Mode");
    assertThat(TypeScenarios.type(project, "d.first")).isEqualTo("string");
    assertThat(TypeScenarios.type(project, "o.depth")).isEqualTo(NUMBER);
  }

  @Test
  void keepsPackageIndexesBetweenRunsByContentHash() {
    final String unit = "import { load } from \"pkg\";\nconst r = load(1);\n";
    final TypeProject project =
        TypeScenarios.project(unit)
            .file("node_modules/pkg/package.json", "{\"types\":\"index.d.ts\"}")
            .file(
                "node_modules/pkg/index.d.ts",
                "export declare function load(id: number): Promise<string>;\n");

    final String first = TypeScenarios.type(project, "load(1)");
    final int stored = project.cacheEntries();
    final int readsBefore = project.files().reads();
    final String second = TypeScenarios.type(project, "load(1)");

    assertThat(first).isEqualTo("Promise<string>").isEqualTo(second);
    assertThat(stored).isPositive();
    assertThat(project.cacheHits()).isPositive();
    assertThat(project.cacheEntries()).isEqualTo(stored);
    assertThat(project.files().reads() - readsBefore).isLessThan(readsBefore);
  }
}
