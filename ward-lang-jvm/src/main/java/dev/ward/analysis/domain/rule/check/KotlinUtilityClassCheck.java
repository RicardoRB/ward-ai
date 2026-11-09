package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a class whose body holds only a companion object, as detekt's {@code
 * UtilityClassWithPublicConstructor} does. A class with a supertype, a primary constructor, or a
 * modifier such as open, abstract, sealed or interface is left alone, since it is not a plain
 * utility class.
 */
public final class KotlinUtilityClassCheck implements RuleCheck {

  private static final String CLASS = "classDeclaration";
  private static final String BODY = "classBody";
  private static final String MEMBER = "classMemberDeclaration";
  private static final String COMPANION = "companionObject";
  private static final Set<String> NOT_PLAIN =
      Set.of("interface", "open", "abstract", "sealed", "annotation", "enum", "inner");
  private static final Set<String> NOT_PLAIN_CHILDREN =
      Set.of("primaryConstructor", "delegationSpecifiers");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public KotlinUtilityClassCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS))) {
      if (onlyCompanion(visit.node())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean onlyCompanion(final SyntaxNode node) {
    if (node.children().stream().anyMatch(child -> NOT_PLAIN_CHILDREN.contains(child.kind()))
        || header(node).stream().anyMatch(NOT_PLAIN::contains)) {
      return false;
    }
    final SyntaxNode body =
        node.children().stream()
            .filter(child -> BODY.equals(child.kind()))
            .findFirst()
            .orElse(null);
    if (body == null) {
      return false;
    }
    final List<SyntaxNode> members =
        body.children().stream().filter(child -> !child.children().isEmpty()).toList();
    return !members.isEmpty()
        && members.stream()
            .allMatch(
                member ->
                    MEMBER.equals(member.kind())
                        && member.children().stream()
                            .anyMatch(child -> COMPANION.equals(child.kind())));
  }

  private static List<String> header(final SyntaxNode node) {
    final List<String> words = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (!BODY.equals(child.kind())) {
        SyntaxWalk.leaves(child)
            .forEach(leaf -> words.add(String.valueOf(leaf.fields().get("text"))));
      }
    }
    return words;
  }
}
