package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.LanguageFrontend;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The declarations of {@code .pyi} stub modules, parsed on first use. The text of a module comes
 * from {@code sources}; a module with no text is unknown.
 */
public final class PythonStubs {

  private static final String DOT = ".";
  private static final int MAX_BASES = 4;

  private final LanguageFrontend parser;
  private final Function<String, Optional<String>> sources;
  private final Map<String, Optional<SyntaxNode>> modules = new HashMap<>();

  /**
   * A function or method declared by a stub.
   *
   * @param definition the {@code funcdef} node
   * @param module the module that declares it
   */
  record StubFunction(SyntaxNode definition, String module) {}

  /** Creates the stubs; {@code sources} gives the {@code .pyi} text of a dotted module name. */
  public PythonStubs(
      final LanguageFrontend parser, final Function<String, Optional<String>> sources) {
    this.parser = parser;
    this.sources = sources;
  }

  /** Returns the module-level function {@code name} of {@code module}, if declared once. */
  Optional<StubFunction> function(final String module, final String name) {
    return module(module)
        .flatMap(
            tree ->
                PythonStubDefinitions.single(
                    PythonStubDefinitions.named(tree, PythonTypeKinds.FUNCDEF, name)))
        .map(definition -> new StubFunction(definition, module));
  }

  /** Returns the method {@code member} of the class {@code owner}, or of one of its bases. */
  Optional<StubFunction> method(final String owner, final String member) {
    return method(
        PythonQualifiedNames.moduleOf(owner), PythonQualifiedNames.simpleName(owner), member, 0);
  }

  /** Whether {@code qualified} names a class declared by a stub. */
  boolean isClass(final String qualified) {
    return classDefinition(qualified).isPresent();
  }

  private Optional<SyntaxNode> classDefinition(final String qualified) {
    return module(PythonQualifiedNames.moduleOf(qualified))
        .flatMap(
            tree ->
                PythonStubDefinitions.single(
                    PythonStubDefinitions.named(
                        tree,
                        PythonTypeKinds.CLASSDEF,
                        PythonQualifiedNames.simpleName(qualified))));
  }

  private Optional<StubFunction> method(
      final String module, final String className, final String member, final int depth) {
    if (depth > MAX_BASES) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> klass =
        module(module)
            .flatMap(
                tree ->
                    PythonStubDefinitions.single(
                        PythonStubDefinitions.named(tree, PythonTypeKinds.CLASSDEF, className)));
    if (klass.isEmpty()) {
      return Optional.empty();
    }
    final List<SyntaxNode> own =
        PythonStubDefinitions.named(klass.get(), PythonTypeKinds.FUNCDEF, member);
    if (!own.isEmpty()) {
      return PythonStubDefinitions.single(own)
          .map(definition -> new StubFunction(definition, module));
    }
    for (final String base : PythonStubDefinitions.bases(klass.get())) {
      final Optional<StubFunction> inherited = method(module, base, member, depth + 1);
      if (inherited.isPresent()) {
        return inherited;
      }
    }
    return Optional.empty();
  }

  private Optional<SyntaxNode> module(final String name) {
    return modules.computeIfAbsent(name, this::parse);
  }

  private Optional<SyntaxNode> parse(final String name) {
    final Optional<String> text = sources.apply(name);
    if (text.isEmpty()) {
      return Optional.empty();
    }
    final SourceUnit unit =
        new SourceUnit(name.replace(DOT, "/") + ".pyi", AnalysisLanguage.PYTHON, text.get());
    return Optional.of(parser.parse(unit).tree());
  }
}
