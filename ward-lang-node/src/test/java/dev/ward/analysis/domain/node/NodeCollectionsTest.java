package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A collection is one that only its file writes to when every mention of it is a plain use. */
class NodeCollectionsTest {
  private static Set<String> closed(final String source) {
    final SourceUnit unit = new SourceUnit("src/a.ts", AnalysisLanguage.TYPESCRIPT, source);
    return NodeCollections.of(new AntlrLanguageFrontend().parse(unit).tree());
  }

  @Test
  void aCollectionThatIsOnlyReadComparedResetWrittenAndReturnedIsClosed() {
    final String source =
        "let m: Map<string, Map<string, number>> | null = null;\n"
            + "function load() {\n"
            + "  if (m === null) {\n"
            + "    m = new Map<string, Map<string, number>>();\n"
            + "    m.set('a', new Map());\n"
            + "  }\n"
            + "  console.log(m.size, m.has('a'), [...m.keys()]);\n"
            + "  return m;\n"
            + "}\n";
    assertThat(closed(source)).containsExactly("m");
  }

  @Test
  void aSetStartedEmptyIsClosedToo() {
    assertThat(closed("const s = new Set();\nfunction add(x) {\n  s.add(x);\n}\n")).isEmpty();
    assertThat(closed("const s = new Set();\nfunction has(x) {\n  return s.has(x);\n}\n"))
        .containsExactly("s");
  }

  @Test
  void aCollectionThatIsHandedOutIsNotClosed() {
    assertThat(closed("let m = null;\nfunction f() {\n  use(m);\n}\n")).isEmpty();
    assertThat(closed("let m = null;\nfunction f() {\n  const copy = m;\n}\n")).isEmpty();
    assertThat(closed("let m = null;\nconst g = () => m;\n")).isEmpty();
    assertThat(closed("let m = null;\nexport { m };\n")).isEmpty();
  }

  @Test
  void aCollectionThatIsFilledOtherwiseIsNotClosed() {
    assertThat(closed("let m = null;\nfunction f(x) {\n  m = x;\n}\n")).isEmpty();
    assertThat(closed("let m = null;\nfunction f(x) {\n  m = new Map(x);\n}\n")).isEmpty();
    assertThat(closed("let m = null;\nfunction f(x) {\n  m?.set(x, 1);\n}\n")).isEmpty();
    assertThat(closed("let m = null;\nfunction f(x) {\n  m.forEach(x);\n}\n")).isEmpty();
    assertThat(closed("let m = null;\nfunction f(x) {\n  return m.constructor;\n}\n")).isEmpty();
  }

  @Test
  void anExportedOrNonEmptyDeclarationIsNotACandidate() {
    assertThat(closed("export let m = null;\nfunction f() {\n  return m;\n}\n")).isEmpty();
    assertThat(closed("const m = new Map([[1, 2]]);\nfunction f() {\n  return m;\n}\n")).isEmpty();
  }

  @Test
  void aPropertyWithTheSameNameIsNotTheCollection() {
    assertThat(closed("let m = null;\nfunction f(o) {\n  return o.m;\n}\n")).containsExactly("m");
  }
}
