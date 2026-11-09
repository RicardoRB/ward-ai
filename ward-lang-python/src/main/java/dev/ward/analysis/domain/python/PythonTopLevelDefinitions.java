package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what the unused-definition rules need from a module: the top-level functions and classes it
 * declares, and the identifiers it uses.
 *
 * <p>shortcut: a name is used when any file spells it, with no scope or import resolution, so a
 * same-named local variable hides a dead definition; upgrade by resolving imports per file.
 * Decorated definitions are left out because decorators register them (routes, fixtures, entry
 * points). Like vulture, a {@code getattr} or {@code hasattr} string literal uses the attribute it
 * names, and importing {@code unittest} or {@code mock} uses the names of their bundled whitelists.
 */
final class PythonTopLevelDefinitions {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");
  private static final String FUNCDEF = "funcdef";
  private static final String CLASSDEF = "classdef";
  private static final String FUNCTION = "function";
  private static final String CLASS = "class";
  private static final String NAME = "name";
  private static final String ATOM_EXPR = "atom_expr";
  private static final String TRAILER = "trailer";
  private static final String ARGLIST = "arglist";
  private static final String ARGUMENT = "argument";
  private static final Set<String> GETATTR_CALLS = Set.of("getattr", "hasattr");
  private static final int GETATTR_MIN_ARGUMENTS = 2;
  private static final int GETATTR_MAX_ARGUMENTS = 3;
  private static final int HASATTR_ARGUMENTS = 2;
  private static final int NAME_ARGUMENT = 1;
  private static final int SINGLE_TOKEN = 1;
  private static final String UNITTEST = "unittest";
  private static final String MOCK = "mock";
  // vulture's bundled whitelists for unittest and unittest.mock, which it applies when the module
  // is
  // imported.
  private static final Set<String> UNITTEST_WHITELIST =
      Set.of(
          "setUp",
          "tearDown",
          "setUpClass",
          "tearDownClass",
          "run",
          "skipTest",
          "debug",
          "failureException",
          "longMessage",
          "maxDiff",
          "subTest");
  private static final Set<String> MOCK_WHITELIST = Set.of("return_value", "side_effect");

  private PythonTopLevelDefinitions() {}

  /**
   * The undecorated top-level functions and classes of the module {@code tree}, then the methods,
   * properties and attributes of the module.
   */
  static List<Symbol> declared(final SyntaxNode tree) {
    final List<Symbol> symbols = new ArrayList<>();
    for (final SyntaxNode statement : PythonExports.topLevelStatements(tree)) {
      final String kind = kindOf(statement);
      if (!kind.isEmpty()) {
        PythonSyntax.children(statement, NAME).stream()
            .limit(1)
            .forEach(
                name ->
                    symbols.add(new Symbol(PythonSyntax.dottedName(name), statement.span(), kind)));
      }
    }
    symbols.addAll(PythonMemberDefinitions.declared(tree));
    return symbols;
  }

  /**
   * Every identifier under {@code tree} except the name a {@code def} or {@code class} introduces
   * and the name an assignment stores on a target, plus the entries of a literal {@code __all__},
   * which export a name without using it, and the whitelisted names of an imported unittest or
   * mock.
   */
  static Set<String> usedNames(final SyntaxNode tree) {
    final Set<String> names = new HashSet<>();
    collect(tree, names, null);
    PythonExports.declaredAll(tree).ifPresent(names::addAll);
    if (names.contains(UNITTEST)) {
      names.addAll(UNITTEST_WHITELIST);
    }
    if (names.contains(MOCK)) {
      names.addAll(MOCK_WHITELIST);
    }
    return names;
  }

  private static String kindOf(final SyntaxNode statement) {
    return switch (statement.kind()) {
      case FUNCDEF -> FUNCTION;
      case CLASSDEF -> CLASS;
      default -> "";
    };
  }

  /**
   * Adds the names under {@code node}, leaving out {@code skip}, the stored name of an assignment.
   */
  private static void collect(
      final SyntaxNode node, final Set<String> names, final SyntaxNode skip) {
    if (PythonSyntax.COMMENT_KIND.equals(node.kind()) || node.equals(skip)) {
      return;
    }
    if (node.children().isEmpty()) {
      addLeafNames(PythonSyntax.text(node), names);
      return;
    }
    addAttributeLiteral(node, names);
    final SyntaxNode stored = PythonAttributeStores.storedAttribute(node).orElse(skip);
    boolean skipped = !FUNCDEF.equals(node.kind()) && !CLASSDEF.equals(node.kind());
    for (final SyntaxNode child : node.children()) {
      if (!skipped && NAME.equals(child.kind())) {
        skipped = true;
      } else {
        collect(child, names, stored);
      }
    }
  }

  /** The identifier of a leaf, or every word of an f-string, attribute names included. */
  private static void addLeafNames(final String text, final Set<String> names) {
    if (IDENTIFIER.matcher(text).matches()) {
      names.add(text);
    } else if (PythonFStrings.isFString(text)) {
      // Every word counts, so {self.port} uses the attribute port.
      final Matcher matcher = IDENTIFIER.matcher(text);
      while (matcher.find()) {
        names.add(matcher.group());
      }
    }
  }

  /** The attribute that {@code getattr(x, "name")} or {@code hasattr(x, "name")} names. */
  private static void addAttributeLiteral(final SyntaxNode node, final Set<String> names) {
    // The call's arguments follow the callee, and a call may be followed by more trailers.
    if (!ATOM_EXPR.equals(node.kind()) || node.children().size() < 2) {
      return;
    }
    final SyntaxNode trailer = node.children().get(1);
    final String callee = PythonSyntax.dottedName(node.children().get(0));
    if (!TRAILER.equals(trailer.kind()) || !GETATTR_CALLS.contains(callee)) {
      return;
    }
    final List<SyntaxNode> arguments = new ArrayList<>();
    PythonSyntax.children(trailer, ARGLIST)
        .forEach(list -> arguments.addAll(PythonSyntax.children(list, ARGUMENT)));
    final boolean valid =
        "getattr".equals(callee)
            ? arguments.size() >= GETATTR_MIN_ARGUMENTS && arguments.size() <= GETATTR_MAX_ARGUMENTS
            : arguments.size() == HASATTR_ARGUMENTS;
    if (valid) {
      stringLiteral(arguments.get(NAME_ARGUMENT))
          .filter(name -> IDENTIFIER.matcher(name).matches())
          .ifPresent(names::add);
    }
  }

  /** The contents of {@code argument} when it is one plain quoted string. */
  private static Optional<String> stringLiteral(final SyntaxNode argument) {
    final List<SyntaxNode> tokens = PythonSyntax.tokens(argument);
    if (tokens.size() != SINGLE_TOKEN) {
      return Optional.empty();
    }
    final String text = PythonSyntax.text(tokens.get(0));
    final boolean quoted =
        text.length() >= 2
            && (text.charAt(0) == '\'' || text.charAt(0) == '"')
            && text.charAt(text.length() - 1) == text.charAt(0);
    return quoted ? Optional.of(text.substring(1, text.length() - 1)) : Optional.empty();
  }
}
