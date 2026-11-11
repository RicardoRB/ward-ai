package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an object or array created inside a loop, except where PMD's {@code
 * AvoidInstantiatingObjectsInLoops} allows it: directly in a {@code throw} or {@code return}, in a
 * statement followed by {@code break}, assigned to an array element, or passed to a method of a
 * {@link java.util.Collection}. The initialiser of a {@code for} and the iterated expression of a
 * for-each are not inside the loop. When the receiver's type is unknown, the collection exemption
 * does not apply.
 */
public final class LoopAllocationCheck extends FactsCheck {
  private static final String CREATOR = "creator";
  private static final String EXPRESSION = "expression";
  private static final String STATEMENT = "statement";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String FOR_CONTROL = "forControl";
  private static final Set<String> LOOPS = Set.of("'for'", "'while'", "'do'");
  private static final Set<String> OUTSIDE_PARTS = Set.of("enhancedForControl", "forInit");
  private static final Set<String> EXITS = Set.of("'throw'", "'return'");
  private static final String BREAK = "'break'";
  private static final String ASSIGN = "'='";
  private static final String INDEX = "'['";
  private static final String COLLECTION = "java.util.Collection";
  private static final int CONTROL_PATH = 2;
  private static final int ASSIGNMENT_PARTS = 3;
  private static final int RIGHT = 2;
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at each allocation
   */
  public LoopAllocationCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(CREATOR)).stream()
        .filter(visit -> inLoop(visit.ancestors()) && !allowed(visit.ancestors(), types))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static boolean inLoop(final List<SyntaxNode> up) {
    for (int i = up.size() - 1; i >= 0; i--) {
      if (isLoop(up.get(i)) && !outsideBody(up.subList(i + 1, up.size()))) {
        return true;
      }
    }
    return false;
  }

  private static boolean isLoop(final SyntaxNode node) {
    return STATEMENT.equals(node.kind()) && firstKindIn(node, LOOPS);
  }

  private static boolean firstKindIn(final SyntaxNode node, final Set<String> kinds) {
    return !node.children().isEmpty() && kinds.contains(node.children().get(0).kind());
  }

  private static boolean outsideBody(final List<SyntaxNode> below) {
    return below.size() >= CONTROL_PATH
        && FOR_CONTROL.equals(below.get(0).kind())
        && OUTSIDE_PARTS.contains(below.get(1).kind());
  }

  private static boolean allowed(final List<SyntaxNode> up, final UnitTypeFacts types) {
    return exits(up) || breakFollows(up) || arrayElementValue(up) || collectionArgument(up, types);
  }

  private static boolean exits(final List<SyntaxNode> up) {
    return up.size() >= CONTROL_PATH
        && firstKindIn(up.get(up.size() - CONTROL_PATH), EXITS)
        && STATEMENT.equals(up.get(up.size() - CONTROL_PATH).kind());
  }

  private static boolean breakFollows(final List<SyntaxNode> up) {
    for (int i = up.size() - 1; i > 0; i--) {
      if (BLOCK_STATEMENT.equals(up.get(i).kind())) {
        return nextIsBreak(up.get(i - 1), up.get(i));
      }
    }
    return false;
  }

  private static boolean nextIsBreak(final SyntaxNode block, final SyntaxNode statement) {
    final List<SyntaxNode> siblings = block.children();
    final int next = siblings.indexOf(statement) + 1;
    return next > 0
        && next < siblings.size()
        && siblings.get(next).children().stream()
            .anyMatch(child -> firstKindIn(child, Set.of(BREAK)));
  }

  private static boolean arrayElementValue(final List<SyntaxNode> up) {
    for (int i = up.size() - 1; i > 0; i--) {
      final List<SyntaxNode> parts = up.get(i - 1).children();
      if (EXPRESSION.equals(up.get(i - 1).kind())
          && parts.size() == ASSIGNMENT_PARTS
          && ASSIGN.equals(parts.get(1).kind())
          && parts.get(RIGHT).equals(up.get(i))) {
        final List<SyntaxNode> target = parts.get(0).children();
        return target.size() > 1 && INDEX.equals(target.get(1).kind());
      }
    }
    return false;
  }

  private static boolean collectionArgument(final List<SyntaxNode> up, final UnitTypeFacts types) {
    for (int i = up.size() - 1; i >= CONTROL_PATH; i--) {
      final SyntaxNode call = up.get(i - 1);
      final SyntaxNode access = up.get(i - CONTROL_PATH);
      if ("arguments".equals(up.get(i).kind())
          && "methodCall".equals(call.kind())
          && EXPRESSION.equals(access.kind())
          && access.children().size() == ASSIGNMENT_PARTS
          && access.children().get(RIGHT).equals(call)
          && isCollection(access.children().get(0), types)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isCollection(final SyntaxNode receiver, final UnitTypeFacts types) {
    return types.typeOf(receiver.span()).map(TypeRef::name).filter(COLLECTION::equals).isPresent()
        || types
            .membersOf(receiver.span())
            .filter(m -> m.ancestors().stream().anyMatch(a -> COLLECTION.equals(a.name())))
            .isPresent();
  }
}
