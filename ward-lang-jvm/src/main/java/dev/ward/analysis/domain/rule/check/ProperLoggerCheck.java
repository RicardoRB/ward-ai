package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a logger field that is not {@code private static final}, or is not named as configured,
 * or is created for another class, as PMD's ProperLogger does. A logger is a field whose declared
 * type is the logger class, by its qualified or its simple name. An instance logger without an
 * initializer must be assigned a constructor parameter.
 */
public final class ProperLoggerCheck implements UnitCheck {

  private static final Pattern CLASS_LITERAL = Pattern.compile("(\\w+)\\.class");
  private static final String INITIALIZER = "=";

  private final String loggerClass;
  private final String staticName;
  private final String instanceName;
  private final String message;

  /**
   * Creates the check.
   *
   * @param loggerClass the qualified name of the logger type
   * @param staticName the name of a static logger
   * @param instanceName the name of an instance logger
   * @param message the message reported at the field
   */
  public ProperLoggerCheck(
      final String loggerClass,
      final String staticName,
      final String instanceName,
      final String message) {
    this.loggerClass = Objects.requireNonNull(loggerClass, "loggerClass");
    this.staticName = Objects.requireNonNull(staticName, "staticName");
    this.instanceName = Objects.requireNonNull(instanceName, "instanceName");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> reportsOf(clazz).stream())
        .toList();
  }

  private List<RuleMatch> reportsOf(final SyntaxNode clazz) {
    final String className = ClassMembers.nameOf(clazz);
    return ClassMembers.fieldMembers(clazz).stream()
        .filter(this::isLogger)
        .filter(member -> improper(member, clazz, className))
        .map(member -> CheckSupport.at(ClassMembers.declarationOf(member).span(), message))
        .toList();
  }

  private boolean isLogger(final SyntaxNode member) {
    return ClassMembers.typeText(ClassMembers.declarationOf(member))
        .filter(
            type -> type.equals(loggerClass) || type.equals(ClassMembers.simpleName(loggerClass)))
        .isPresent();
  }

  private boolean improper(
      final SyntaxNode member, final SyntaxNode clazz, final String className) {
    final SyntaxNode field = ClassMembers.declarationOf(member);
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    final boolean isStatic = modifiers.contains(MemberDeclarations.STATIC);
    final String expectedName = isStatic ? staticName : instanceName;
    final String text = SyntaxWalk.text(field);
    return !modifiers.contains(MemberDeclarations.PRIVATE)
        || !modifiers.contains(MemberDeclarations.FINAL)
        || ClassMembers.fieldNames(field).stream().anyMatch(name -> !name.equals(expectedName))
        || foreignClassLiteral(text, className)
        || !isStatic && !text.contains(INITIALIZER) && !assignedByConstructor(clazz, expectedName);
  }

  private static boolean foreignClassLiteral(final String text, final String className) {
    final Matcher literal = CLASS_LITERAL.matcher(text);
    while (literal.find()) {
      if (!literal.group(1).equals(className)) {
        return true;
      }
    }
    return false;
  }

  /** Tells whether a constructor assigns the logger from one of its own parameters. */
  private static boolean assignedByConstructor(final SyntaxNode clazz, final String name) {
    final Pattern assignment = Pattern.compile("^(?:this\\.)?" + Pattern.quote(name) + "=(\\w+)$");
    return ClassMembers.callableMembers(clazz).stream()
        .map(ClassMembers::declarationOf)
        .filter(declaration -> MemberDeclarations.CONSTRUCTOR.equals(declaration.kind()))
        .anyMatch(
            constructor -> {
              final List<String> parameters = parameterNames(constructor);
              return SyntaxWalk.visits(constructor, Set.of("expression")).stream()
                  .map(visit -> assignment.matcher(SyntaxWalk.text(visit.node())))
                  .anyMatch(match -> match.matches() && parameters.contains(match.group(1)));
            });
  }

  private static List<String> parameterNames(final SyntaxNode constructor) {
    return SyntaxWalk.visits(constructor, Set.of("formalParameter")).stream()
        .flatMap(visit -> ClassMembers.fieldNames(visit.node()).stream())
        .toList();
  }
}
