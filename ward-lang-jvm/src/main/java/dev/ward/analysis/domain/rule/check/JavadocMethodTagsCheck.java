package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the tags of the Javadoc of a method or constructor against its signature, as Checkstyle's
 * JavadocMethod does with its defaults: every parameter has a {@code @param} tag, no {@code @param}
 * tag names something else, and a method that returns a value has a {@code @return} tag. A method
 * with an {@code @Override} annotation or {@code {@inheritDoc}} is not checked.
 */
public final class JavadocMethodTagsCheck implements UnitCheck {

  private static final Set<String> MODIFIER_KINDS =
      Set.of("modifier", "classOrInterfaceModifier", "variableModifier", "annotation");
  private static final Set<String> METHODS = Set.of("methodDeclaration", "constructorDeclaration");
  private static final Set<String> NAME_SKIPPED =
      Set.of("typeTypeOrVoid", "typeType", "typeParameters", "formalParameters", "annotation");
  private static final Set<String> BODIES = Set.of("block", "methodBody");
  private static final String PARAM = "@param";
  private static final String RETURN = "@return";
  private static final String INHERIT = "{@inheritDoc}";
  private static final String OVERRIDE = "Override";
  private static final String VOID = "void";
  private static final String TYPE_PARAMETER = "<";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} says what is wrong
   */
  public JavadocMethodTagsCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, METHODS)) {
      final Declaration declaration = Declaration.of(visit, MODIFIER_KINDS);
      final Optional<SyntaxNode> comment =
          declaration.previousSibling().filter(JavadocTags::isJavadoc);
      if (comment.isPresent() && !skipped(comment.get(), declaration)) {
        checkTags(visit.node(), comment.get(), matches);
      }
    }
    return List.copyOf(matches);
  }

  private static boolean skipped(final SyntaxNode comment, final Declaration declaration) {
    return comment.fields().getOrDefault("text", "").contains(INHERIT)
        || declaration.modifiers().contains(OVERRIDE);
  }

  private void checkTags(
      final SyntaxNode method, final SyntaxNode comment, final List<RuleMatch> matches) {
    final Span name =
        SyntaxWalk.firstDescendant(method, "identifier", NAME_SKIPPED)
            .map(SyntaxNode::span)
            .orElse(method.span());
    final List<String> parameters = parameterNames(method);
    final List<JavadocTags.Tag> tags = JavadocTags.tags(comment);
    final List<String> documented = new ArrayList<>();
    for (final JavadocTags.Tag tag : tags) {
      if (PARAM.equals(tag.name()) && !tag.description().startsWith(TYPE_PARAMETER)) {
        final String parameter = firstWord(tag.description());
        documented.add(parameter);
        if (!parameters.contains(parameter)) {
          report(tag.span(), "unused @param tag for '" + parameter + "'", matches);
        }
      }
    }
    parameters.stream()
        .filter(parameter -> !documented.contains(parameter))
        .forEach(parameter -> report(name, "missing @param tag for '" + parameter + "'", matches));
    if (returnsValue(method) && tags.stream().noneMatch(tag -> RETURN.equals(tag.name()))) {
      report(name, "missing @return tag", matches);
    }
  }

  private void report(final Span span, final String problem, final List<RuleMatch> matches) {
    matches.add(CheckSupport.at(span, CheckSupport.fill(message, problem, "")));
  }

  private static List<String> parameterNames(final SyntaxNode method) {
    final List<String> names = new ArrayList<>();
    SyntaxWalk.firstDescendant(method, "formalParameters", BODIES)
        .ifPresent(
            formals ->
                SyntaxWalk.descendants(formals).stream()
                    .filter(node -> "variableDeclaratorId".equals(node.kind()))
                    .forEach(node -> names.add(SyntaxWalk.text(node))));
    return names;
  }

  private static boolean returnsValue(final SyntaxNode method) {
    return "methodDeclaration".equals(method.kind())
        && SyntaxWalk.firstDescendant(method, "typeTypeOrVoid", BODIES)
            .map(SyntaxWalk::text)
            .filter(type -> !VOID.equals(type))
            .isPresent();
  }

  private static String firstWord(final String description) {
    final int space = description.indexOf(' ');
    return space < 0 ? description : description.substring(0, space);
  }
}
