package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Runs the Python type facts over a snippet of source, for the type-fact tests. */
final class PythonTypeFactsFixtures {

  private static final String PROJECT_STUB = "helpers";
  private static final String PROJECT_STUB_TEXT =
      "def make() -> str: ...\nclass Pool:\n    def size(self) -> int: ...\n";
  private static final String STUB_PREFIX = "/dev/ward/analysis/lang/python/stubs/";
  private static final String FILE = "m.py";

  private static final AntlrLanguageFrontend FRONTEND = new AntlrLanguageFrontend();

  private PythonTypeFactsFixtures() {}

  /** The facts of the outermost node whose text is exactly {@code snippet}, with stubs. */
  static SpanFacts facts(final String source, final String snippet, final Set<FactKind> kinds) {
    return facts(stubs(Map.of()), source, snippet, kinds);
  }

  /** The facts of {@code snippet} in {@code source}, answered with {@code stubs}. */
  static SpanFacts facts(
      final PythonStubs stubs,
      final String source,
      final String snippet,
      final Set<FactKind> kinds) {
    final Span span = spanOf(source, snippet);
    final SourceUnit unit = new SourceUnit(FILE, AnalysisLanguage.PYTHON, source);
    final UnitTypeFacts answered =
        new PythonTypeFacts(FRONTEND, stubs).factsOf(unit, List.of(span), kinds);
    return answered.at(span);
  }

  /** Stubs of the project module {@code helpers}, plus the bundled standard-library stubs. */
  static PythonStubs stubs(final Map<String, String> project) {
    final Map<String, String> modules = new HashMap<>(project);
    modules.putIfAbsent(PROJECT_STUB, PROJECT_STUB_TEXT);
    return new PythonStubs(
        FRONTEND, name -> Optional.ofNullable(modules.get(name)).or(() -> bundled(name)));
  }

  private static Optional<String> bundled(final String name) {
    try (InputStream stream =
        PythonTypeFactsFixtures.class.getResourceAsStream(STUB_PREFIX + name + ".pyi")) {
      return stream == null
          ? Optional.empty()
          : Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException unreadable) {
      throw new IllegalStateException(unreadable);
    }
  }

  /** The span of the outermost node of an expression kind whose offsets are {@code snippet}. */
  static Span spanOf(final String source, final String snippet) {
    final int start = source.indexOf(snippet);
    final int end = start + snippet.length();
    final SyntaxNode tree =
        FRONTEND.parse(new SourceUnit(FILE, AnalysisLanguage.PYTHON, source)).tree();
    return find(tree, start, end)
        .orElseThrow(() -> new AssertionError("no node for " + snippet))
        .span();
  }

  private static final Set<String> EXPRESSION_KINDS =
      Set.of("atom_expr", "atom", "NUMBER", "STRING", "NAME");

  private static Optional<SyntaxNode> find(final SyntaxNode node, final int start, final int end) {
    if (node.span().startOffset() == start
        && node.span().endOffset() == end
        && EXPRESSION_KINDS.contains(node.kind())) {
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
