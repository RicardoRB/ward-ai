package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reports a {@code throw} of a value whose known type is not an error. */
public final class OnlyThrowErrorCheck extends TypedCheck {

  private static final String THROW = "throwStatement";
  private static final String ERROR_SUFFIX = "Error";

  private final String message;

  /** Creates the check with the message of the rule. */
  public OnlyThrowErrorCheck(final String message) {
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode statement : SyntaxText.nodesOf(root, THROW)) {
      for (final SyntaxNode thrown : SyntaxText.childrenOf(statement, SEQUENCE)) {
        final Optional<TypeRef> type = typeOf(types, thrown);
        if (type.isPresent() && !isUntyped(type.get()) && !isError(type.get())) {
          out.add(new RuleMatch(thrown.span(), message));
        }
      }
    }
    return out;
  }

  private static boolean isError(final TypeRef type) {
    return type.name().endsWith(ERROR_SUFFIX);
  }
}
