package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a call on a value that another call returned, as PMD's law of Demeter does with its
 * default trust radius of 1: {@code value.get().use()} reaches one call too far through a value of
 * a type outside the class. The value must have a known type, so an unknown one reports nothing. A
 * chain that is passed or returned is not reported, as PMD does not report those either. A call is
 * trusted, and not reported, when it returns the type of its receiver (a builder), when the
 * receiver's type or the type the call returns is in {@code java.} or {@code javax.}, when the
 * receiver is a record, when the call is a getter without arguments, or when the receiver's type
 * matches a trusted pattern.
 */
public final class LawOfDemeterCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final int CHAIN_PARTS = 3;
  private static final String METHOD_CALL = "methodCall";
  private static final String DOT = ".";
  private static final String THIS = "this";
  private static final String SUPER = "super";
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE, FactKind.SHAPE, FactKind.CALL));
  private static final Pattern GETTER = Pattern.compile("(?:get|is)\\p{Lu}.*");
  private static final List<String> PLATFORM_PREFIXES = List.of("java.", "javax.");

  private final String message;
  private final List<Pattern> trusted;

  /**
   * Creates the check.
   *
   * @param message the message reported at the inner call; {@code {value}} is its type
   * @param trusted patterns that the whole qualified name of a receiver's type may match to be
   *     trusted
   */
  public LawOfDemeterCheck(final String message, final List<Pattern> trusted) {
    this.message = Objects.requireNonNull(message, "message");
    this.trusted = List.copyOf(trusted);
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .flatMap(visit -> reportOf(visit, types).stream())
        .toList();
  }

  private Optional<RuleMatch> reportOf(final SyntaxWalk.Visit visit, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = visit.node().children();
    if (parts.size() < CHAIN_PARTS
        || !EXPRESSION.equals(parts.get(0).kind())
        || !isDot(parts.get(1))) {
      return Optional.empty();
    }
    final SyntaxNode inner = parts.get(0);
    if (!isUntrustedCall(inner, enclosingClassName(visit.ancestors()), types)
        || isTrusted(inner, types)) {
      return Optional.empty();
    }
    final String type =
        types.typeOf(inner.children().get(0).span()).map(TypeRef::display).orElse("");
    return Optional.of(CheckSupport.at(inner.span(), CheckSupport.fill(message, type, "")));
  }

  /** Tells whether {@code inner} is {@code value.call()} on a value of a known outside type. */
  private static boolean isUntrustedCall(
      final SyntaxNode inner, final String own, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = inner.children();
    if (parts.size() != CHAIN_PARTS
        || !EXPRESSION.equals(parts.get(0).kind())
        || !isDot(parts.get(1))
        || !METHOD_CALL.equals(parts.get(2).kind())) {
      return false;
    }
    final SyntaxNode value = parts.get(0);
    final String text = SyntaxWalk.text(value);
    if (THIS.equals(text) || SUPER.equals(text) || hasCall(value)) {
      return false;
    }
    return types
        .typeOf(value.span())
        .map(TypeRef::name)
        .filter(name -> !ClassMembers.simpleName(name).equals(own))
        .isPresent();
  }

  /** Tells whether the call {@code value.call()} is one the rule trusts. */
  private boolean isTrusted(final SyntaxNode inner, final UnitTypeFacts types) {
    final SyntaxNode value = inner.children().get(0);
    final SyntaxNode call = inner.children().get(2);
    final String type = types.typeOf(value.span()).map(TypeRef::name).orElse("");
    final String result =
        types
            .typeOf(inner.span())
            .or(() -> types.callTarget(inner.span()).map(CallTarget::returnType))
            .map(TypeRef::name)
            .orElse("");
    final String method =
        call.children().stream()
            .filter(part -> "identifier".equals(part.kind()))
            .findFirst()
            .map(SyntaxWalk::text)
            .orElse("");
    return type.equals(result)
        || PLATFORM_PREFIXES.stream()
            .anyMatch(prefix -> type.startsWith(prefix) || result.startsWith(prefix))
        || types
            .shapeOf(value.span())
            .filter(shape -> shape.kind() == TypeShape.Kind.RECORD)
            .isPresent()
        || GETTER.matcher(method).matches() && CallArgumentNodes.all(inner).isEmpty()
        || trusted.stream().anyMatch(pattern -> pattern.matcher(type).matches());
  }

  private static boolean hasCall(final SyntaxNode expression) {
    return expression.children().stream().anyMatch(child -> METHOD_CALL.equals(child.kind()));
  }

  /** Returns the name of the nearest class around a node, or an empty name outside any class. */
  private static String enclosingClassName(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (MemberDeclarations.CLASS.equals(ancestors.get(index).kind())) {
        return ClassMembers.nameOf(ancestors.get(index));
      }
    }
    return "";
  }

  private static boolean isDot(final SyntaxNode part) {
    return DOT.equals(SyntaxWalk.text(part));
  }
}
