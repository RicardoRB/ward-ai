package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports {@code 'use strict'} directives, like ESLint {@code strict}. A module is strict already,
 * so each of its directives is unnecessary. A script follows the mode: {@code global} wants the
 * directive at the top of the source, {@code function} wants it in each function that is not nested
 * in a strict one, {@code never} forbids it, and {@code safe} is {@code function} for a script.
 */
public final class NodeStrictDirectiveCheck implements RuleCheck {

  /** The modes of the rule, as ESLint names them. */
  public enum Mode {
    NEVER,
    GLOBAL,
    FUNCTION,
    SAFE;

    /** The mode with the given option name. */
    public static Mode named(final String name) {
      return valueOf(name.toUpperCase(java.util.Locale.ROOT));
    }
  }

  private static final String SOURCE_ELEMENTS = "sourceElements";
  private static final String FUNCTION_BODY = "functionBody";
  private static final String PARAMETERS = "formalParameterList";
  private static final String ARROW_PARAMETERS = "arrowFunctionParameters";
  private static final String FUNCTION_DECLARATION = "functionDeclaration";
  private static final String FUNCTION_EXPRESSION = "anonymousFunction";
  private static final String ARROW = "arrowFunctionDeclaration";
  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String CLASS_EXPRESSION = "classExpression";
  private static final Set<String> COMPLEX_PARAMETER_MARKS = Set.of("=", "...", "[", "{");
  private static final String USE_STRICT = "use strict";
  private static final String FUNCTION = "function";
  private static final String GLOBAL = "global";
  private static final String NEVER = "never";
  private static final String MODULE = "module";
  private static final String MULTIPLE = "multiple";
  private static final String UNNECESSARY = "unnecessary";
  private static final String UNNECESSARY_IN_CLASSES = "unnecessaryInClasses";
  private static final String NON_SIMPLE = "nonSimpleParameterList";
  private static final String WRAP = "wrap";

  private final Mode mode;
  private final boolean alwaysModule;
  private final Map<String, String> messages;

  /**
   * Creates the check.
   *
   * @param mode the mode of the rule
   * @param alwaysModule whether every source is a module, as the TypeScript parser reports it
   * @param messages the message of each ESLint message id, keyed by the id
   */
  public NodeStrictDirectiveCheck(
      final Mode mode, final boolean alwaysModule, final Map<String, String> messages) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.alwaysModule = alwaysModule;
    this.messages = Map.copyOf(messages);
  }

  /** The walk over one unit: the scope stack of functions, and the depth of class bodies. */
  private final class DirectiveWalk {

    private final boolean module;
    private final List<Boolean> scopes = new ArrayList<>();
    private int classDepth;
    private final List<RuleMatch> matches = new ArrayList<>();

    private DirectiveWalk(final boolean module) {
      this.module = module;
    }

    private Mode effective() {
      if (module) {
        return null;
      }
      return mode == Mode.SAFE ? Mode.FUNCTION : mode;
    }

    private void visit(final SyntaxNode node) {
      if (isFunction(node)) {
        function(node);
      } else if (CLASS_DECLARATION.equals(node.kind()) || CLASS_EXPRESSION.equals(node.kind())) {
        classDepth++;
        node.children().forEach(this::visit);
        classDepth--;
      } else {
        node.children().forEach(this::visit);
      }
    }

    private void program(final SyntaxNode elements) {
      final List<SyntaxNode> directives = directives(elements);
      final Mode current = effective();
      if (current == Mode.GLOBAL) {
        if (!elements.children().isEmpty() && directives.isEmpty()) {
          matches.add(new RuleMatch(elements.children().get(0).span(), messages.get(GLOBAL)));
        }
        allExceptFirst(directives, MULTIPLE);
      } else {
        all(directives, messageOf(current));
      }
    }

    private void function(final SyntaxNode function) {
      final List<SyntaxNode> directives = bodyDirectives(function);
      final Mode current = effective();
      if (current == Mode.FUNCTION) {
        functionMode(function, directives);
        scopes.add(!scopes.isEmpty() && scopes.get(scopes.size() - 1) || !directives.isEmpty());
        function.children().forEach(this::visit);
        scopes.remove(scopes.size() - 1);
      } else {
        if (!directives.isEmpty()) {
          reportOrNonSimple(function, directives, messageOf(current));
        }
        function.children().forEach(this::visit);
      }
    }

    private void functionMode(final SyntaxNode function, final List<SyntaxNode> directives) {
      final boolean simple = simpleParameters(function);
      if (directives.isEmpty()) {
        if (scopes.isEmpty() && classDepth == 0) {
          matches.add(new RuleMatch(function.span(), messages.get(simple ? FUNCTION : WRAP)));
        }
      } else {
        strictFunction(directives, simple);
      }
    }

    private void strictFunction(final List<SyntaxNode> directives, final boolean simple) {
      if (simple) {
        unnecessary(directives.get(0));
      } else {
        matches.add(new RuleMatch(directives.get(0).span(), messages.get(NON_SIMPLE)));
      }
      allExceptFirst(directives, MULTIPLE);
    }

    /** A strict directive inside a function that is already strict, or inside a class. */
    private void unnecessary(final SyntaxNode directive) {
      if (!scopes.isEmpty() && scopes.get(scopes.size() - 1)) {
        matches.add(new RuleMatch(directive.span(), messages.get(UNNECESSARY)));
      } else if (classDepth > 0) {
        matches.add(new RuleMatch(directive.span(), messages.get(UNNECESSARY_IN_CLASSES)));
      }
    }

    private void reportOrNonSimple(
        final SyntaxNode function, final List<SyntaxNode> directives, final String id) {
      if (simpleParameters(function)) {
        all(directives, id);
      } else {
        matches.add(new RuleMatch(directives.get(0).span(), messages.get(NON_SIMPLE)));
        allExceptFirst(directives, MULTIPLE);
      }
    }

    private void all(final List<SyntaxNode> directives, final String id) {
      directives.forEach(
          directive -> matches.add(new RuleMatch(directive.span(), messages.get(id))));
    }

    private void allExceptFirst(final List<SyntaxNode> directives, final String id) {
      directives.stream()
          .skip(1)
          .forEach(directive -> matches.add(new RuleMatch(directive.span(), messages.get(id))));
    }
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final DirectiveWalk walk = new DirectiveWalk(alwaysModule || SourceKind.isModule(root));
    root.children().stream()
        .filter(child -> SOURCE_ELEMENTS.equals(child.kind()))
        .findFirst()
        .ifPresent(
            elements -> {
              walk.program(elements);
              elements.children().forEach(walk::visit);
            });
    return List.copyOf(walk.matches);
  }

  /** The message id of a mode, for a directive that the mode does not allow. */
  private String messageOf(final Mode current) {
    if (current == null) {
      return MODULE;
    }
    return switch (current) {
      case NEVER -> NEVER;
      case GLOBAL -> GLOBAL;
      case FUNCTION, SAFE -> FUNCTION;
    };
  }

  /** The directives at the start of the source elements that hold the directive text. */
  private static List<SyntaxNode> directives(final SyntaxNode elements) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode element : elements.children()) {
      if (!ExpressionShapes.isStringElement(element)) {
        break;
      }
      if (USE_STRICT.equals(
          LeafText.of(element).replace("\"", "").replace("'", "").replace(";", ""))) {
        found.add(element);
      }
    }
    return found;
  }

  /** The directives of a function body, which is a block with source elements. */
  private static List<SyntaxNode> bodyDirectives(final SyntaxNode function) {
    return bodyElements(function).map(NodeStrictDirectiveCheck::directives).orElse(List.of());
  }

  /** The source elements of the function body that belongs to a function, not to a nested one. */
  private static Optional<SyntaxNode> bodyElements(final SyntaxNode function) {
    return findBody(function)
        .flatMap(
            body ->
                body.children().stream().filter(c -> SOURCE_ELEMENTS.equals(c.kind())).findFirst());
  }

  private static Optional<SyntaxNode> findBody(final SyntaxNode node) {
    for (final SyntaxNode child : node.children()) {
      if (FUNCTION_BODY.equals(child.kind())) {
        return Optional.of(child);
      }
      if (!isFunction(child)) {
        final Optional<SyntaxNode> deeper = findBody(child);
        if (deeper.isPresent()) {
          return deeper;
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Whether a function's parameters are simple: plain names, without defaults, rest or patterns.
   */
  private static boolean simpleParameters(final SyntaxNode function) {
    return function.children().stream()
        .filter(c -> PARAMETERS.equals(c.kind()) || ARROW_PARAMETERS.equals(c.kind()))
        .allMatch(c -> COMPLEX_PARAMETER_MARKS.stream().noneMatch(LeafText.of(c)::contains));
  }

  private static boolean isFunction(final SyntaxNode node) {
    return FUNCTION_DECLARATION.equals(node.kind())
        || ARROW.equals(node.kind())
        || FUNCTION_EXPRESSION.equals(node.kind())
            && node.children().stream().noneMatch(c -> ARROW.equals(c.kind()));
  }
}
