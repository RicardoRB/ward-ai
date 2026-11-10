package dev.ward.analysis.domain.node.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Checks the export forms of declaration files and the classes of JavaScript sources. */
class NativeNodeExportFactsTest {
  private static final String UNIT =
      """
      import dflt from "a";
      import assigned from "b";
      import { ns, wide } from "c";
      import { q } from "ambient";
      import { K } from "./k";
      const r1 = dflt();
      const r2 = assigned();
      const r3 = ns.g();
      const r4 = wide;
      const r5 = q;
      const r6 = new K();
      """;

  private static TypeProject project() {
    return TypeScenarios.project(UNIT)
        .file("node_modules/a/package.json", "{\"types\":\"index.d.ts\"}")
        .file("node_modules/a/index.d.ts", "export default function dflt(): number;\n")
        .file("node_modules/b/package.json", "{\"types\":\"index.d.ts\"}")
        .file("node_modules/b/index.d.ts", "declare function f(): string;\nexport = f;\n")
        .file("node_modules/c/package.json", "{\"types\":\"index.d.ts\"}")
        .file(
            "node_modules/c/index.d.ts",
            "export * as ns from \"./inner\";\nexport { v as wide } from \"./inner\";\n")
        .file(
            "node_modules/c/inner.d.ts",
            "export declare const v: boolean;\nexport declare function g(): number;\n")
        .file("types/amb.d.ts", "declare module \"ambient\" { export const q: string; }\n")
        .file("src/k.js", "export class K { m(x) { return x; } static s() { return 1; } }\n");
  }

  @Test
  void defaultAssignedAndReExportedNamesResolve() {
    final TypeProject project = project();

    assertThat(TypeScenarios.type(project, "dflt()")).isEqualTo("number");
    assertThat(TypeScenarios.type(project, "assigned()")).isEqualTo("string");
    assertThat(TypeScenarios.type(project, "ns.g()")).isEqualTo("number");
    assertThat(TypeScenarios.type(project, "wide")).isEqualTo("boolean");
  }

  @Test
  void ambientModulesAndJavaScriptClassesResolve() {
    final TypeProject project = project();

    assertThat(TypeScenarios.type(project, "q")).isEqualTo("string");
    assertThat(TypeScenarios.type(project, "new K()")).isEqualTo("K");
  }
}
