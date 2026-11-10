package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the shape of one class declaration in one file, for three SpotBugs patterns: methods whose
 * names differ only by case, a custom class loader whose {@code getPermissions} never calls the
 * superclass, and an iterator over map entries that hands out one entry object it holds in a field.
 */
public final class ClassShapeCheck implements RuleCheck {

  /** What the check looks for. */
  public enum Mode {
    /** Two methods of a class whose names differ only by case. */
    CASE_CONFUSION,
    /** A class loader whose getPermissions does not call super. */
    CLASS_LOADER_PERMISSIONS,
    /** An entry iterator whose next returns one field. */
    ENTRY_REUSE
  }

  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final Set<String> LOADERS =
      Set.of("ClassLoader", "SecureClassLoader", "URLClassLoader");
  private static final String GET_PERMISSIONS = "getPermissions";
  private static final String SUPER_CALL = "super.getPermissions(";
  private static final Pattern ENTRY_ITERATOR =
      Pattern.compile("^.*implements.*Iterator<.*Entry.*$");
  private static final Pattern RETURNS_NAME = Pattern.compile("return(?:this\\.)?(\\w+);");

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what the check looks for
   * @param message the message reported at each finding
   */
  public ClassShapeCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS_DECLARATION))) {
      matches.addAll(of(visit.node()));
    }
    return List.copyOf(matches);
  }

  private List<RuleMatch> of(final SyntaxNode clazz) {
    return switch (mode) {
      case CASE_CONFUSION -> caseConfusion(clazz);
      case CLASS_LOADER_PERMISSIONS -> permissions(clazz);
      default -> entryReuse(clazz);
    };
  }

  private List<RuleMatch> caseConfusion(final SyntaxNode clazz) {
    final List<String> seen = new ArrayList<>();
    final List<RuleMatch> matches = new ArrayList<>();
    for (final DeclaredMethods.Method method : DeclaredMethods.of(clazz)) {
      if (seen.stream()
          .anyMatch(name -> name.equalsIgnoreCase(method.name()) && !name.equals(method.name()))) {
        matches.add(CheckSupport.at(method.node().span(), message));
      }
      seen.add(method.name());
    }
    return matches;
  }

  private List<RuleMatch> permissions(final SyntaxNode clazz) {
    final boolean loader =
        ClassHeaders.extendsType(clazz)
            .map(ClassHeaders::simple)
            .filter(LOADERS::contains)
            .isPresent();
    if (!loader) {
      return List.of();
    }
    return DeclaredMethods.of(clazz).stream()
        .filter(method -> GET_PERMISSIONS.equals(method.name()))
        .filter(method -> !SyntaxWalk.text(method.node()).contains(SUPER_CALL))
        .map(method -> CheckSupport.at(method.node().span(), message))
        .toList();
  }

  private List<RuleMatch> entryReuse(final SyntaxNode clazz) {
    final String text = SyntaxWalk.text(clazz);
    if (!ENTRY_ITERATOR.matcher(text.substring(0, Math.max(0, text.indexOf('{')))).matches()) {
      return List.of();
    }
    final List<String> shared = new ArrayList<>();
    for (final SyntaxNode member : ClassMembers.fieldMembers(clazz)) {
      final SyntaxNode field = ClassMembers.declarationOf(member);
      final boolean entry =
          ClassMembers.typeText(field).filter(type -> type.contains("Entry")).isPresent();
      if (entry && !MemberDeclarations.modifiers(member).contains(MemberDeclarations.FINAL)) {
        shared.addAll(ClassMembers.fieldNames(field));
      }
    }
    return DeclaredMethods.of(clazz).stream()
        .filter(method -> "next".equals(method.name()))
        .filter(method -> returnsAny(SyntaxWalk.text(method.node()), shared))
        .map(method -> CheckSupport.at(method.node().span(), message))
        .toList();
  }

  private static boolean returnsAny(final String body, final List<String> fields) {
    return RETURNS_NAME.matcher(body).results().anyMatch(found -> fields.contains(found.group(1)));
  }
}
