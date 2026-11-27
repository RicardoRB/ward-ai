package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** An object cache is a top-level empty object; the places that mention it are told apart. */
class NodeObjectCachesTest {
  private static final int CLOSE_OF_BLOCK = 9;
  private static final int BLOCK_OPEN = 4;

  private static Map<String, List<Integer>> caches(final String source) {
    final SourceUnit unit = new SourceUnit("src/a.ts", AnalysisLanguage.TYPESCRIPT, source);
    return NodeObjectCaches.of(new AntlrLanguageFrontend().parse(unit).tree());
  }

  @Test
  void theDeclarationIsNotAMention() {
    final Map<String, List<Integer>> found =
        caches("const c: Record<string, number> = {};\nfunction f(k) {\n  return c[k];\n}\n");

    assertThat(found).containsOnlyKeys("c");
    assertThat(found.get("c")).hasSize(1);
  }

  @Test
  void aMentionAfterADotIsAnotherProperty() {
    assertThat(caches("const c = {};\nfunction f(o) {\n  return o.c;\n}\n").get("c")).isEmpty();
  }

  @Test
  void anExportedObjectHasAMentionNoFunctionContains() {
    assertThat(caches("export const c = {};\n").get("c")).containsExactly(NodeObjectCaches.NOWHERE);
  }

  @Test
  void aNonEmptyObjectIsNotACache() {
    assertThat(caches("const c = { a: 1 };\nfunction f() {\n  return c;\n}\n")).isEmpty();
  }

  @Test
  void tokensMatchByTextWithAWildcard() {
    final SourceUnit unit =
        new SourceUnit("src/a.ts", AnalysisLanguage.TYPESCRIPT, "if (a) { b(); }\n");
    final NodeTokens tokens =
        new NodeTokens(NodeLeaves.of(new AntlrLanguageFrontend().parse(unit).tree()));

    assertThat(tokens.match(0, "if", "(", "*", ")")).contains(List.of("a"));
    assertThat(tokens.match(0, "if", "[")).isEmpty();
    assertThat(tokens.closing(BLOCK_OPEN)).isEqualTo(CLOSE_OF_BLOCK);
  }
}
