package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.Nullness;
import dev.ward.analysis.domain.types.NullnessAnnotations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The nullness rules of the Kotlin typer: what a written type says, what an inferred declaration
 * may claim, and when a use of a nullable variable is smart-cast to non-null by a guard.
 */
final class KotlinNullness {
  private static final String ATOMIC = "atomicExpression";
  private static final String LITERAL = "literalConstant";
  private static final String NULL_LITERAL = "null";
  private static final String VAR_KEYWORD = "'var'";
  private static final int SINGLE_CHILD = 1;
  private static final int MAX_GUARD_TEXT = 200;
  private static final int FIRST_GROUP = 1;
  private static final int SECOND_GROUP = 2;
  private static final Pattern NULL_CHECK_LEFT = Pattern.compile("^(\\w+)(!=|==)null$");
  private static final Pattern NULL_CHECK_RIGHT = Pattern.compile("^null(!=|==)(\\w+)$");
  private static final Pattern TYPE_CHECK = Pattern.compile("^(\\w+)!?is[A-Z]");
  private static final Pattern ELVIS_EXIT =
      Pattern.compile("^(\\w+)\\?:(return|throw|break|continue)");
  private static final Pattern REQUIRE_NOT_NULL =
      Pattern.compile("^(?:require|check)NotNull\\((\\w+)\\)");

  private KotlinNullness() {}

  /** Returns what a written type says of nullness: {@code T?} may be null, {@code T} is not. */
  static Nullness writtenNullness(final Optional<SyntaxNode> written) {
    return written
        .map(node -> Syntax.text(node).endsWith("?") ? Nullness.NULLABLE : Nullness.NON_NULL)
        .orElse(Nullness.UNKNOWN);
  }

  /**
   * Returns the nullness of a property: its written type, or for an inferred type only a literal
   * initializer. A {@code var} that may be null is unknown, because another thread can change it.
   */
  static Nullness propertyNullness(
      final SyntaxNode property,
      final Optional<SyntaxNode> written,
      final boolean literalInitializer) {
    final Nullness declared =
        written.isEmpty() && literalInitializer ? Nullness.NON_NULL : writtenNullness(written);
    final boolean mutable =
        property.children().stream().anyMatch(child -> VAR_KEYWORD.equals(child.kind()));
    return mutable && declared == Nullness.NULLABLE ? Nullness.UNKNOWN : declared;
  }

  /** Returns whether an expression is a non-null literal, with no operation applied to it. */
  static boolean isLiteral(final SyntaxNode expression) {
    SyntaxNode current = expression;
    while (!ATOMIC.equals(current.kind())) {
      if (current.children().size() != SINGLE_CHILD) {
        return false;
      }
      current = current.children().get(0);
    }
    return current.children().size() == SINGLE_CHILD
        && LITERAL.equals(current.children().get(0).kind())
        && !NULL_LITERAL.equals(Syntax.text(current.children().get(0)));
  }

  /**
   * Returns the first offset of a null check, type check, elvis exit or {@code requireNotNull} of
   * each name in {@code function}. A use before that offset is not smart-cast, so it stays unknown.
   */
  static Map<String, Integer> guardsIn(final SyntaxNode function) {
    final Map<String, Integer> guards = new HashMap<>();
    collectGuards(function, guards);
    return guards;
  }

  /**
   * Returns the nullness of a use of a variable. A nullable variable is unknown after a guard of
   * its name before the use, because the guard smart-casts it; a use outside any function is
   * unknown.
   */
  static Nullness useNullness(
      final Nullness declared,
      final SyntaxNode identifier,
      final Optional<Map<String, Integer>> guards) {
    if (declared != Nullness.NULLABLE) {
      return declared;
    }
    if (guards.isEmpty()) {
      return Nullness.UNKNOWN;
    }
    final Integer guard = guards.get().get(Syntax.text(identifier));
    return guard != null && guard < identifier.span().startOffset()
        ? Nullness.UNKNOWN
        : Nullness.NULLABLE;
  }

  /**
   * Returns the declaration's annotations, and for a Kotlin expression the marker that its nullness
   * is known to be, so that the nullness of a span reads from the annotations.
   */
  static List<String> annotations(final Typed typed, final Optional<DeclInfo> declaration) {
    final List<String> found =
        new ArrayList<>(declaration.map(FactValues::annotations).orElse(List.of()));
    if (typed.nullness() == Nullness.NON_NULL) {
      found.add(NullnessAnnotations.KOTLIN_NON_NULL);
    } else if (typed.nullness() == Nullness.NULLABLE) {
      found.add(NullnessAnnotations.KOTLIN_NULLABLE);
    }
    return found;
  }

  private static void collectGuards(final SyntaxNode node, final Map<String, Integer> guards) {
    final String text = Syntax.text(node);
    if (text.length() <= MAX_GUARD_TEXT) {
      guardName(text).ifPresent(name -> guards.merge(name, node.span().startOffset(), Math::min));
    }
    node.children().forEach(child -> collectGuards(child, guards));
  }

  private static Optional<String> guardName(final String text) {
    return firstGroup(NULL_CHECK_LEFT, text, FIRST_GROUP)
        .or(() -> firstGroup(NULL_CHECK_RIGHT, text, SECOND_GROUP))
        .or(() -> firstGroup(TYPE_CHECK, text, FIRST_GROUP))
        .or(() -> firstGroup(ELVIS_EXIT, text, FIRST_GROUP))
        .or(() -> firstGroup(REQUIRE_NOT_NULL, text, FIRST_GROUP));
  }

  private static Optional<String> firstGroup(
      final Pattern pattern, final String text, final int group) {
    final Matcher matcher = pattern.matcher(text);
    return matcher.find() ? Optional.of(matcher.group(group)) : Optional.empty();
  }
}
