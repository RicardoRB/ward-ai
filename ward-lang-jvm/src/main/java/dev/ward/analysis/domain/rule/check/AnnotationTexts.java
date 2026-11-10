package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.regex.Pattern;

/** Reads the annotations written on a declaration, as compact texts such as {@code @Named("a")}. */
final class AnnotationTexts {

  private static final String VARIABLE_MODIFIER = "variableModifier";
  private static final String MARK = "@";
  private static final Pattern ARGUMENTS = Pattern.compile("\\(.*$", Pattern.DOTALL);

  private AnnotationTexts() {}

  /**
   * Returns the annotations of a declaration node: the modifiers of a type or member wrapper, or
   * the variable modifiers of a parameter or local variable.
   */
  static List<String> of(final SyntaxNode declaration) {
    final List<String> texts = new java.util.ArrayList<>(MemberDeclarations.modifiers(declaration));
    declaration.children().stream()
        .filter(child -> VARIABLE_MODIFIER.equals(child.kind()))
        .map(SyntaxWalk::text)
        .forEach(texts::add);
    return texts.stream().filter(text -> text.startsWith(MARK)).toList();
  }

  /** Returns the simple name of an annotation text: {@code Named} for {@code @a.Named("x")}. */
  static String simpleName(final String annotation) {
    final String bare = ARGUMENTS.matcher(annotation.substring(1)).replaceFirst("");
    return bare.substring(bare.lastIndexOf('.') + 1);
  }
}
