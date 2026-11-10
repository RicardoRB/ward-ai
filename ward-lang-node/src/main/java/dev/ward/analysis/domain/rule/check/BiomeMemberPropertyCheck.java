package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a property name that the code reads as a member: the name of a dotted access ({@code
 * x.name}, {@code x?.name}), a computed string key ({@code x['name']}) or a destructured property
 * ({@code const { name } = x}). An identifier that is only a variable is not reported.
 */
public final class BiomeMemberPropertyCheck implements RuleCheck {

  private static final Set<String> DOT_OPERATORS = Set.of("'.'", "'?.'");
  private static final String OPEN_BRACKET = "'['";
  private static final int GRANDPARENT = 1;
  private static final int SEQUENCE_LEVEL = 2;
  private static final int INDEX_LEVEL = 3;
  private static final int ARRAY_ELEMENT_LEVEL = 2;
  private static final int ELEMENT_LIST_LEVEL = 3;
  private static final int ARRAY_LEVEL = 4;
  private static final int WRAPPER_LEVEL = 5;
  private static final int NAME_LEVEL = 6;
  private static final int MIN_ARRAY_DEPTH = 7;
  private static final int MIN_INDEX_DEPTH = 4;
  private static final int MIN_PATTERN_DEPTH = 4;
  private static final int OBJECT_LEVEL = 2;
  private static final int PATTERN_LEVEL = 3;
  private static final int WRAPPER_PATTERN_LEVEL = 4;
  private static final int MIN_WRAPPED_DEPTH = 5;

  private final Set<String> kinds;
  private final Pattern text;
  private final String message;

  /**
   * Creates the check.
   *
   * @param kinds the node kinds of a candidate, such as {@code identifier} or {@code StringLiteral}
   * @param text the pattern the candidate's text must match
   * @param message the message; {@code {value}} is the candidate's text
   */
  public BiomeMemberPropertyCheck(
      final Set<String> kinds, final Pattern text, final String message) {
    this.kinds = Set.copyOf(kinds);
    this.text = text;
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, kinds).stream()
        .filter(visit -> text.matcher(SyntaxWalk.text(visit.node())).find())
        .filter(visit -> isMemberRead(visit.node(), visit.ancestors()))
        .map(
            visit ->
                CheckSupport.at(
                    visit.node().span(),
                    CheckSupport.fill(message, SyntaxWalk.text(visit.node()), "")))
        .toList();
  }

  private static boolean isMemberRead(final SyntaxNode node, final List<SyntaxNode> ancestors) {
    if (BiomeTree.isKind(node, "StringLiteral")) {
      return computedKey(ancestors);
    }
    return dottedName(ancestors) || destructuredName(ancestors);
  }

  /**
   * The string is the only element of an array literal that indexes a bare name: the grammar reads
   * {@code name['key']} as the name followed by an array literal.
   */
  private static boolean computedKey(final List<SyntaxNode> ancestors) {
    return indexedArray(ancestors) || indexedMember(ancestors);
  }

  /**
   * The JavaScript grammar reads {@code name['key']} as an index whose sequence holds the string.
   */
  private static boolean indexedMember(final List<SyntaxNode> ancestors) {
    return ancestors.size() >= MIN_INDEX_DEPTH
        && BiomeTree.isKind(BiomeTree.up(ancestors, SEQUENCE_LEVEL), "expressionSequence")
        && BiomeTree.hasChild(BiomeTree.up(ancestors, INDEX_LEVEL), OPEN_BRACKET);
  }

  private static boolean indexedArray(final List<SyntaxNode> ancestors) {
    return ancestors.size() >= MIN_ARRAY_DEPTH
        && BiomeTree.isKind(BiomeTree.up(ancestors, ARRAY_ELEMENT_LEVEL), "arrayElement")
        && BiomeTree.isKind(BiomeTree.up(ancestors, ELEMENT_LIST_LEVEL), "elementList")
        && BiomeTree.isKind(BiomeTree.up(ancestors, ARRAY_LEVEL), "arrayLiteral")
        && indexesName(BiomeTree.up(ancestors, WRAPPER_LEVEL), BiomeTree.up(ancestors, NAME_LEVEL));
  }

  /** The wrapper holds the array literal, and its parent is the name followed by the wrapper. */
  private static boolean indexesName(final SyntaxNode wrapper, final SyntaxNode name) {
    final List<SyntaxNode> parts = name.children();
    return BiomeTree.isKind(wrapper, "singleExpression")
        && BiomeTree.hasChild(wrapper, "arrayLiteral")
        && parts.size() == 2
        && BiomeTree.isKind(parts.get(0), "identifierName")
        && parts.get(1).equals(wrapper);
  }

  /** The name sits in a member access: identifier name, then the expression with a dot. */
  private static boolean dottedName(final List<SyntaxNode> ancestors) {
    return ancestors.size() >= 2
        && BiomeTree.isKind(BiomeTree.up(ancestors, 0), "identifierName")
        && BiomeTree.up(ancestors, 1).children().stream()
            .anyMatch(child -> DOT_OPERATORS.contains(child.kind()));
  }

  /** The name is a shorthand property of the object pattern that declares a variable. */
  private static boolean destructuredName(final List<SyntaxNode> ancestors) {
    return ancestors.size() >= MIN_PATTERN_DEPTH
        && BiomeTree.isKind(BiomeTree.up(ancestors, GRANDPARENT), "propertyAssignment")
        && BiomeTree.isKind(BiomeTree.up(ancestors, OBJECT_LEVEL), "objectLiteral")
        && declaresVariable(ancestors);
  }

  /** The pattern is the binding of a declaration, directly or through the JavaScript wrapper. */
  private static boolean declaresVariable(final List<SyntaxNode> ancestors) {
    final String parent = BiomeTree.up(ancestors, PATTERN_LEVEL).kind();
    return "variableDeclaration".equals(parent)
        || "assignable".equals(parent)
            && ancestors.size() >= MIN_WRAPPED_DEPTH
            && BiomeTree.isKind(
                BiomeTree.up(ancestors, WRAPPER_PATTERN_LEVEL), "variableDeclaration");
  }
}
