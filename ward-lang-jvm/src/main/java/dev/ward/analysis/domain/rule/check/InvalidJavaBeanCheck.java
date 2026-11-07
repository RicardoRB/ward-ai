package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a bean of the configured packages that breaks the JavaBeans rules, as PMD's invalid
 * JavaBean does: each non-static, non-transient field needs a getter and a setter of its type, and
 * a class with constructors needs a no-arg one. No packages means nothing is reported, as PMD
 * requires the packages to be configured. The Serializable requirement is not checked.
 */
public final class InvalidJavaBeanCheck implements UnitCheck {

  private static final String UNIT = "compilationunit";
  private static final String PACKAGE = "packageDeclaration";
  private static final String PACKAGE_PREFIX = "package";
  private static final String STATIC = "static";
  private static final String TRANSIENT = "transient";
  private static final String CONSTRUCTOR = "constructorDeclaration";

  private final List<String> packages;
  private final String message;

  /**
   * Creates the check.
   *
   * @param packages the package hierarchies whose classes are beans
   * @param message the message; {@code {value}} is the field or class that breaks the rule
   */
  public InvalidJavaBeanCheck(final List<String> packages, final String message) {
    this.packages = List.copyOf(packages);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    if (packages.isEmpty()) {
      return List.of();
    }
    return SyntaxWalk.visits(root, Set.of(UNIT)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(unit -> inPackages(packageOf(unit)))
        .flatMap(unit -> SyntaxWalk.visits(unit, Set.of(MemberDeclarations.CLASS)).stream())
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> reportsOf(clazz).stream())
        .toList();
  }

  private boolean inPackages(final String own) {
    return packages.stream().anyMatch(pkg -> own.equals(pkg) || own.startsWith(pkg + "."));
  }

  private List<RuleMatch> reportsOf(final SyntaxNode clazz) {
    final List<SyntaxNode> members = ClassMembers.members(clazz);
    final List<SyntaxNode> methods =
        members.stream()
            .map(ClassMembers::declarationOf)
            .filter(declaration -> MemberDeclarations.METHOD.equals(declaration.kind()))
            .toList();
    final List<RuleMatch> reports = new ArrayList<>();
    if (!hasNoArgConstructor(members)) {
      reports.add(
          CheckSupport.at(clazz.span(), fill(MemberDeclarations.name(clazz) + " constructor")));
    }
    for (final SyntaxNode member : members) {
      if (isBeanField(member)) {
        reports.addAll(fieldReports(member, methods));
      }
    }
    return reports;
  }

  private List<RuleMatch> fieldReports(final SyntaxNode member, final List<SyntaxNode> methods) {
    final SyntaxNode field = ClassMembers.declarationOf(member);
    final Optional<String> type = ClassMembers.typeText(field);
    if (type.isEmpty()) {
      return List.of();
    }
    return ClassMembers.fieldNames(field).stream()
        .filter(name -> !BeanAccessors.complete(name, type.get(), methods))
        .map(name -> CheckSupport.at(member.span(), fill(name)))
        .toList();
  }

  private static boolean hasNoArgConstructor(final List<SyntaxNode> members) {
    final List<SyntaxNode> constructors =
        members.stream()
            .map(ClassMembers::declarationOf)
            .filter(declaration -> CONSTRUCTOR.equals(declaration.kind()))
            .toList();
    return constructors.isEmpty()
        || constructors.stream()
            .anyMatch(constructor -> ClassMetrics.parameterCount(constructor) == 0);
  }

  private static boolean isBeanField(final SyntaxNode member) {
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    final boolean field =
        MemberDeclarations.declaration(member)
            .filter(declaration -> MemberDeclarations.FIELD.equals(declaration.kind()))
            .isPresent();
    return field && !modifiers.contains(STATIC) && !modifiers.contains(TRANSIENT);
  }

  private String fill(final String value) {
    return CheckSupport.fill(message, value, "");
  }

  private static String packageOf(final SyntaxNode unit) {
    return unit.children().stream()
        .filter(child -> PACKAGE.equals(child.kind()))
        .findFirst()
        .map(child -> trimmed(SyntaxWalk.text(child), PACKAGE_PREFIX))
        .orElse("");
  }

  private static String trimmed(final String text, final String prefix) {
    final String body = text.startsWith(prefix) ? text.substring(prefix.length()) : text;
    return body.endsWith(";") ? body.substring(0, body.length() - 1) : body;
  }
}
