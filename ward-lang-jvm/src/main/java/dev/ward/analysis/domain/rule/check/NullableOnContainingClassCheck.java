package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a type-use {@code @Nullable} written before a nested type name such as {@code @Nullable
 * Outer.Inner}: it annotates {@code Outer}, not {@code Inner}. The annotation counts as type-use
 * only when the file imports one that is, from JSpecify or the Checker Framework; with another
 * {@code Nullable} the check stays silent.
 */
public final class NullableOnContainingClassCheck implements RuleCheck {

  private static final String TYPE_TYPE = "typeType";
  private static final String ANNOTATION = "annotation";
  private static final String CLASS_TYPE = "classOrInterfaceType";
  private static final String IMPORT = "importDeclaration";
  private static final String NULLABLE = "@Nullable";
  private static final String VARIABLE_MODIFIER = "variableModifier";
  private static final String MEMBER = "classBodyDeclaration";
  private static final Set<String> LOCAL_DECLARATIONS =
      Set.of("formalParameter", "localVariableDeclaration");
  private static final Set<String> MEMBER_DECLARATIONS =
      Set.of("fieldDeclaration", "methodDeclaration");
  private static final Pattern TYPE_USE_IMPORT =
      Pattern.compile(
          "^import(?:org\\.jspecify\\.annotations|org\\.checkerframework\\.checker\\.nullness\\.qual)"
              + "\\.Nullable;$");
  private static final Pattern NESTED = Pattern.compile("^[A-Z]\\w*(?:<[^>]*>)?\\.[A-Z]");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the type
   */
  public NullableOnContainingClassCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final boolean typeUse =
        SyntaxWalk.visits(root, Set.of(IMPORT)).stream()
            .anyMatch(visit -> TYPE_USE_IMPORT.matcher(SyntaxWalk.text(visit.node())).matches());
    if (!typeUse) {
      return List.of();
    }
    return SyntaxWalk.visits(root, Set.of(TYPE_TYPE)).stream()
        .filter(NullableOnContainingClassCheck::isNullableNested)
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static boolean isNullableNested(final SyntaxWalk.Visit visit) {
    final SyntaxNode type = visit.node();
    return annotationTexts(visit).contains(NULLABLE)
        && type.children().stream()
            .filter(child -> CLASS_TYPE.equals(child.kind()))
            .anyMatch(child -> NESTED.matcher(SyntaxWalk.text(child)).find());
  }

  /** Returns the annotations written before the type: on it, or on the declaration it types. */
  private static List<String> annotationTexts(final SyntaxWalk.Visit visit) {
    final List<String> texts = new ArrayList<>();
    visit.node().children().stream()
        .filter(child -> ANNOTATION.equals(child.kind()))
        .forEach(child -> texts.add(SyntaxWalk.text(child)));
    final List<SyntaxNode> up = visit.ancestors();
    if (up.isEmpty()) {
      return texts;
    }
    final SyntaxNode parent = up.get(up.size() - 1);
    if (LOCAL_DECLARATIONS.contains(parent.kind())) {
      parent.children().stream()
          .filter(child -> VARIABLE_MODIFIER.equals(child.kind()))
          .forEach(child -> texts.add(SyntaxWalk.text(child)));
    } else if (MEMBER_DECLARATIONS.contains(parent.kind())) {
      up.stream()
          .filter(node -> MEMBER.equals(node.kind()))
          .reduce((first, second) -> second)
          .ifPresent(member -> texts.addAll(MemberDeclarations.modifiers(member)));
    }
    return texts;
  }
}
