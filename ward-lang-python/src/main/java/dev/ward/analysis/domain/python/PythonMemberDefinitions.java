package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the methods, properties and instance attributes of the top-level classes of a module, for
 * the unused-member rules.
 *
 * <p>shortcut: a method with any decorator except {@code staticmethod} or {@code classmethod} is
 * left out, since a decorator may register it, and only a plain {@code self.name = value} store
 * declares an attribute, so tuple targets are not read. Upgrade by reading each decorator's target.
 */
final class PythonMemberDefinitions {

  /** The kind of a method declared in a class body. */
  static final String METHOD = "method";

  /**
   * The kind of a property: a method decorated with {@code property} or {@code cached_property}.
   */
  static final String PROPERTY = "property";

  /** The kind of an instance attribute assigned through {@code self}. */
  static final String ATTRIBUTE = "attribute";

  private static final Pattern SELF_STORE = Pattern.compile("self\\.([A-Za-z_]\\w*)");
  private static final Set<String> PLAIN_DECORATORS = Set.of("staticmethod", "classmethod");
  private static final Set<String> PROPERTY_DECORATORS =
      Set.of("property", "cached_property", "functools.cached_property");
  private static final String CLASSDEF = "classdef";
  private static final String FUNCDEF = "funcdef";
  private static final String DECORATED = "decorated";
  private static final String DECORATORS = "decorators";
  private static final String DECORATOR = "decorator";
  private static final String BLOCK = "block";
  private static final String NAME = "name";
  private static final String EXPR_STMT = "expr_stmt";
  private static final String ANNASSIGN = "annassign";
  private static final String ASSIGN = "=";
  private static final String AT = "@";
  private static final String OPEN_CALL = "(";

  private PythonMemberDefinitions() {}

  /** The methods, properties and attributes of the top-level classes of the module {@code tree}. */
  static List<Symbol> declared(final SyntaxNode tree) {
    final List<Symbol> symbols = new ArrayList<>();
    for (final SyntaxNode statement : PythonExports.topLevelStatements(tree)) {
      if (CLASSDEF.equals(statement.kind())) {
        symbols.addAll(classMembers(statement));
      }
    }
    return symbols;
  }

  /**
   * The attribute name when {@code node} is {@code self.name = value} or {@code self.name: type =
   * value}, empty otherwise.
   */
  static Optional<String> selfStoreName(final SyntaxNode node) {
    if (!EXPR_STMT.equals(node.kind()) || node.children().size() < 2) {
      return Optional.empty();
    }
    final SyntaxNode operator = node.children().get(1);
    final boolean assigns =
        ASSIGN.equals(PythonSyntax.text(operator)) || ANNASSIGN.equals(operator.kind());
    final Matcher matcher = SELF_STORE.matcher(PythonSyntax.dottedName(node.children().get(0)));
    return assigns && matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  private static List<Symbol> classMembers(final SyntaxNode classDef) {
    final List<Symbol> members = new ArrayList<>();
    final Map<String, Symbol> attributes = new LinkedHashMap<>();
    for (final SyntaxNode block : PythonSyntax.children(classDef, BLOCK)) {
      for (final SyntaxNode statement : PythonExports.topLevelStatements(block)) {
        addMembers(statement, members, attributes);
      }
    }
    members.addAll(attributes.values());
    return members;
  }

  private static void addMembers(
      final SyntaxNode statement,
      final List<Symbol> members,
      final Map<String, Symbol> attributes) {
    final Optional<SyntaxNode> def = function(statement);
    if (def.isEmpty()) {
      return;
    }
    final Optional<String> kind = methodKind(statement);
    final Optional<String> name = name(def.get());
    if (kind.isPresent() && name.isPresent()) {
      members.add(new Symbol(name.get(), def.get().span(), kind.get()));
    }
    for (final Symbol attribute : attributesOf(def.get())) {
      attributes.putIfAbsent(attribute.name(), attribute);
    }
  }

  /** The {@code def} of a statement that is a function or a decorated function, if it is one. */
  private static Optional<SyntaxNode> function(final SyntaxNode statement) {
    if (FUNCDEF.equals(statement.kind())) {
      return Optional.of(statement);
    }
    if (DECORATED.equals(statement.kind())) {
      return PythonSyntax.children(statement, FUNCDEF).stream().findFirst();
    }
    return Optional.empty();
  }

  /** The kind of a method given its decorators, or empty when a decorator may register it. */
  private static Optional<String> methodKind(final SyntaxNode statement) {
    final List<String> decorators = decorators(statement);
    if (decorators.isEmpty()) {
      return Optional.of(METHOD);
    }
    if (decorators.size() != 1) {
      return Optional.empty();
    }
    if (PLAIN_DECORATORS.contains(decorators.get(0))) {
      return Optional.of(METHOD);
    }
    return PROPERTY_DECORATORS.contains(decorators.get(0))
        ? Optional.of(PROPERTY)
        : Optional.empty();
  }

  private static List<String> decorators(final SyntaxNode statement) {
    final List<String> texts = new ArrayList<>();
    for (final SyntaxNode child : statement.children()) {
      if (DECORATOR.equals(child.kind())) {
        texts.add(decoratorText(child));
      } else if (DECORATORS.equals(child.kind())) {
        child.children().forEach(decorator -> texts.add(decoratorText(decorator)));
      }
    }
    return texts;
  }

  /** The dotted name of a decorator, such as {@code functools.cached_property}; calls are cut. */
  private static String decoratorText(final SyntaxNode decorator) {
    final StringBuilder text = new StringBuilder();
    for (final SyntaxNode token : PythonSyntax.tokens(decorator)) {
      final String value = PythonSyntax.text(token).strip();
      if (OPEN_CALL.equals(value)) {
        break;
      }
      if (!value.isEmpty() && !AT.equals(value)) {
        text.append(value);
      }
    }
    return text.toString();
  }

  private static Optional<String> name(final SyntaxNode def) {
    return PythonSyntax.children(def, NAME).stream()
        .limit(1)
        .map(PythonSyntax::dottedName)
        .findFirst();
  }

  /** Every {@code self.name} store under {@code node}, each at the target that stores it. */
  private static List<Symbol> attributesOf(final SyntaxNode node) {
    final List<Symbol> found = new ArrayList<>();
    collectStores(node, found);
    return found;
  }

  private static void collectStores(final SyntaxNode node, final List<Symbol> found) {
    selfStoreName(node)
        .ifPresent(name -> found.add(new Symbol(name, node.children().get(0).span(), ATTRIBUTE)));
    node.children().forEach(child -> collectStores(child, found));
  }
}
