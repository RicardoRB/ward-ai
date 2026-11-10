package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports an identity {@code map} ({@code .map { $0 }}, {@code .map { element in element }}) on a
 * known sequence, where {@code Array(sequence)} is the type-safe spelling, like SwiftLint's {@code
 * typesafe_array_init}. The {@code map} is reported. The receiver type comes from the type facts;
 * when the facts do not answer, the syntax of the unit decides (an array literal, a name annotated
 * with an array or set type, a same-file type that conforms to a sequence, or a generic parameter
 * constrained by a sequence or collection). An unknown receiver gives no finding. shortcut: the
 * syntax fallback reads the whole unit's text, not the receiver's scope, so a name with the same
 * spelling in another scope can match; upgrade when the type facts answer every receiver.
 */
public final class SwiftTypesafeArrayInitCheck implements RuleCheck {

  private static final String CALL = "postfix_expression";
  private static final String MAP = "map";
  private static final String DOT = ".";
  private static final Pattern IDENTITY_MAP =
      Pattern.compile("^(.+)\\.map\\(?\\{(?:(\\w+)in)?(?:return)?(\\$0|\\w+)\\}\\)?$");
  private static final Set<String> SEQUENCE_NAMES =
      Set.of("Array", "ArraySlice", "ContiguousArray", "Set", "Dictionary");
  private static final int PARAMETER_GROUP = 2;
  private static final int BODY_GROUP = 3;
  private static final FactNeed NEED =
      new FactNeed(Set.of(CALL), Set.of(FactKind.TYPE, FactKind.SHAPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftTypesafeArrayInitCheck(final String message) {
    this.message = message;
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return find(root, UnitTypeFacts.none());
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    return find(root, types);
  }

  private List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final String file = SwiftTrees.compact(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode call : SwiftTrees.descendants(root, CALL)) {
      final Matcher identity = IDENTITY_MAP.matcher(SwiftTrees.compact(call));
      if (identity.matches() && isIdentity(identity)) {
        final List<SyntaxNode> leaves = SwiftTrees.leaves(call);
        final int dot = indexOfMap(leaves);
        if (dot > 0 && isSequence(leaves.subList(0, dot - 1), identity.group(1), types, file)) {
          matches.add(SwiftTrees.at(leaves.get(dot), message));
        }
      }
    }
    return List.copyOf(matches);
  }

  /** The closure returns its own parameter: {@code $0}, {@code x in x}, or {@code return $0}. */
  private static boolean isIdentity(final Matcher identity) {
    final String parameter =
        identity.group(PARAMETER_GROUP) != null ? identity.group(PARAMETER_GROUP) : "$0";
    return parameter.equals(identity.group(BODY_GROUP));
  }

  /** The index of the {@code map} identifier that follows the dot of the call, or -1. */
  private static int indexOfMap(final List<SyntaxNode> leaves) {
    for (int i = 1; i < leaves.size(); i++) {
      if (MAP.equals(leaves.get(i).fields().get("text"))
          && DOT.equals(leaves.get(i - 1).fields().get("text"))) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isSequence(
      final List<SyntaxNode> receiverLeaves,
      final String receiver,
      final UnitTypeFacts types,
      final String file) {
    final Span span = spanOf(receiverLeaves);
    final Optional<TypeRef> type = types.typeOf(span);
    if (type.isPresent()) {
      return isSequenceType(type.get(), types.shapeOf(span));
    }
    return SwiftSyntaxSequence.holds(receiver, file);
  }

  /** A type is a known sequence by its name, its array depth or its supertypes. */
  private static boolean isSequenceType(final TypeRef type, final Optional<TypeShape> shape) {
    if (type.arrayDepth() > 0 || SEQUENCE_NAMES.contains(type.name())) {
      return true;
    }
    return shape
        .map(
            s -> s.supertypes().stream().anyMatch(SwiftTypesafeArrayInitCheck::isSequenceSupertype))
        .orElse(false);
  }

  private static boolean isSequenceSupertype(final TypeRef supertype) {
    return "Sequence".equals(supertype.name()) || "Collection".equals(supertype.name());
  }

  private static Span spanOf(final List<SyntaxNode> leaves) {
    final Span first = leaves.get(0).span();
    final Span last = leaves.get(leaves.size() - 1).span();
    return new Span(
        first.startLine(),
        first.startColumn(),
        last.endLine(),
        last.endColumn(),
        first.startOffset(),
        last.endOffset());
  }
}
