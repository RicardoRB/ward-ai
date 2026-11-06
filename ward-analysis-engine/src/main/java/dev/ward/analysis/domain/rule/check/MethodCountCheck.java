package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Fails each class or interface body that declares more methods than a limit: in total, or of one
 * visibility. A method of an interface is public unless it is private. A method without a modifier
 * in a class is package-private.
 */
public final class MethodCountCheck implements RuleCheck {

  private static final Set<String> BODIES = Set.of("classBody", "interfaceBody");
  private static final Set<String> MEMBERS = Set.of("classBodyDeclaration", "interfaceBodyDeclaration");
  private static final Set<String> METHODS =
      Set.of(
          "methodDeclaration",
          "genericMethodDeclaration",
          "interfaceMethodDeclaration",
          "genericInterfaceMethodDeclaration");
  private static final String INTERFACE_BODY = "interfaceBody";
  private static final String MODIFIER = "modifier";
  private static final String PRIVATE = "private";
  private static final String PROTECTED = "protected";
  private static final String PUBLIC = "public";
  private static final String PACKAGE = "package";

  private final Limits limits;
  private final String message;

  /**
   * Creates the check.
   *
   * @param limits the largest allowed method counts
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   */
  public MethodCountCheck(final Limits limits, final String message) {
    this.limits = Objects.requireNonNull(limits, "limits");
    this.message = Objects.requireNonNull(message, "message");
  }

  /**
   * The largest allowed number of methods: in total, and of each visibility.
   *
   * @param total the largest total
   * @param priv the largest number of private methods
   * @param pkg the largest number of package-private methods
   * @param prot the largest number of protected methods
   * @param pub the largest number of public methods
   */
  public record Limits(int total, int priv, int pkg, int prot, int pub) {

    /**
     * Validates the limits.
     *
     * @throws IllegalArgumentException if a limit is negative
     */
    public Limits {
      CheckSupport.nonNegative(total, "max-total");
      CheckSupport.nonNegative(priv, "max-private");
      CheckSupport.nonNegative(pkg, "max-package");
      CheckSupport.nonNegative(prot, "max-protected");
      CheckSupport.nonNegative(pub, "max-public");
    }
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, BODIES)) {
      final SyntaxNode body = visit.node();
      final List<String> visibilities = visibilities(body);
      report(body, visibilities.size(), limits.total(), matches);
      report(body, count(visibilities, PRIVATE), limits.priv(), matches);
      report(body, count(visibilities, PACKAGE), limits.pkg(), matches);
      report(body, count(visibilities, PROTECTED), limits.prot(), matches);
      report(body, count(visibilities, PUBLIC), limits.pub(), matches);
    }
    return List.copyOf(matches);
  }

  private void report(
      final SyntaxNode body, final int value, final int max, final List<RuleMatch> matches) {
    if (value > max) {
      matches.add(
          new RuleMatch(
              body.span(),
              CheckSupport.fill(message, String.valueOf(value), String.valueOf(max))));
    }
  }

  private static List<String> visibilities(final SyntaxNode body) {
    final List<String> found = new ArrayList<>();
    for (final SyntaxNode member : body.children()) {
      if (MEMBERS.contains(member.kind()) && declaresMethod(member)) {
        found.add(visibility(member, INTERFACE_BODY.equals(body.kind())));
      }
    }
    return found;
  }

  /** Tells whether a member declaration holds a method, not counting the body of a nested type. */
  private static boolean declaresMethod(final SyntaxNode member) {
    for (final SyntaxNode child : member.children()) {
      if (METHODS.contains(child.kind())
          || (!BODIES.contains(child.kind()) && declaresMethod(child))) {
        return true;
      }
    }
    return false;
  }

  private static String visibility(final SyntaxNode member, final boolean inInterface) {
    final String modifiers =
        member.children().stream()
            .filter(child -> MODIFIER.equals(child.kind()))
            .map(SyntaxWalk::text)
            .reduce("", (left, right) -> left + " " + right);
    if (modifiers.contains(PRIVATE)) {
      return PRIVATE;
    }
    if (modifiers.contains(PROTECTED)) {
      return PROTECTED;
    }
    if (modifiers.contains(PUBLIC) || inInterface) {
      return PUBLIC;
    }
    return PACKAGE;
  }

  private static int count(final List<String> visibilities, final String wanted) {
    return (int) visibilities.stream().filter(wanted::equals).count();
  }
}
