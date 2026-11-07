package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Compares the Javadoc of each class member with its annotation. A member whose Javadoc contains
 * {@code tag} must carry {@code annotation}; with {@code methodsOnly}, only methods are checked.
 * The Javadoc is read from the source text, because comments are not in the tree.
 */
public final class JavadocAnnotationCheck implements UnitCheck {

  private static final String CLASS_BODY = "classBodyDeclaration";
  private static final String INTERFACE_BODY = "interfaceBodyDeclaration";
  private static final Set<String> METHOD_KINDS =
      Set.of("methodDeclaration", "interfaceMethodDeclaration", "genericMethodDeclaration");
  private static final String JAVADOC_OPEN = "/**";
  private static final String JAVADOC_CLOSE = "*/";

  private final String tag;
  private final String annotation;
  private final boolean methodsOnly;
  private final String message;

  /**
   * Creates the check.
   *
   * @param tag the Javadoc text that calls for the annotation
   * @param annotation the annotation, with its {@code @}
   * @param methodsOnly whether only methods are checked
   * @param message the message
   */
  public JavadocAnnotationCheck(
      final String tag, final String annotation, final boolean methodsOnly, final String message) {
    this.tag = Objects.requireNonNull(tag, "tag");
    this.annotation = Objects.requireNonNull(annotation, "annotation");
    this.methodsOnly = methodsOnly;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if ((CLASS_BODY.equals(node.kind()) || INTERFACE_BODY.equals(node.kind()))
          && !node.children().isEmpty()
          && isBad(node, file.text())) {
        matches.add(CheckSupport.at(node.children().get(0).span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean isBad(final SyntaxNode member, final String text) {
    final String javadoc = javadocBefore(member.children().get(0).span().startOffset(), text);
    if (javadoc == null || !javadoc.contains(tag)) {
      return false;
    }
    if (methodsOnly && !isMethod(member)) {
      return false;
    }
    return member.children().stream().noneMatch(this::hasAnnotation);
  }

  private boolean hasAnnotation(final SyntaxNode child) {
    return SyntaxWalk.text(child).trim().equals(annotation);
  }

  private static boolean isMethod(final SyntaxNode member) {
    return SyntaxWalk.descendants(member).stream()
        .anyMatch(node -> METHOD_KINDS.contains(node.kind()));
  }

  /** The Javadoc comment that ends just before {@code offset}, or {@code null}. */
  private static String javadocBefore(final int offset, final String text) {
    final String before = text.substring(0, Math.min(offset, text.length())).stripTrailing();
    if (!before.endsWith(JAVADOC_CLOSE)) {
      return null;
    }
    final int open = before.lastIndexOf(JAVADOC_OPEN);
    return open < 0 ? null : before.substring(open);
  }
}
