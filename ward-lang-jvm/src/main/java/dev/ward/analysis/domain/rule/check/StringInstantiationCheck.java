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
 * Reports {@code new String(value)} where the only argument is a {@code String}, as PMD's {@code
 * StringInstantiation} does: the copy is unnecessary. A constructor that decodes bytes or copies
 * characters, such as {@code new String(bytes, charset)} or {@code new String(chars)}, is not
 * reported. A string literal argument is reported even when its type is unknown.
 */
public final class StringInstantiationCheck extends FactsCheck {

  private static final String EXPRESSION = "expression";
  private static final String CREATOR = "creator";
  private static final String EXPRESSION_LIST = "expressionList";
  private static final String STRING = "java.lang.String";
  private static final Set<String> NAMES = Set.of("String", STRING);
  private static final String QUOTE = "\"";
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the creation
   */
  public StringInstantiationCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(CREATOR)).stream()
        .filter(visit -> copiesAString(visit.node(), types))
        .map(visit -> CheckSupport.at(creation(visit).span(), message))
        .toList();
  }

  /** The creation expression, which starts at {@code new}, where PMD reports it. */
  private static SyntaxNode creation(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    return up.isEmpty() ? visit.node() : up.get(up.size() - 1);
  }

  private static boolean copiesAString(final SyntaxNode creator, final UnitTypeFacts types) {
    final boolean string =
        creator.children().stream()
            .filter(part -> "createdName".equals(part.kind()))
            .map(SyntaxWalk::text)
            .anyMatch(NAMES::contains);
    final List<SyntaxNode> arguments =
        SyntaxWalk.descendants(creator).stream()
            .filter(node -> EXPRESSION_LIST.equals(node.kind()))
            .findFirst()
            .map(list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).toList())
            .orElse(List.of());
    return string && arguments.size() == 1 && isString(arguments.get(0), types);
  }

  private static boolean isString(final SyntaxNode argument, final UnitTypeFacts types) {
    return SyntaxWalk.text(argument).startsWith(QUOTE)
        || types
            .typeOf(argument.span())
            .map(TypeRef::name)
            .filter(STRING::equals)
            .filter(name -> types.typeOf(argument.span()).get().arrayDepth() == 0)
            .isPresent();
  }
}
