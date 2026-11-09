package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the methods, properties and attributes of a module, for the unused-member rules. Like
 * vulture, an attribute is any assigned {@code target.name}, in any scope, and every such store is
 * a definition.
 *
 * <p>shortcut: a method with any decorator except {@code staticmethod} or {@code classmethod} is
 * left out, since a decorator may register it; tuple targets such as {@code a.x, b.y = ...} define
 * no attribute. Upgrade by reading each decorator's target and every target element.
 */
final class PythonMemberDefinitions {

  /** The kind of a method declared in a class body. */
  static final String METHOD = "method";

  /**
   * The kind of a property: a method decorated with {@code property} or {@code cached_property}.
   */
  static final String PROPERTY = "property";

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
  private static final String AT = "@";
  private static final int SINGLE_DECORATOR = 1;
  private static final String OPEN_CALL = "(";

  private PythonMemberDefinitions() {}

  /**
   * The methods and properties of the top-level classes of the module {@code tree}, then every
   * attribute that any assignment in the module stores.
   */
  static List<Symbol> declared(final SyntaxNode tree) {
    final List<Symbol> symbols = new ArrayList<>();
    for (final SyntaxNode statement : PythonExports.topLevelStatements(tree)) {
      if (CLASSDEF.equals(statement.kind())) {
        symbols.addAll(classMembers(statement));
      }
    }
    symbols.addAll(PythonAttributeStores.declared(tree));
    return symbols;
  }

  private static List<Symbol> classMembers(final SyntaxNode classDef) {
    final List<Symbol> members = new ArrayList<>();
    for (final SyntaxNode block : PythonSyntax.children(classDef, BLOCK)) {
      for (final SyntaxNode statement : PythonExports.topLevelStatements(block)) {
        addMember(statement, members);
      }
    }
    return members;
  }

  private static void addMember(final SyntaxNode statement, final List<Symbol> members) {
    final Optional<SyntaxNode> def = function(statement);
    if (def.isEmpty()) {
      return;
    }
    final Optional<String> kind = methodKind(statement);
    final Optional<String> name = name(def.get());
    if (kind.isPresent() && name.isPresent()) {
      members.add(new Symbol(name.get(), def.get().span(), kind.get()));
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
    if (decorators.size() != SINGLE_DECORATOR) {
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
}
