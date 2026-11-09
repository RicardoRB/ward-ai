package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports each javadoc of a function, method or constructor whose tags do not match its signature,
 * like Checkstyle's JavadocMethod: every parameter needs a {@code @param} tag, a non-void function
 * needs {@code @return}, and a tag must name a parameter. The javadoc belongs to the declaration
 * that follows it by position. The findings are placed at the javadoc comment.
 */
public final class NodeJavadocMethodCheck implements RuleCheck {

  private static final String COMMENT = "comment";
  private static final String CALL_SIGNATURE = "callSignature";
  private static final String JAVADOC = "/**";
  private static final Pattern PARAM =
      Pattern.compile("@param\\s+(?:\\{[^}]*\\}\\s*)?([A-Za-z_$][\\w$]*)");
  private static final Pattern RETURN = Pattern.compile("@returns?\\b");
  private static final Pattern VALUE_RETURN = Pattern.compile("return[^;}]");
  private static final Set<String> METHODS =
      Set.of("functionDeclaration", "methodDefinition", "constructorDeclaration");
  private static final Set<String> MODIFIERS =
      Set.of(
          "export",
          "default",
          "declare",
          "abstract",
          "async",
          "static",
          "public",
          "private",
          "protected",
          "readonly");
  private static final Set<String> PARAMETERS =
      Set.of(
          "formalParameterArg",
          "lastFormalParameterArg",
          "requiredParameter",
          "optionalParameter",
          "restParameter");

  private final boolean allowMissingParamTags;
  private final boolean allowMissingReturnTag;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowMissingParamTags whether a parameter without a {@code @param} tag is left alone
   * @param allowMissingReturnTag whether a non-void function without {@code @return} is left alone
   * @param message the message of each finding; {@code {value}} describes the problem
   */
  public NodeJavadocMethodCheck(
      final boolean allowMissingParamTags,
      final boolean allowMissingReturnTag,
      final String message) {
    this.allowMissingParamTags = allowMissingParamTags;
    this.allowMissingReturnTag = allowMissingReturnTag;
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<SyntaxNode> tokens = new ArrayList<>();
    final List<SyntaxNode> javadocs = new ArrayList<>();
    final Map<Integer, SyntaxNode> methods = new HashMap<>();
    collect(root, tokens, javadocs, methods);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxNode javadoc : javadocs) {
      final Optional<SyntaxNode> method = methodAfter(javadoc, tokens, methods);
      if (method.isPresent()) {
        for (final String problem : problems(javadoc, method.get())) {
          found.add(new RuleMatch(javadoc.span(), message.replace("{value}", problem)));
        }
      }
    }
    return List.copyOf(found);
  }

  private void collect(
      final SyntaxNode node,
      final List<SyntaxNode> tokens,
      final List<SyntaxNode> javadocs,
      final Map<Integer, SyntaxNode> methods) {
    if (COMMENT.equals(node.kind())) {
      if (node.fields().getOrDefault("text", "").startsWith(JAVADOC)) {
        javadocs.add(node);
      }
      return;
    }
    if (METHODS.contains(node.kind())) {
      methods.put(NodeSyntax.firstLeaf(node).span().startOffset(), node);
    }
    if (node.children().isEmpty()) {
      if (node.span().startOffset() < node.span().endOffset()) {
        tokens.add(node);
      }
      return;
    }
    node.children().forEach(child -> collect(child, tokens, javadocs, methods));
  }

  /** The method whose first token follows the javadoc, skipping leading modifiers. */
  private static Optional<SyntaxNode> methodAfter(
      final SyntaxNode javadoc,
      final List<SyntaxNode> tokens,
      final Map<Integer, SyntaxNode> methods) {
    for (int index = 0; index < tokens.size(); index++) {
      if (tokens.get(index).span().startOffset() >= javadoc.span().endOffset()) {
        int at = index;
        while (at < tokens.size() && MODIFIERS.contains(text(tokens.get(at)))) {
          at++;
        }
        return at < tokens.size()
            ? Optional.ofNullable(methods.get(tokens.get(at).span().startOffset()))
            : Optional.empty();
      }
    }
    return Optional.empty();
  }

  /** The tag problems of one javadoc against its method, as descriptions for the message. */
  private List<String> problems(final SyntaxNode javadoc, final SyntaxNode method) {
    final String comment = javadoc.fields().getOrDefault("text", "");
    final Set<String> documented = new HashSet<>();
    final Matcher param = PARAM.matcher(comment);
    while (param.find()) {
      documented.add(param.group(1));
    }
    final List<String> names = new ArrayList<>();
    parameterNames(method, names);
    final List<String> problems = new ArrayList<>();
    if (!allowMissingParamTags) {
      names.stream()
          .filter(name -> !documented.contains(name))
          .forEach(name -> problems.add("expected @param tag for '" + name + "'"));
    }
    documented.stream()
        .filter(name -> !names.contains(name))
        .forEach(name -> problems.add("unused @param tag for '" + name + "'"));
    if (!allowMissingReturnTag && returnsValue(method) && !RETURN.matcher(comment).find()) {
      problems.add("expected @return tag");
    }
    return problems;
  }

  private static void parameterNames(final SyntaxNode node, final List<String> names) {
    if (PARAMETERS.contains(node.kind())) {
      NodeSyntax.firstIdentifier(node).ifPresent(name -> names.add(NodeSyntax.text(name)));
      return;
    }
    node.children().forEach(child -> parameterNames(child, names));
  }

  /**
   * Whether the function returns a value: a TypeScript return type other than void, or a return in
   * its body.
   */
  private static boolean returnsValue(final SyntaxNode method) {
    final Optional<SyntaxNode> annotation = returnAnnotation(method);
    if (annotation.isPresent()) {
      return !"void".equals(NodeSyntax.text(annotation.get()).replace(":", ""));
    }
    return VALUE_RETURN.matcher(NodeSyntax.text(method)).find();
  }

  /** The return type annotation of a TypeScript signature, which sits under its call signature. */
  private static Optional<SyntaxNode> returnAnnotation(final SyntaxNode method) {
    for (final SyntaxNode child : method.children()) {
      if (CALL_SIGNATURE.equals(child.kind())) {
        return child.children().stream()
            .filter(part -> "typeAnnotation".equals(part.kind()))
            .findFirst();
      }
    }
    return Optional.empty();
  }

  private static String text(final SyntaxNode token) {
    return token.fields().getOrDefault("text", "");
  }
}
