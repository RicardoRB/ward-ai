package dev.ward.analysis.domain.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests the native Python type facts through the expressions a rule asks about. */
class PythonTypeFactsTest {

  private static final String PROJECT_STUB = "helpers";
  private static final String PROJECT_STUB_TEXT =
      "def make() -> str: ...\nclass Pool:\n    def size(self) -> int: ...\n";

  private final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();

  @Test
  void annotatedParameterCallTargetResolvesToBuiltinListAppend() {
    final String source = "def f(items: list[str]):\n    items.append(1)\n";

    final CallTarget target = callTarget(source, "items.append(1)");

    assertThat(target.declaringType().name()).isEqualTo("list");
    assertThat(target.name()).isEqualTo("append");
    assertThat(target.returnType()).isEqualTo(TypeRef.of("None"));
  }

  @Test
  void annotatedParameterTypeKeepsItsArguments() {
    final String source = "def f(items: list[str]):\n    items\n";

    final TypeRef type = typeOf(source, "items");

    assertThat(type.name()).isEqualTo("list");
    assertThat(type.arguments()).containsExactly(TypeRef.of("str"));
  }

  @Test
  void literalsHaveTheirBuiltinTypes() {
    assertThat(typeOf("x = 1\n", "1").name()).isEqualTo("int");
    assertThat(typeOf("x = 1.5\n", "1.5").name()).isEqualTo("float");
    assertThat(typeOf("x = 2j\n", "2j").name()).isEqualTo("complex");
    assertThat(typeOf("x = 'a'\n", "'a'").name()).isEqualTo("str");
    assertThat(typeOf("x = b'a'\n", "b'a'").name()).isEqualTo("bytes");
    assertThat(typeOf("x = True\n", "True").name()).isEqualTo("bool");
    assertThat(typeOf("x = [1]\n", "[1]").name()).isEqualTo("list");
    assertThat(typeOf("x = {}\n", "{}").name()).isEqualTo("dict");
    assertThat(typeOf("x = {1}\n", "{1}").name()).isEqualTo("set");
    assertThat(typeOf("x = (1, 2)\n", "(1, 2)").name()).isEqualTo("tuple");
  }

  @Test
  void localInitializedOnceIsInferredFromItsInitializer() {
    final String source = "s = []\ns.append(1)\n";

    final CallTarget target = callTarget(source, "s.append(1)");

    assertThat(target.declaringType().name()).isEqualTo("list");
  }

  @Test
  void localAssignedAgainIsUnknown() {
    final String source = "s = []\ns = 3\ns.append(1)\n";

    assertThat(callFacts(source, "s.append(1)")).isNull();
  }

  @Test
  void constructorOfSourceClassHasItsClassAsType() {
    final String source = "class Box:\n    pass\nb = Box()\n";

    assertThat(typeOf(source, "Box()").name()).isEqualTo("Box");
  }

  @Test
  void unannotatedParameterIsUnknown() {
    final String source = "def g(x):\n    x.append(1)\n";

    assertThat(callFacts(source, "x.append(1)")).isNull();
  }

  @Test
  void unsupportedAnnotationIsUnknown() {
    final String source =
        "from typing import Optional\ndef h(items: Optional[list[str]]):\n    items.append(1)\n";

    assertThat(callFacts(source, "items.append(1)")).isNull();
  }

  @Test
  void projectStubGivesTheReturnTypeOfAnImportedFunction() {
    final String source = "import helpers\nx = helpers.make()\n";

    assertThat(typeOf(source, "helpers.make()").name()).isEqualTo("str");
  }

  @Test
  void projectStubGivesTheReturnTypeOfAMethodOfAnImportedClass() {
    final String source = "from helpers import Pool\ndef f(p: Pool):\n    p.size()\n";

    assertThat(typeOf(source, "p.size()").name()).isEqualTo("int");
  }

  @Test
  void bundledStandardModuleStubGivesTheCallTarget() {
    final String source = "import json\njson.dumps(1)\n";

    final CallTarget target = callTarget(source, "json.dumps(1)");

    assertThat(target.declaringType().name()).isEqualTo("json");
    assertThat(target.name()).isEqualTo("dumps");
    assertThat(target.returnType()).isEqualTo(TypeRef.of("str"));
  }

  @Test
  void stubDeclaredTwiceIsUnknown() {
    final PythonStubs stubs = stubs(Map.of("twice", "def f() -> int: ...\ndef f() -> str: ...\n"));
    final String source = "import twice\ntwice.f()\n";

    assertThat(typeOfWith(stubs, source, "twice.f()")).isNull();
  }

  @Test
  void unknownKindsAreNotAnswered() {
    final String source = "x = 1\n";
    final UnitTypeFacts facts = facts(source, "1", EnumSet.of(FactKind.SHAPE));

    assertThat(facts.at(spanOf(source, "1")).type()).isNull();
  }

  private TypeRef typeOf(final String source, final String snippet) {
    return typeOfWith(stubs(Map.of()), source, snippet);
  }

  private TypeRef typeOfWith(final PythonStubs stubs, final String source, final String snippet) {
    return facts(stubs, source, snippet, EnumSet.of(FactKind.TYPE))
        .at(spanOf(source, snippet))
        .type();
  }

  private CallTarget callTarget(final String source, final String snippet) {
    return callFacts(source, snippet);
  }

  private CallTarget callFacts(final String source, final String snippet) {
    return facts(stubs(Map.of()), source, snippet, EnumSet.of(FactKind.CALL))
        .at(spanOf(source, snippet))
        .call();
  }

  private UnitTypeFacts facts(
      final String source, final String snippet, final EnumSet<FactKind> kinds) {
    return facts(stubs(Map.of()), source, snippet, kinds);
  }

  private UnitTypeFacts facts(
      final PythonStubs stubs,
      final String source,
      final String snippet,
      final EnumSet<FactKind> kinds) {
    final SourceUnit unit = new SourceUnit("m.py", AnalysisLanguage.PYTHON, source);
    return new PythonTypeFacts(frontend, stubs)
        .factsOf(unit, List.of(spanOf(source, snippet)), kinds);
  }

  private PythonStubs stubs(final Map<String, String> project) {
    final Map<String, String> modules = new java.util.HashMap<>(project);
    modules.putIfAbsent(PROJECT_STUB, PROJECT_STUB_TEXT);
    return new PythonStubs(
        frontend, name -> Optional.ofNullable(modules.get(name)).or(() -> bundled(name)));
  }

  private static Optional<String> bundled(final String name) {
    try (InputStream stream =
        PythonTypeFactsTest.class.getResourceAsStream(
            "/dev/ward/analysis/lang/python/stubs/" + name + ".pyi")) {
      return stream == null
          ? Optional.empty()
          : Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException unreadable) {
      throw new IllegalStateException(unreadable);
    }
  }

  /** The span of the outermost atom expression whose text is exactly {@code snippet}. */
  private Span spanOf(final String source, final String snippet) {
    final int start = source.indexOf(snippet);
    final int end = start + snippet.length();
    final SyntaxNode tree =
        frontend.parse(new SourceUnit("m.py", AnalysisLanguage.PYTHON, source)).tree();
    return find(tree, start, end)
        .orElseThrow(() -> new AssertionError("no node for " + snippet))
        .span();
  }

  private static Optional<SyntaxNode> find(final SyntaxNode node, final int start, final int end) {
    if (node.span().startOffset() == start
        && node.span().endOffset() == end
        && ("atom_expr".equals(node.kind())
            || "atom".equals(node.kind())
            || "NUMBER".equals(node.kind())
            || "STRING".equals(node.kind())
            || "NAME".equals(node.kind()))) {
      return Optional.of(node);
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<SyntaxNode> found = find(child, start, end);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }
}
