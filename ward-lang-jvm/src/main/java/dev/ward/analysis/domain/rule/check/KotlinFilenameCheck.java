package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reports a Kotlin file whose name does not match its top-level declarations, as ktlint's {@code
 * filename} rule does: a file with a single public class is named after that class, and any other
 * file is named in PascalCase.
 */
public final class KotlinFilenameCheck implements UnitCheck {

  private static final Pattern PASCAL_CASE = Pattern.compile("[A-Z][A-Za-z\\d]*");
  private static final String KOTLIN_EXTENSION = ".kt";
  private static final String PACKAGE_FILE = "package.kt";
  private static final String TOP_LEVEL = "topLevelObject";
  private static final String CLASS = "classDeclaration";
  private static final String OBJECT = "objectDeclaration";
  private static final String FUN = "functionDeclaration";
  private static final String PROPERTY = "propertyDeclaration";
  private static final String IDENTIFIER = "simpleIdentifier";
  private static final String MODIFIERS = "modifierList";
  private static final String RECEIVER = "receiverType";
  private static final String PRIVATE = "private";
  private static final char SEPARATOR = '/';
  private static final char DOT = '.';
  private static final char BACKTICK = '`';
  private static final int SINGLE = 1;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the start of the file
   */
  public KotlinFilenameCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final String path = file.path().replace('\\', SEPARATOR);
    final String name = path.substring(path.lastIndexOf(SEPARATOR) + 1);
    if (!name.endsWith(KOTLIN_EXTENSION) || PACKAGE_FILE.equals(name)) {
      return List.of();
    }
    final String base = name.substring(0, name.indexOf(DOT));
    final List<SyntaxNode> declarations = publicDeclarations(root);
    if (matchesDeclarations(base, declarations)) {
      return List.of();
    }
    return List.of(CheckSupport.at(KotlinLeaves.fileStart(), message));
  }

  private static boolean matchesDeclarations(
      final String base, final List<SyntaxNode> declarations) {
    final List<SyntaxNode> classes = ofKind(declarations, CLASS);
    if (classes.size() == SINGLE) {
      final String className = identifier(classes.get(0));
      return hasNotExtending(declarations, className)
          ? PASCAL_CASE.matcher(base).matches()
          : base.equals(className);
    }
    if (declarations.size() == SINGLE && OBJECT.equals(declarations.get(0).kind())) {
      return base.equals(identifier(declarations.get(0)));
    }
    return PASCAL_CASE.matcher(base).matches();
  }

  private static boolean hasNotExtending(
      final List<SyntaxNode> declarations, final String className) {
    return declarations.stream()
        .anyMatch(
            declaration ->
                OBJECT.equals(declaration.kind())
                    || PROPERTY.equals(declaration.kind())
                    || (FUN.equals(declaration.kind()) && !extendsClass(declaration, className)));
  }

  private static boolean extendsClass(final SyntaxNode function, final String className) {
    return function.children().stream()
        .filter(child -> RECEIVER.equals(child.kind()))
        .anyMatch(receiver -> SyntaxWalk.text(receiver).contains(className));
  }

  private static List<SyntaxNode> publicDeclarations(final SyntaxNode root) {
    return root.children().stream()
        .filter(child -> TOP_LEVEL.equals(child.kind()))
        .flatMap(topLevel -> topLevel.children().stream())
        .filter(declaration -> !isPrivate(declaration))
        .toList();
  }

  private static boolean isPrivate(final SyntaxNode declaration) {
    return declaration.children().stream()
        .filter(child -> MODIFIERS.equals(child.kind()))
        .flatMap(modifiers -> SyntaxWalk.leaves(modifiers).stream())
        .anyMatch(leaf -> PRIVATE.equals(KotlinLeaves.text(leaf)));
  }

  private static List<SyntaxNode> ofKind(final List<SyntaxNode> nodes, final String kind) {
    return nodes.stream().filter(node -> kind.equals(node.kind())).toList();
  }

  private static String identifier(final SyntaxNode declaration) {
    final Optional<SyntaxNode> name =
        declaration.children().stream()
            .filter(child -> IDENTIFIER.equals(child.kind()))
            .findFirst();
    return name.map(SyntaxWalk::text)
        .map(text -> text.replace(String.valueOf(BACKTICK), ""))
        .orElse("");
  }
}
