package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.node.NodeModuleResolver;
import dev.ward.analysis.domain.node.NodeProjectLayout;
import dev.ward.analysis.domain.node.NodeScopeAnalysis;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A small project in memory and the facts the native resolver answers for one of its files. */
final class TypeProject {
  private final MemoryFiles files = new MemoryFiles();
  private final Map<String, byte[]> cached = new HashMap<>();
  private int cacheHits;

  TypeProject file(final String path, final String text) {
    files.with(path, text);
    return this;
  }

  MemoryFiles files() {
    return files;
  }

  int cacheHits() {
    return cacheHits;
  }

  int cacheEntries() {
    return cached.size();
  }

  private DeclarationCache cache() {
    return new DeclarationCache() {
      @Override
      public java.util.Optional<byte[]> load(final String key) {
        final byte[] found = cached.get(key);
        cacheHits += found == null ? 0 : 1;
        return java.util.Optional.ofNullable(found);
      }

      @Override
      public void store(final String key, final byte[] bytes) {
        cached.put(key, bytes);
      }
    };
  }

  NativeNodeTypeFacts facts() {
    final NodeProjectLayout layout =
        new NodeProjectLayout(
            Set.copyOf(
                files.paths().stream().filter(path -> !path.contains("node_modules/")).toList()),
            List.of(),
            List.of());
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final Program program =
        new Program(
            files, cache(), frontend, layout.files(), new NodeModuleResolver(layout)::resolve);
    return new NativeNodeTypeFacts(
        program, frontend, new NodeScopeAnalysis(environments -> Set.of()));
  }

  /** Answers the facts of {@code path} for the spans of {@code tokens}, the last match of each. */
  UnitTypeFacts answer(final String path, final String... tokens) {
    final String text = files.read(path).orElseThrow();
    final AnalysisLanguage language =
        path.endsWith(".js") ? AnalysisLanguage.JAVASCRIPT : AnalysisLanguage.TYPESCRIPT;
    final List<Span> spans =
        java.util.Arrays.stream(tokens).map(token -> span(text, token)).toList();
    return facts()
        .factsOf(new SourceUnit(path, language, text), spans, EnumSet.allOf(FactKind.class));
  }

  /** Returns the span of the last occurrence of {@code token} in {@code text}. */
  static Span span(final String text, final String token) {
    final int offset = text.lastIndexOf(token);
    if (offset < 0) {
      throw new IllegalArgumentException("not in the text: " + token);
    }
    final int line = (int) text.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    final int column = offset - text.lastIndexOf('\n', offset - 1);
    final int end = offset + token.length();
    final int last = end - 1;
    final int endLine = (int) text.substring(0, last).chars().filter(c -> c == '\n').count() + 1;
    final int endColumn = last - text.lastIndexOf('\n', last - 1);
    return new Span(line, column, endLine, endColumn, offset, end);
  }
}
