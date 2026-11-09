package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a Kotlin file whose only public top-level class or object has another name, as detekt's
 * {@code matching-declaration-name} does. The file name loses its {@code .kt} suffix and a
 * multiplatform target suffix such as {@code .jvm}. A file without a path is not checked.
 */
public final class KotlinFileNameCheck implements UnitCheck {

  private static final String FILE = "kotlinFile";
  private static final String KT = ".kt";
  private static final String KTS = ".kts";
  private static final String TOP_LEVEL = "topLevelObject";
  private static final String NAME = "simpleIdentifier";
  private static final String MODIFIERS = "modifierList";
  private static final String PRIVATE = "private";
  private static final String TYPE_ALIAS = "typeAlias";
  private static final Set<String> TYPES = Set.of("classDeclaration", "objectDeclaration");
  private static final Set<String> DECLARATIONS =
      Set.of(
          "classDeclaration",
          "objectDeclaration",
          "functionDeclaration",
          "propertyDeclaration",
          TYPE_ALIAS);
  private static final Set<String> TARGETS =
      Set.of(
          "ios",
          "android",
          "js",
          "jvm",
          "native",
          "iosArm64",
          "iosX64",
          "macosX64",
          "mingwX64",
          "linuxX64");

  private final boolean mustBeFirst;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mustBeFirst whether the declaration must be the first top-level declaration of the file
   * @param message the message; {@code {value}} is the file name without suffix
   */
  public KotlinFileNameCheck(final boolean mustBeFirst, final String message) {
    this.mustBeFirst = mustBeFirst;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    if (file.path().isEmpty()) {
      return List.of();
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(FILE))) {
      checkFile(visit.node(), fileNameWithoutSuffix(file.name()), matches);
    }
    return List.copyOf(matches);
  }

  private void checkFile(
      final SyntaxNode file, final String fileName, final List<RuleMatch> matches) {
    final List<SyntaxNode> declarations = declarations(file);
    final List<SyntaxNode> publicTypes =
        declarations.stream()
            .filter(decl -> TYPES.contains(decl.kind()) && !isPrivate(decl))
            .toList();
    if (publicTypes.size() != 1
        || (mustBeFirst && !declarations.get(0).equals(publicTypes.get(0)))
        || hasTypeAlias(declarations, fileName)) {
      return;
    }
    final SyntaxNode declaration = publicTypes.get(0);
    final Optional<SyntaxNode> name = child(declaration, NAME);
    if (name.isPresent() && !SyntaxWalk.text(name.get()).equals(fileName)) {
      matches.add(CheckSupport.at(name.get().span(), CheckSupport.fill(message, fileName, "")));
    }
  }

  private static List<SyntaxNode> declarations(final SyntaxNode file) {
    return file.children().stream()
        .filter(child -> TOP_LEVEL.equals(child.kind()))
        .flatMap(top -> top.children().stream())
        .filter(child -> DECLARATIONS.contains(child.kind()))
        .toList();
  }

  private static boolean isPrivate(final SyntaxNode declaration) {
    return child(declaration, MODIFIERS)
        .map(modifiers -> SyntaxWalk.text(modifiers).contains(PRIVATE))
        .orElse(false);
  }

  private static boolean hasTypeAlias(final List<SyntaxNode> declarations, final String name) {
    return declarations.stream()
        .filter(decl -> TYPE_ALIAS.equals(decl.kind()))
        .anyMatch(
            decl -> child(decl, NAME).map(n -> SyntaxWalk.text(n).equals(name)).orElse(false));
  }

  private static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(c -> kind.equals(c.kind())).findFirst();
  }

  private static String fileNameWithoutSuffix(final String fileName) {
    final String base =
        fileName.endsWith(KTS) ? fileName.substring(0, fileName.length() - KTS.length()) : fileName;
    final String name = base.endsWith(KT) ? base.substring(0, base.length() - KT.length()) : base;
    final int dot = name.lastIndexOf('.');
    if (dot >= 0 && TARGETS.contains(name.substring(dot + 1))) {
      return name.substring(0, dot);
    }
    return name;
  }
}
