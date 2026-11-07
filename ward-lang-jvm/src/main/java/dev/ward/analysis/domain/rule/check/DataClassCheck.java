package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a class that reveals its data through public fields and accessors, and has little
 * functionality, as PMD's data class does. The class is reported when its weight of class (WOC) is
 * below one third and its public fields and accessors (NOPA + NOAM) and its complexity (WMC) pass
 * PMD's levels: more than three of the first, and either under 31 complexity or over five of the
 * first with under 47.
 */
public final class DataClassCheck implements UnitCheck {

  private static final double WOC_LIMIT = 1.0 / 3.0;
  private static final int FEW_REVEALED = 3;
  private static final int MANY_REVEALED = 5;
  private static final int WMC_HIGH = 31;
  private static final int WMC_VERY_HIGH = 47;
  private static final int PERCENT = 100;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the class; {@code {value}} is the metrics
   */
  public DataClassCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> reportOf(clazz).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode clazz) {
    final List<SyntaxNode> members = ClassMembers.members(clazz);
    final int wmc = weightedMethods(clazz);
    final int revealed = publicFields(members) + publicAccessors(members);
    final double woc = weightOfClass(members);
    if (woc >= WOC_LIMIT || !revealsDataAndLacksComplexity(revealed, wmc)) {
      return List.of();
    }
    final String metrics =
        "WMC " + wmc + ", WOC " + Math.round(woc * PERCENT) + "%, NOPA+NOAM " + revealed;
    return List.of(CheckSupport.at(clazz.span(), CheckSupport.fill(message, metrics, "")));
  }

  private static boolean revealsDataAndLacksComplexity(final int revealed, final int wmc) {
    if (revealed <= FEW_REVEALED) {
      return false;
    }
    return wmc < WMC_HIGH || revealed > MANY_REVEALED && wmc < WMC_VERY_HIGH;
  }

  private static int weightedMethods(final SyntaxNode clazz) {
    return ClassMembers.callableMembers(clazz).stream()
        .mapToInt(member -> ClassMetrics.complexity(ClassMembers.declarationOf(member)))
        .sum();
  }

  /** Returns the share of public methods that do more than a getter or setter. */
  private static double weightOfClass(final List<SyntaxNode> members) {
    final List<SyntaxNode> publicMethods = publicMethods(members);
    if (publicMethods.isEmpty()) {
      return 0;
    }
    final long functional =
        publicMethods.stream().filter(method -> !ClassMetrics.isAccessor(method)).count();
    return (double) functional / publicMethods.size();
  }

  private static List<SyntaxNode> publicMethods(final List<SyntaxNode> members) {
    return members.stream()
        .filter(DataClassCheck::isPublic)
        .map(ClassMembers::declarationOf)
        .filter(declaration -> MemberDeclarations.METHOD.equals(declaration.kind()))
        .toList();
  }

  private static int publicAccessors(final List<SyntaxNode> members) {
    return (int) publicMethods(members).stream().filter(ClassMetrics::isAccessor).count();
  }

  private static int publicFields(final List<SyntaxNode> members) {
    return members.stream()
        .filter(DataClassCheck::isPublic)
        .map(ClassMembers::declarationOf)
        .filter(declaration -> MemberDeclarations.FIELD.equals(declaration.kind()))
        .mapToInt(field -> ClassMembers.fieldNames(field).size())
        .sum();
  }

  private static boolean isPublic(final SyntaxNode member) {
    return MemberDeclarations.modifiers(member).contains(MemberDeclarations.PUBLIC);
  }
}
