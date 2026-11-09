package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a Kotlin property with a literal value that could be a {@code const val}, as detekt's
 * {@code MayBeConst} does. Only top-level properties and members of an {@code object} or a
 * companion object qualify; a local or a class member never can be const. A property with a
 * visibility modifier only, a literal value and no getter or delegate is reported. An override,
 * open or annotated property is left alone.
 */
public final class KotlinMayBeConstCheck implements RuleCheck {

  private static final String PROPERTY = "propertyDeclaration";

  /** The ancestors of a member of an object: its class member, its body and the object. */
  private static final int OBJECT_MEMBER_DEPTH = 3;

  private static final String TOP_LEVEL = "topLevelObject";
  private static final String MEMBER = "classMemberDeclaration";
  private static final String CLASS_BODY = "classBody";
  private static final Set<String> OBJECT_KINDS = Set.of("objectDeclaration", "companionObject");
  private static final Pattern CONSTANT_PROPERTY =
      Pattern.compile(
          "(?:(?:public|internal|protected|private))?val\\w+"
              + "(?::(?:String|Int|Long|Short|Byte|Double|Float|Boolean))?"
              + "=(?:\"[^\"$\\n]*\"|-?\\d[\\w.]*|true|false)");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public KotlinMayBeConstCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(PROPERTY))) {
      if (declaredAtConstantLevel(visit.ancestors())
          && CONSTANT_PROPERTY.matcher(SyntaxWalk.text(visit.node())).matches()) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean declaredAtConstantLevel(final List<SyntaxNode> ancestors) {
    final int size = ancestors.size();
    return size >= 1 && TOP_LEVEL.equals(ancestors.get(size - 1).kind())
        || size >= OBJECT_MEMBER_DEPTH
            && MEMBER.equals(ancestors.get(size - 1).kind())
            && CLASS_BODY.equals(ancestors.get(size - 2).kind())
            && OBJECT_KINDS.contains(ancestors.get(size - OBJECT_MEMBER_DEPTH).kind());
  }
}
