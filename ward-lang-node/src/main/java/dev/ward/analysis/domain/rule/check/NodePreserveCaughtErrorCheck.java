package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a built-in error thrown inside a {@code catch} without the caught error as its {@code
 * cause}, like ESLint {@code preserve-caught-error}. A destructured catch parameter is reported at
 * the catch clause; a missing parameter is reported at the throw only when {@code
 * requireCatchParameter} is set. Causes that are not an object literal are not judged.
 */
public final class NodePreserveCaughtErrorCheck implements RuleCheck {

  private static final String CATCH = "catchProduction";
  private static final String THROW = "throwStatement";
  private static final String SINGLE = "singleExpression";
  private static final String SEQUENCE = "expressionSequence";
  private static final String IDENTIFIER = "identifier";
  private static final String ASSIGNABLE = "assignable";
  private static final String ARGUMENTS = "arguments";
  private static final String ARGUMENT = "argument";
  private static final String ARGUMENT_LIST = "argumentList";
  private static final String OBJECT = "objectLiteral";
  private static final String ARRAY = "arrayLiteral";
  private static final String PROPERTY = "propertyAssignment";
  private static final String NEW = "new";
  private static final int NEW_WITH_CALLEE = 3;
  private static final String CAUSE = "cause";
  private static final String AGGREGATE = "AggregateError";
  private static final int CAUSE_INDEX = 1;
  private static final int AGGREGATE_CAUSE_INDEX = 2;
  private static final Set<String> BUILT_IN_ERRORS =
      Set.of(
          "Error",
          "EvalError",
          "RangeError",
          "ReferenceError",
          "SyntaxError",
          "TypeError",
          "URIError",
          AGGREGATE);
  private static final Set<String> FUNCTIONS =
      Set.of(
          "functionDeclaration",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "methodDefinition",
          "constructorDeclaration",
          "classDeclaration",
          "classExpression");

  private final Options options;
  private final String missingCauseMessage;
  private final String partialMessage;
  private final String missingParameterMessage;
  private final String incorrectCauseMessage;

  /** The options of the check, as ESLint names them. */
  public record Options(boolean requireCatchParameter) {}

  /**
   * Creates the check.
   *
   * @param options the ESLint options
   * @param missingCauseMessage the message for a thrown error without the caught error as its cause
   * @param partialMessage the message for a destructured catch parameter
   * @param missingParameterMessage the message for a catch without a parameter, when required
   * @param incorrectCauseMessage the message for a cause that is another value
   */
  public NodePreserveCaughtErrorCheck(
      final Options options,
      final String missingCauseMessage,
      final String partialMessage,
      final String missingParameterMessage,
      final String incorrectCauseMessage) {
    this.options = Objects.requireNonNull(options, "options");
    this.missingCauseMessage = Objects.requireNonNull(missingCauseMessage, "missingCauseMessage");
    this.partialMessage = Objects.requireNonNull(partialMessage, "partialMessage");
    this.missingParameterMessage =
        Objects.requireNonNull(missingParameterMessage, "missingParameterMessage");
    this.incorrectCauseMessage =
        Objects.requireNonNull(incorrectCauseMessage, "incorrectCauseMessage");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    walk(root, Optional.empty(), matches);
    return List.copyOf(matches);
  }

  /** Walks the tree with the innermost catch parameter: its name, or empty when there is none. */
  private void walk(
      final SyntaxNode node, final Optional<Caught> caught, final List<RuleMatch> matches) {
    if (THROW.equals(node.kind())) {
      caught.ifPresent(scope -> checkThrow(node, scope, matches));
    }
    if (CATCH.equals(node.kind())) {
      final Optional<Caught> inner = catchOf(node, matches);
      node.children().forEach(child -> walk(child, inner, matches));
      return;
    }
    final Optional<Caught> next = FUNCTIONS.contains(node.kind()) ? Optional.empty() : caught;
    node.children().forEach(child -> walk(child, next, matches));
  }

  /**
   * The catch clause's parameter; a destructured one is reported and its throws are not checked.
   */
  private Optional<Caught> catchOf(final SyntaxNode clause, final List<RuleMatch> matches) {
    final List<SyntaxNode> parameter = parameterParts(clause);
    final Optional<SyntaxNode> identifier =
        parameter.stream().filter(c -> IDENTIFIER.equals(c.kind())).findFirst();
    if (identifier.isPresent()) {
      return Optional.of(new Caught(LeafText.of(identifier.get())));
    }
    final boolean destructured =
        parameter.stream().anyMatch(c -> OBJECT.equals(c.kind()) || ARRAY.equals(c.kind()));
    if (destructured) {
      matches.add(new RuleMatch(clause.span(), partialMessage));
      return Optional.empty();
    }
    return Optional.of(new Caught(null));
  }

  /** The nodes of a catch clause's parameter, which the tree holds directly or in an assignable. */
  private static List<SyntaxNode> parameterParts(final SyntaxNode clause) {
    final List<SyntaxNode> parts = new ArrayList<>();
    for (final SyntaxNode child : clause.children()) {
      if (ASSIGNABLE.equals(child.kind())) {
        parts.addAll(child.children());
      } else {
        parts.add(child);
      }
    }
    return parts;
  }

  /** The caught error's name, or null when the catch has no parameter. */
  private record Caught(String name) {}

  private void checkThrow(
      final SyntaxNode throwStatement, final Caught caught, final List<RuleMatch> matches) {
    final Optional<SyntaxNode> expression = Calls.thrown(throwStatement);
    if (expression.isEmpty()) {
      return;
    }
    final Optional<BuiltInCall> call = Calls.builtInCall(expression.get());
    if (call.isEmpty()) {
      return;
    }
    if (caught.name() == null) {
      if (options.requireCatchParameter()) {
        matches.add(new RuleMatch(throwStatement.span(), missingParameterMessage));
      }
      return;
    }
    checkCause(throwStatement, call.get(), caught.name(), matches);
  }

  private void checkCause(
      final SyntaxNode throwStatement,
      final BuiltInCall call,
      final String name,
      final List<RuleMatch> matches) {
    final List<SyntaxNode> values = Calls.argumentValues(call.arguments());
    final int index = AGGREGATE.equals(call.name()) ? AGGREGATE_CAUSE_INDEX : CAUSE_INDEX;
    if (values.size() <= index) {
      matches.add(new RuleMatch(throwStatement.span(), missingCauseMessage));
      return;
    }
    final Optional<SyntaxNode> object = Calls.objectOf(values.get(index));
    if (object.isEmpty()) {
      return;
    }
    final Optional<SyntaxNode> cause =
        object.get().children().stream()
            .filter(
                c -> PROPERTY.equals(c.kind()) && CAUSE.equals(LeafText.of(c.children().get(0))))
            .findFirst();
    if (cause.isEmpty()) {
      matches.add(new RuleMatch(throwStatement.span(), missingCauseMessage));
    } else if (!Calls.causeIs(cause.get(), name)) {
      matches.add(new RuleMatch(cause.get().children().get(2).span(), incorrectCauseMessage));
    }
  }

  /** A property {@code cause: name} of an options object. */

  /** A call or construction of a built-in error type: its name and its arguments. */
  private record BuiltInCall(String name, SyntaxNode arguments) {}

  /** The thrown expression, the built-in calls and the cause option of a throw. */
  private static final class Calls {

    /** The expression a throw statement throws, which a sequence may wrap. */
    private static Optional<SyntaxNode> thrown(final SyntaxNode throwStatement) {
      for (final SyntaxNode child : throwStatement.children()) {
        if (SINGLE.equals(child.kind())) {
          return Optional.of(child);
        }
        if (SEQUENCE.equals(child.kind())) {
          return child.children().stream().filter(c -> SINGLE.equals(c.kind())).findFirst();
        }
      }
      return Optional.empty();
    }

    private static Optional<BuiltInCall> builtInCall(final SyntaxNode expression) {
      final List<SyntaxNode> children = expression.children();
      final int callee =
          children.size() == NEW_WITH_CALLEE && NEW.equals(LeafText.of(children.get(0))) ? 1 : 0;
      if (children.size() < callee + 2) {
        return Optional.empty();
      }
      final String name = LeafText.of(children.get(callee));
      final SyntaxNode args = children.get(callee + 1);
      return ARGUMENTS.equals(args.kind()) && BUILT_IN_ERRORS.contains(name)
          ? Optional.of(new BuiltInCall(name, args))
          : Optional.empty();
    }

    /**
     * The options argument must give the caught error as the cause; a wrong one is reported at its
     * value.
     */
    private static boolean causeIs(final SyntaxNode property, final String name) {
      final List<SyntaxNode> children = property.children();
      return children.size() == NEW_WITH_CALLEE
          && CAUSE.equals(LeafText.of(children.get(0)))
          && name.equals(LeafText.of(children.get(2)));
    }

    private static Optional<SyntaxNode> objectOf(final SyntaxNode value) {
      final SyntaxNode inner = ExpressionShapes.innerOf(value);
      return inner.children().size() == 1 && OBJECT.equals(inner.children().get(0).kind())
          ? Optional.of(inner.children().get(0))
          : Optional.empty();
    }

    private static List<SyntaxNode> argumentValues(final SyntaxNode arguments) {
      final List<SyntaxNode> items = new ArrayList<>();
      for (final SyntaxNode child : arguments.children()) {
        items.addAll(ARGUMENT_LIST.equals(child.kind()) ? child.children() : List.of(child));
      }
      final List<SyntaxNode> values = new ArrayList<>();
      for (final SyntaxNode item : items) {
        if (SINGLE.equals(item.kind())) {
          values.add(item);
        } else if (ARGUMENT.equals(item.kind())) {
          item.children().stream()
              .filter(c -> SINGLE.equals(c.kind()))
              .findFirst()
              .ifPresent(values::add);
        }
      }
      return values;
    }
  }
}
