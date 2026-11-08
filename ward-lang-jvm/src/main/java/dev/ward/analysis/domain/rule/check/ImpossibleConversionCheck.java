package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a cast or {@code instanceof} that can never succeed because of what a local variable
 * holds: the value last assigned to it is of exactly one known type, and the target is neither that
 * type nor one above it. The types come from a data-flow analysis over the control-flow graph of
 * each body, so a variable declared as {@code Object} but set to a {@code String} is known as a
 * {@code String}. It lowers the control flow itself, since a rule reads either types or flow.
 */
public final class ImpossibleConversionCheck implements RuleCheck {

  /** What the check reports. */
  public enum Mode {
    /** A cast between types where neither is above the other. */
    CAST,
    /** A cast of a value of exactly one type to a subtype of it. */
    DOWNCAST,
    /** An {@code instanceof} test that is always false. */
    INSTANCEOF,
    /** A cast of a primitive array to another primitive array type. */
    PRIMITIVE_ARRAY_CAST
  }

  private static final String EXPRESSION = "expression";
  private static final String TYPE_TYPE = "typeType";
  private static final int CAST_PARTS = 4;
  private static final int CAST_TYPE = 1;
  private static final int CAST_OPERAND = 3;
  private static final int INSTANCEOF_PARTS = 3;
  private static final Pattern NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
  private static final FactNeed NEED =
      new FactNeed(
          Set.of(EXPRESSION, TYPE_TYPE), Set.of(FactKind.TYPE, FactKind.SHAPE, FactKind.MEMBERS));

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what the check reports
   * @param message the message reported at the conversion
   */
  public ImpossibleConversionCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
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
    final List<RuleMatch> matches = new ArrayList<>();
    final TypeState state = new TypeState(types);
    for (final ControlFlowGraph graph : new JavaControlFlow().lower(unit, root).graphs()) {
      state.walk(
          graph,
          (node, block, known) -> {
            for (final SyntaxNode candidate : withDescendants(node)) {
              if (impossible(candidate, block, known, types)) {
                matches.add(CheckSupport.at(candidate.span(), message));
              }
            }
          });
    }
    return List.copyOf(matches);
  }

  private static List<SyntaxNode> withDescendants(final SyntaxNode node) {
    final List<SyntaxNode> all = new ArrayList<>(List.of(node));
    all.addAll(SyntaxWalk.descendants(node));
    return all;
  }

  private boolean impossible(
      final SyntaxNode node,
      final BasicBlock block,
      final Map<Span, TypeState.Known> known,
      final UnitTypeFacts types) {
    if (!EXPRESSION.equals(node.kind())) {
      return false;
    }
    final List<SyntaxNode> parts = node.children();
    final boolean cast =
        parts.size() == CAST_PARTS
            && "(".equals(SyntaxWalk.text(parts.get(0)))
            && TYPE_TYPE.equals(parts.get(CAST_TYPE).kind());
    final boolean test =
        parts.size() >= INSTANCEOF_PARTS && "instanceof".equals(SyntaxWalk.text(parts.get(1)));
    final boolean wanted = mode == Mode.INSTANCEOF ? test : cast;
    if (!wanted) {
      return false;
    }
    final SyntaxNode operand = cast ? parts.get(CAST_OPERAND) : parts.get(0);
    final SyntaxNode target = cast ? parts.get(CAST_TYPE) : typeOfTest(parts);
    return held(operand, block, known)
        .flatMap(
            value -> types.typeOf(target.span()).map(type -> conflict(value, type, target, types)))
        .orElse(false);
  }

  private static SyntaxNode typeOfTest(final List<SyntaxNode> parts) {
    final SyntaxNode last = parts.get(parts.size() - 1);
    return TYPE_TYPE.equals(last.kind())
        ? last
        : SyntaxWalk.descendants(last).stream()
            .filter(node -> TYPE_TYPE.equals(node.kind()))
            .findFirst()
            .orElse(last);
  }

  /** Returns what the local variable named by {@code operand} holds, if it is known. */
  private static Optional<TypeState.Known> held(
      final SyntaxNode operand, final BasicBlock block, final Map<Span, TypeState.Known> known) {
    if (!NAME.matcher(SyntaxWalk.text(operand)).matches()) {
      return Optional.empty();
    }
    return block.accesses().stream()
        .filter(access -> access.kind() == AccessKind.READ && access.span().equals(operand.span()))
        .map(VariableAccess::declaration)
        .map(known::get)
        .filter(Objects::nonNull)
        .findFirst();
  }

  private boolean conflict(
      final TypeState.Known value,
      final TypeRef wanted,
      final SyntaxNode target,
      final UnitTypeFacts types) {
    if (!value.exact()) {
      return false;
    }
    return mode == Mode.PRIMITIVE_ARRAY_CAST
        ? primitiveArrays(value.type(), wanted)
        : unrelatedClasses(value, wanted, target, types);
  }

  private boolean unrelatedClasses(
      final TypeState.Known value,
      final TypeRef wanted,
      final SyntaxNode target,
      final UnitTypeFacts types) {
    final boolean classes = value.type().arrayDepth() == 0 && wanted.arrayDepth() == 0;
    final boolean related =
        value.type().name().equals(wanted.name()) || value.ancestors().contains(wanted.name());
    if (!classes || related || !value.ancestorsKnown()) {
      return false;
    }
    if (mode == Mode.INSTANCEOF) {
      return true;
    }
    final Set<String> above =
        types
            .membersOf(target.span())
            .map(members -> Set.copyOf(members.ancestors().stream().map(TypeRef::name).toList()))
            .orElse(Set.of());
    return !above.isEmpty() && above.contains(value.type().name()) == (mode == Mode.DOWNCAST);
  }

  private static boolean primitiveArrays(final TypeRef held, final TypeRef wanted) {
    return held.primitive()
        && wanted.primitive()
        && held.arrayDepth() > 0
        && wanted.arrayDepth() > 0
        && (!held.name().equals(wanted.name()) || held.arrayDepth() != wanted.arrayDepth());
  }
}
