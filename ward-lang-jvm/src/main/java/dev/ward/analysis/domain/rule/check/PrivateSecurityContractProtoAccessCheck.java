package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a call of a method named {@code ...PrivateDoNotAccessOrElse...} on one of the SafeHtml
 * protocol buffers or their builders from outside {@code com.google.common.html.types}, as Error
 * Prone's PrivateSecurityContractProtoAccess does. A call whose target is unknown is never
 * reported.
 */
public final class PrivateSecurityContractProtoAccessCheck extends FactsCheck {

  private static final String TYPES_PACKAGE = "com.google.common.html.types";
  private static final String PRIVATE = "PrivateDoNotAccessOrElse";
  private static final String EXPRESSION = "expression";
  private static final String PACKAGE = "packageDeclaration";
  private static final Set<String> PROTOS =
      Set.of(
          TYPES_PACKAGE + ".SafeHtmlProto",
          TYPES_PACKAGE + ".SafeUrlProto",
          TYPES_PACKAGE + ".TrustedResourceUrlProto",
          TYPES_PACKAGE + ".SafeScriptProto",
          TYPES_PACKAGE + ".SafeStyleProto",
          TYPES_PACKAGE + ".SafeStyleSheetProto");
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public PrivateSecurityContractProtoAccessCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    if (SyntaxWalk.visits(root, Set.of(PACKAGE)).stream()
        .anyMatch(visit -> SyntaxWalk.text(visit.node()).startsWith("package" + TYPES_PACKAGE))) {
      return List.of();
    }
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(call -> SyntaxWalk.text(call).endsWith(")"))
        .filter(call -> privateAccess(call, types))
        .map(call -> CheckSupport.at(call.span(), message))
        .toList();
  }

  private static boolean privateAccess(final SyntaxNode call, final UnitTypeFacts types) {
    return types
        .callTarget(call.span())
        .filter(target -> !target.isStatic() && target.name().contains(PRIVATE))
        .filter(target -> protoOrBuilder(target.declaringType().name()))
        .isPresent();
  }

  private static boolean protoOrBuilder(final String type) {
    return PROTOS.contains(type)
        || PROTOS.stream()
            .anyMatch(proto -> type.equals(proto + ".Builder") || type.equals(proto + "$Builder"));
  }
}
