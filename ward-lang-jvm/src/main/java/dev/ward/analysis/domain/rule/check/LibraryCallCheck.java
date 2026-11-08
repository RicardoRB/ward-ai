package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports calls of library methods that do nothing useful, resolved through the call target so that
 * a method of the same name elsewhere is never reported: a null check of a String constant, and an
 * EasyMock {@code replay}, {@code verify} or {@code reset} without mocks.
 */
public final class LibraryCallCheck implements RuleCheck {

  /** What the check reports. */
  public enum Mode {
    /** A null check or assertion applied to a String constant. */
    NULL_CHECK_OF_CONSTANT,
    /** An EasyMock call with an empty array of mocks. */
    EMPTY_MOCKS
  }

  private static final String EXPRESSION = "expression";
  private static final Pattern LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
  private static final Set<String> GUAVA_STRINGS =
      Set.of("nullToEmpty", "emptyToNull", "isNullOrEmpty");
  private static final String GUAVA_PRECONDITIONS = "com.google.common.base.Preconditions";
  private static final String GUAVA_STRING = "com.google.common.base.Strings";
  private static final Set<String> ASSERTIONS =
      Set.of("junit.framework.Assert", "org.junit.Assert", "org.junit.jupiter.api.Assertions");
  private static final String EASYMOCK = "org.easymock.EasyMock";
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL));

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what the check reports
   * @param message the message reported at the call
   */
  public LibraryCallCheck(final Mode mode, final String message) {
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
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(call -> reported(call, types))
        .map(call -> CheckSupport.at(call.span(), message))
        .toList();
  }

  private boolean reported(final SyntaxNode call, final UnitTypeFacts types) {
    return types
        .callTarget(call.span())
        .filter(
            target ->
                mode == Mode.EMPTY_MOCKS
                    ? emptyMocks(target, call)
                    : constantNullCheck(target, CallArgumentNodes.all(call)))
        .isPresent();
  }

  private static boolean emptyMocks(final CallTarget target, final SyntaxNode call) {
    final String name = target.name();
    return EASYMOCK.equals(target.declaringType().name())
        && ("replay".equals(name) || "verify".equals(name) || name.startsWith("reset"))
        && target.parameters().size() == 1
        && target.parameters().get(0).arrayDepth() == 1
        && CallArgumentNodes.all(call).isEmpty();
  }

  private static boolean constantNullCheck(final CallTarget target, final List<SyntaxNode> args) {
    final String owner = target.declaringType().name();
    final String name = target.name();
    if (args.isEmpty()) {
      return false;
    }
    if (GUAVA_PRECONDITIONS.equals(owner) && "checkNotNull".equals(name)) {
      return preconditions(args);
    }
    if (GUAVA_STRING.equals(owner) && GUAVA_STRINGS.contains(name)) {
      return args.size() == 1 && literal(args.get(0));
    }
    return ASSERTIONS.contains(owner) && "assertNotNull".equals(name) && assertion(args);
  }

  private static boolean preconditions(final List<SyntaxNode> args) {
    return literal(args.get(0)) && (args.size() == 1 || args.size() == 2 && literal(args.get(1)));
  }

  private static boolean assertion(final List<SyntaxNode> args) {
    return literal(args.get(args.size() - 1))
        && (args.size() == 1 || args.size() == 2 && literal(args.get(0)));
  }

  private static boolean literal(final SyntaxNode argument) {
    return LITERAL.matcher(SyntaxWalk.text(argument)).matches();
  }
}
