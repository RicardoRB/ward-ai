package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Reports each field that is initialized to the default value of its declared type: null for an
 * object reference, zero for a numeric type, false for boolean and the null character for char. As
 * Checkstyle's ExplicitInitialization does, the default is read from the declared type's text, so a
 * type alias that means a primitive is not recognized. With the user option, only null is reported.
 */
public final class ExplicitInitializationCheck implements UnitCheck {

  private static final Set<String> NUMERIC =
      Set.of("byte", "short", "int", "long", "float", "double");
  private static final Set<String> CHAR_ZEROES = Set.of("'\\0'", "'\\u0000'");
  private static final Set<String> NOT_ENTERED = Set.of("classBody", "block");

  private final boolean onlyObjectReferences;
  private final String message;

  /**
   * Creates the check.
   *
   * @param onlyObjectReferences whether only null initializations are reported
   * @param message the message reported at each explicit default initialization
   */
  public ExplicitInitializationCheck(final boolean onlyObjectReferences, final String message) {
    this.onlyObjectReferences = onlyObjectReferences;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("fieldDeclaration"))) {
      final String type = typeOf(visit.node());
      for (final SyntaxNode declarator : declarators(visit.node())) {
        final Optional<String> value = initializer(declarator);
        if (value.isPresent() && isDefault(type, value.get())) {
          matches.add(CheckSupport.at(declarator.span(), message));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static String typeOf(final SyntaxNode field) {
    return SyntaxWalk.firstDescendant(field, "typeType", Set.of()).map(SyntaxWalk::text).orElse("");
  }

  private static List<SyntaxNode> declarators(final SyntaxNode node) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if ("variableDeclarator".equals(child.kind())) {
        found.add(child);
      } else if (!NOT_ENTERED.contains(child.kind())) {
        found.addAll(declarators(child));
      }
    }
    return found;
  }

  private static Optional<String> initializer(final SyntaxNode declarator) {
    return declarator.children().stream()
        .filter(child -> "variableInitializer".equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text);
  }

  private boolean isDefault(final String type, final String value) {
    if ("null".equals(value)) {
      return true;
    }
    if (onlyObjectReferences) {
      return false;
    }
    return "boolean".equals(type) && "false".equals(value)
        || NUMERIC.contains(type) && isZero(value)
        || "char".equals(type) && CHAR_ZEROES.contains(value);
  }

  private static boolean isZero(final String value) {
    final OptionalDouble number = MagicNumberCheck.value(value);
    return number.isPresent() && number.getAsDouble() == 0;
  }
}
