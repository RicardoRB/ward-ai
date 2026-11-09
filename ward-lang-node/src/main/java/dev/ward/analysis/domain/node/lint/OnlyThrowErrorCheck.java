package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reports a {@code throw} of a value whose known type is not an error. */
public final class OnlyThrowErrorCheck extends TypedCheck {

  private static final String THROW = "throwStatement";
  private static final String CATCH = "catchProduction";
  private static final String IDENTIFIER = "identifier";
  private static final String ERROR_SUFFIX = "Error";
  private static final String UNKNOWN = "unknown";

  private final boolean allowRethrowing;
  private final boolean allowThrowingAny;
  private final boolean allowThrowingUnknown;
  private final Set<String> allowed;
  private final String message;

  /** Creates the check with the rule's options and message. */
  public OnlyThrowErrorCheck(
      final boolean allowRethrowing,
      final boolean allowThrowingAny,
      final boolean allowThrowingUnknown,
      final Set<String> allowed,
      final String message) {
    super();
    this.allowRethrowing = allowRethrowing;
    this.allowThrowingAny = allowThrowingAny;
    this.allowThrowingUnknown = allowThrowingUnknown;
    this.allowed = Set.copyOf(allowed);
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    final Set<String> catchNames = catchParameters(root);
    for (final SyntaxNode statement : SyntaxText.nodesOf(root, THROW)) {
      for (final SyntaxNode thrown : SyntaxText.childrenOf(statement, SEQUENCE)) {
        final Optional<TypeRef> type = typeOf(types, thrown);
        if (type.isPresent() && isReported(type.get()) && !isRethrow(thrown, catchNames)) {
          out.add(new RuleMatch(thrown.span(), message));
        }
      }
    }
    return out;
  }

  /** Reports whether a thrown value of this type breaks the rule's options. */
  private boolean isReported(final TypeRef type) {
    if (allowed.contains(type.name())) {
      return false;
    }
    if (isAny(type)) {
      return !allowThrowingAny;
    }
    if (isUnknownType(type)) {
      return !allowThrowingUnknown;
    }
    return !isError(type);
  }

  /** Reports a throw of a catch parameter, which is a rethrow of the caught value. */
  private boolean isRethrow(final SyntaxNode thrown, final Set<String> catchNames) {
    return allowRethrowing && catchNames.contains(SyntaxText.of(thrown));
  }

  /** Returns the names of the catch parameters of the unit. */
  private static Set<String> catchParameters(final SyntaxNode root) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode clause : SyntaxText.nodesOf(root, CATCH)) {
      SyntaxText.childrenOf(clause, IDENTIFIER).forEach(name -> names.add(SyntaxText.of(name)));
    }
    return names;
  }

  private static boolean isUnknownType(final TypeRef type) {
    return UNKNOWN.equals(type.name()) && type.arrayDepth() == 0;
  }

  private static boolean isError(final TypeRef type) {
    return type.name().endsWith(ERROR_SUFFIX);
  }
}
