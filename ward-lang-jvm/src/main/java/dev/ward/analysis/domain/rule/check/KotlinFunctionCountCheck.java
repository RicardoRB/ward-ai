package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a file, class, interface, object or enum that declares too many functions, as detekt's
 * {@code too-many-functions} does. A file counts its top-level functions; a class counts the
 * functions in its own body, not those of nested classes. Each container has its own threshold, and
 * a container is reported when its count reaches it. The ignore flags skip private, overriding and
 * deprecated functions.
 */
public final class KotlinFunctionCountCheck implements RuleCheck {

  private static final String FUNCTION = "functionDeclaration";
  private static final String MEMBER = "classMemberDeclaration";
  private static final String CLASS_BODY = "classBody";
  private static final String ENUM_BODY = "enumClassBody";
  private static final String INTERFACE = "'interface'";
  private static final String ENUM = "enum";
  private static final String PRIVATE = "private";
  private static final String OVERRIDE = "override";
  private static final String DEPRECATED = "Deprecated";
  private static final String TOP_LEVEL = "topLevelObject";
  private static final String FILE = "kotlinFile";
  private static final Set<String> CONTAINERS =
      Set.of("kotlinFile", "classDeclaration", "objectDeclaration");

  /**
   * The threshold of each container and the functions that do not count.
   *
   * @param files the threshold for a file's top-level functions
   * @param classes the threshold for a class
   * @param interfaces the threshold for an interface
   * @param objects the threshold for an object
   * @param enums the threshold for an enum class
   * @param ignorePrivate whether private functions are skipped
   * @param ignoreOverridden whether overriding functions are skipped
   * @param ignoreDeprecated whether deprecated functions are skipped
   */
  public record Policy(
      int files,
      int classes,
      int interfaces,
      int objects,
      int enums,
      boolean ignorePrivate,
      boolean ignoreOverridden,
      boolean ignoreDeprecated) {}

  private final Policy policy;
  private final String message;

  /**
   * Creates the check.
   *
   * @param policy the thresholds and ignore flags
   * @param message the message; {@code {value}} is the count and {@code {max}} the threshold
   */
  public KotlinFunctionCountCheck(final Policy policy, final String message) {
    this.policy = Objects.requireNonNull(policy, "policy");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CONTAINERS)) {
      final SyntaxNode node = visit.node();
      if (FILE.equals(node.kind())) {
        report(node, topLevel(node), policy.files(), matches);
      } else if ("objectDeclaration".equals(node.kind())) {
        report(node, members(node), policy.objects(), matches);
      } else {
        report(node, members(node), classThreshold(node), matches);
      }
    }
    return List.copyOf(matches);
  }

  private void report(
      final SyntaxNode node,
      final List<SyntaxNode> functions,
      final int threshold,
      final List<RuleMatch> out) {
    final long count = functions.stream().filter(this::counts).count();
    if (count >= threshold) {
      out.add(
          CheckSupport.at(
              node.span(),
              CheckSupport.fill(message, String.valueOf(count), String.valueOf(threshold))));
    }
  }

  private int classThreshold(final SyntaxNode clazz) {
    if (hasToken(clazz, INTERFACE)) {
      return policy.interfaces();
    }
    return isEnum(clazz) ? policy.enums() : policy.classes();
  }

  private boolean counts(final SyntaxNode function) {
    return !(policy.ignorePrivate() && hasModifier(function, PRIVATE))
        && !(policy.ignoreOverridden() && hasModifier(function, OVERRIDE))
        && !(policy.ignoreDeprecated() && hasModifier(function, DEPRECATED));
  }

  private static List<SyntaxNode> topLevel(final SyntaxNode file) {
    return file.children().stream()
        .filter(child -> TOP_LEVEL.equals(child.kind()))
        .flatMap(top -> top.children().stream())
        .filter(child -> FUNCTION.equals(child.kind()))
        .toList();
  }

  private static List<SyntaxNode> members(final SyntaxNode owner) {
    return body(owner)
        .map(
            body ->
                body.children().stream()
                    .filter(child -> MEMBER.equals(child.kind()))
                    .flatMap(member -> member.children().stream())
                    .filter(child -> FUNCTION.equals(child.kind()))
                    .toList())
        .orElse(List.of());
  }

  private static Optional<SyntaxNode> body(final SyntaxNode owner) {
    return owner.children().stream()
        .filter(child -> CLASS_BODY.equals(child.kind()) || ENUM_BODY.equals(child.kind()))
        .findFirst();
  }

  private static boolean isEnum(final SyntaxNode clazz) {
    return clazz.children().stream()
        .filter(child -> child.kind().startsWith("modifier"))
        .anyMatch(child -> hasLeaf(child, ENUM));
  }

  private static boolean hasModifier(final SyntaxNode function, final String text) {
    return function.children().stream()
        .filter(
            child -> child.kind().startsWith("modifier") || child.kind().endsWith("ModifierList"))
        .anyMatch(child -> hasLeaf(child, text));
  }

  private static boolean hasToken(final SyntaxNode node, final String token) {
    return node.children().stream().anyMatch(child -> token.equals(child.kind()));
  }

  private static boolean hasLeaf(final SyntaxNode node, final String text) {
    return SyntaxWalk.leaves(node).stream()
        .anyMatch(leaf -> text.equals(leaf.fields().get("text")));
  }
}
