package dev.ward.analysis.domain.node.lint;

import static dev.ward.analysis.domain.node.lint.LintFixtures.positions;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The checks that read only the syntax tree of TypeScript, with the fixtures' inputs. */
class SyntaxChecksTest {

  private static final String MESSAGE = "message";

  @Test
  void extraNonNullAssertionReportsADoubleAssertion() {
    assertThat(
            positions(
                new ExtraNonNullAssertionCheck(MESSAGE),
                "const a = b!!;\nconst c = d!!;\nconst e = f!;\n"))
        .containsExactly("1:11", "2:11");
  }

  @Test
  void nonNullOptionalChainReportsTheAssertedChain() {
    assertThat(
            positions(
                new NonNullOptionalChainCheck(MESSAGE),
                "const a = (b?.c)!;\nconst d = (e?.[0])!;\nconst f = (g.h)!;\n"))
        .containsExactly("1:12", "2:12");
  }

  @Test
  void uselessEmptyExportIsReportedOnlyBesideOtherModuleItems() {
    assertThat(positions(new UselessEmptyExportCheck(MESSAGE), "export const a = 1;\nexport {};\n"))
        .containsExactly("2:1");
    assertThat(positions(new UselessEmptyExportCheck(MESSAGE), "export {};\n")).isEmpty();
  }

  @Test
  void preferAsConstReportsALiteralTypeThatRepeatsItsValue() {
    assertThat(
            positions(
                new PreferAsConstCheck(MESSAGE),
                "const a = 'x' as 'x';\nlet b: 'y' = 'y';\nlet c = 'z' as string;\n"))
        .containsExactly("1:18", "2:8");
  }

  @Test
  void duplicateEnumValuesAreReportedAfterTheFirstOne() {
    assertThat(
            positions(
                new DuplicateEnumValuesCheck(MESSAGE),
                "enum A {\n  X = 1,\n  Y = 1,\n  Z = 'a',\n  W = 'a',\n  V = 2,\n}\n"))
        .containsExactly("3:3", "5:3");
  }

  @Test
  void declarationMergingOfAClassAndAnInterfaceIsReported() {
    assertThat(
            positions(
                new DeclarationMergingCheck(MESSAGE),
                "interface Foo {\n  a: string;\n}\nclass Foo {}\n"))
        .containsExactly("1:11", "4:7");
  }

  @Test
  void misusedNewReportsAMethodNamedNewAndAConstructorInAnInterface() {
    assertThat(
            positions(
                new MisusedNewCheck("class message", "interface message"),
                "class Foo {\n  new(): Foo;\n}\ninterface Bar {\n  new (): Bar;\n"
                    + "  constructor(): void;\n}\n"))
        .containsExactly("2:3", "5:3", "6:3");
  }

  @Test
  void duplicateConstituentsAreReportedAfterTheFirstOne() {
    final String text = "type A = 'a' | 'b' | 'a';\ntype B = string & number & string;\n";

    assertThat(positions(new DuplicateConstituentsCheck(false, false, MESSAGE), text))
        .containsExactly("1:22", "2:28");
    assertThat(positions(new DuplicateConstituentsCheck(true, false, MESSAGE), text))
        .containsExactly("2:28");
    assertThat(positions(new DuplicateConstituentsCheck(false, true, MESSAGE), text))
        .containsExactly("1:22");
  }

  @Test
  void thisAliasAllowsDestructuringAndNamedAliasesWhenOptionsSaySo() {
    final String text =
        "class A {\n  f() {\n    const self = this;\n    const { a } = this;\n"
            + "    const that = this;\n  }\n}\n";

    assertThat(positions(new ThisAliasCheck(true, java.util.Set.of(), MESSAGE), text))
        .containsExactly("3:11", "5:11");
    assertThat(positions(new ThisAliasCheck(false, java.util.Set.of(), MESSAGE), text))
        .containsExactly("3:11", "4:11", "5:11");
    assertThat(positions(new ThisAliasCheck(true, java.util.Set.of("that"), MESSAGE), text))
        .containsExactly("3:11");
  }
}
