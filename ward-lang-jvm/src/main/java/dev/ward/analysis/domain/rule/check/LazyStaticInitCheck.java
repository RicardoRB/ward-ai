package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports the lazy initialization {@code if (F == null) F = new T();} of a static, non-volatile
 * field {@code F} in a method that holds no lock. One mode reports it when the new object is
 * touched after it is stored ({@code F.x()}), the other when it is not.
 */
public final class LazyStaticInitCheck implements RuleCheck {

  /** Which of the two bugs is reported. */
  public enum Mode {
    /** The object is only stored. */
    STORE_ONLY,
    /** The object is used after it is stored. */
    STORE_THEN_UPDATE
  }

  private static final String CLASS = "classDeclaration";
  private static final String STATEMENT = "statement";
  private static final String EXPRESSION = "expression";
  private static final String IF = "'if'";
  private static final String SYNCHRONIZED = "'synchronized'";
  private static final Set<String> PRIMITIVES =
      Set.of("int", "long", "short", "byte", "char", "boolean", "float", "double");

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode which of the two bugs is reported
   * @param message the message reported at the assignment
   */
  public LazyStaticInitCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS))) {
      final Set<String> fields = lazyFields(visit.node());
      if (!fields.isEmpty()) {
        JavaNodes.members(visit.node()).stream()
            .filter(member -> "methodDeclaration".equals(member.node().kind()))
            .filter(member -> !member.has("synchronized"))
            .forEach(member -> scan(member.node(), fields, matches));
      }
    }
    return List.copyOf(matches);
  }

  /** Returns the static fields that are neither volatile nor final and have an object type. */
  private static Set<String> lazyFields(final SyntaxNode clazz) {
    final Set<String> names = new java.util.HashSet<>();
    for (final SyntaxNode member : ClassMembers.fieldMembers(clazz)) {
      final List<String> modifiers = MemberDeclarations.modifiers(member);
      final SyntaxNode field = ClassMembers.declarationOf(member);
      final boolean object =
          ClassMembers.typeText(field).filter(type -> !PRIMITIVES.contains(type)).isPresent();
      if (modifiers.contains("static")
          && !modifiers.contains("volatile")
          && !modifiers.contains("final")
          && object) {
        names.addAll(ClassMembers.fieldNames(field));
      }
    }
    return names;
  }

  private void scan(final SyntaxNode method, final Set<String> fields, final List<RuleMatch> out) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(method, Set.of(STATEMENT))) {
      final SyntaxNode statement = visit.node();
      final List<SyntaxNode> parts = statement.children();
      if (!parts.isEmpty() && IF.equals(parts.get(0).kind()) && !locked(visit.ancestors())) {
        final List<SyntaxNode> conditions = JavaNodes.kids(statement, EXPRESSION);
        final List<SyntaxNode> branches = JavaNodes.kids(statement, STATEMENT);
        if (!conditions.isEmpty() && !branches.isEmpty()) {
          fields.stream()
              .filter(field -> nullCheck(SyntaxWalk.text(conditions.get(0)), field))
              .forEach(field -> assignment(branches.get(0), field, out));
        }
      }
    }
  }

  private static boolean nullCheck(final String condition, final String field) {
    return condition.equals(field + "==null") || condition.equals("null==" + field);
  }

  private static boolean locked(final List<SyntaxNode> ancestors) {
    return ancestors.stream()
        .anyMatch(a -> STATEMENT.equals(a.kind()) && startsWith(a, SYNCHRONIZED));
  }

  private static boolean startsWith(final SyntaxNode node, final String kind) {
    return !node.children().isEmpty() && kind.equals(node.children().get(0).kind());
  }

  private void assignment(final SyntaxNode branch, final String field, final List<RuleMatch> out) {
    final Pattern store = Pattern.compile("^(?:[\\w.]+\\.)?" + field + "=new[\\w.<>]*\\(.*;$");
    final Pattern use = Pattern.compile("(?<![\\w])" + field + "\\.");
    final String text = SyntaxWalk.text(branch);
    for (final SyntaxWalk.Visit inner : SyntaxWalk.visits(branch, Set.of(STATEMENT))) {
      final String stored = SyntaxWalk.text(inner.node());
      if (!locked(inner.ancestors()) && store.matcher(stored).matches()) {
        final String after = text.substring(text.indexOf(stored) + stored.length());
        if (use.matcher(after).find() == (mode == Mode.STORE_THEN_UPDATE)) {
          out.add(CheckSupport.at(inner.node().span(), message));
        }
      }
    }
  }
}
