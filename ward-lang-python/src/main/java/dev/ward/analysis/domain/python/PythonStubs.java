package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.LanguageFrontend;
import java.util.ArrayList;
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

  /** The module that holds the built-in classes and functions. */
  static final String BUILTINS = "builtins";

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

  /** Returns the module-level function {@code name} of {@code module}, if one is declared once. */
  Optional<StubFunction> function(final String module, final String name) {
    return module(module)
        .flatMap(tree -> single(named(tree, PythonTypeKinds.FUNCDEF, name)))
        .map(definition -> new StubFunction(definition, module));
  }

  /** Returns the method {@code member} of the class {@code owner}, or of one of its bases. */
  Optional<StubFunction> method(final String owner, final String member) {
    final String module = moduleOf(owner);
    return method(module, simpleName(owner), member, 0);
  }

  /** Whether {@code qualified} names a class declared by a stub. */
  boolean isClass(final String qualified) {
    return classDefinition(qualified).isPresent();
  }

  /** Returns the class declaration of {@code qualified}, such as {@code list} or {@code json.X}. */
  Optional<SyntaxNode> classDefinition(final String qualified) {
    return module(moduleOf(qualified))
        .flatMap(tree -> single(named(tree, PythonTypeKinds.CLASSDEF, simpleName(qualified))));
  }

  /**
   * Returns the module-level function or class {@code name} of {@code module}, if declared once.
   */
  Optional<SyntaxNode> declaration(final String module, final String name) {
    return module(module).flatMap(tree -> single(named(tree, PythonTypeKinds.FUNCDEF, name)));
  }

  /** The qualified name of {@code name} declared in the builtins module, or the name itself. */
  static String qualify(final String module, final String name) {
    return BUILTINS.equals(module) ? name : module + DOT + name;
  }

  /** Splits {@code qualified} into its module; a name with no dot is a builtin. */
  static String moduleOf(final String qualified) {
    final int dot = qualified.lastIndexOf('.');
    return dot < 0 ? BUILTINS : qualified.substring(0, dot);
  }

  static String simpleName(final String qualified) {
    final int dot = qualified.lastIndexOf('.');
    return dot < 0 ? qualified : qualified.substring(dot + 1);
  }

  private Optional<StubFunction> method(
      final String module, final String className, final String member, final int depth) {
    if (depth > MAX_BASES) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> klass =
        module(module).flatMap(tree -> single(named(tree, PythonTypeKinds.CLASSDEF, className)));
    if (klass.isEmpty()) {
      return Optional.empty();
    }
    final List<SyntaxNode> own = named(klass.get(), PythonTypeKinds.FUNCDEF, member);
    if (!own.isEmpty()) {
      return single(own).map(definition -> new StubFunction(definition, module));
    }
    for (final String base : bases(klass.get())) {
      final Optional<StubFunction> inherited = method(module, base, member, depth + 1);
      if (inherited.isPresent()) {
        return inherited;
      }
    }
    return Optional.empty();
  }

  /** The names of the base classes written in {@code klass}, resolved in its own module. */
  private static List<String> bases(final SyntaxNode klass) {
    final List<String> bases = new ArrayList<>();
    for (final SyntaxNode arglist : PythonSyntax.children(klass, PythonTypeKinds.ARGLIST)) {
      for (final SyntaxNode argument : PythonSyntax.children(arglist, PythonTypeKinds.ARGUMENT)) {
        bases.add(PythonNames.identifier(argument.children().get(0)));
      }
    }
    return bases;
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

  private static List<SyntaxNode> named(
      final SyntaxNode scope, final String kind, final String name) {
    final List<SyntaxNode> found = new ArrayList<>();
    collectDefinitions(scope, found);
    return found.stream()
        .filter(node -> kind.equals(node.kind()))
        .filter(node -> name.equals(PythonNames.identifier(node.children().get(1))))
        .toList();
  }

  /** Collects the function and class definitions directly in {@code node}, not in their bodies. */
  private static void collectDefinitions(final SyntaxNode node, final List<SyntaxNode> found) {
    for (final SyntaxNode child : node.children()) {
      switch (child.kind()) {
        case PythonTypeKinds.FUNCDEF, PythonTypeKinds.CLASSDEF -> found.add(child);
        case PythonTypeKinds.STMT,
            PythonTypeKinds.COMPOUND_STMT,
            PythonTypeKinds.BLOCK,
            PythonTypeKinds.DECORATED ->
            collectDefinitions(child, found);
        default -> {
          // Statements that are not definitions, such as assignments, carry no declaration here.
        }
      }
    }
  }

  private static <T> Optional<T> single(final List<T> matches) {
    return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
  }
}
