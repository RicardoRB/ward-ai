package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reports the TypeScript namespaces of typescript-eslint's {@code no-namespace}. A global
 * augmentation ({@code declare global}) and an ambient module ({@code declare module "x"}) are
 * never reported. Definition files are allowed unless {@code allowDefinitionFiles} is false, and a
 * {@code declare} namespace (with its contents) is allowed when {@code allowDeclarations} is set.
 */
public final class NoNamespaceCheck implements RuleCheck {

  private static final String NAMESPACE = "namespaceDeclaration";
  private static final String DECLARE = "declare";
  private static final Pattern GLOBAL = Pattern.compile("^(declare)?global\\b");
  private static final Pattern AMBIENT_MODULE = Pattern.compile("^(declare)?module[\"']");
  private static final Pattern DEFINITION_FILE = Pattern.compile("\\.d\\.([cm]?ts|.*\\.ts)$");

  private final boolean allowDeclarations;
  private final boolean allowDefinitionFiles;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowDeclarations whether a namespace inside a {@code declare} declaration is allowed
   * @param allowDefinitionFiles whether every namespace of a definition file is allowed
   * @param message the message of each finding
   */
  public NoNamespaceCheck(
      final boolean allowDeclarations, final boolean allowDefinitionFiles, final String message) {
    this.allowDeclarations = allowDeclarations;
    this.allowDefinitionFiles = allowDefinitionFiles;
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the definition-file option is read from the unit's path. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the no-namespace check needs the source unit");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    if (allowDefinitionFiles && isDefinitionFile(unit.path())) {
      return List.of();
    }
    final List<Span> declared = allowDeclarations ? declaredSpans(root) : List.of();
    return SyntaxText.nodesOf(root, NAMESPACE).stream()
        .filter(node -> reportable(SyntaxText.of(node)))
        .filter(node -> !isWithin(node.span(), declared))
        .map(node -> new RuleMatch(node.span(), message))
        .toList();
  }

  /** Reports whether a declaration is a namespace, not a global augmentation or ambient module. */
  private static boolean reportable(final String text) {
    final String head = withoutSpaces(text);
    return !GLOBAL.matcher(head).find() && !AMBIENT_MODULE.matcher(head).find();
  }

  /** The text of a node without its spaces, so that the token joins read the same either way. */
  private static String withoutSpaces(final String text) {
    return text.replaceAll("\\s+", "");
  }

  /** Returns the spans of the namespace declarations that start with {@code declare}. */
  private static List<Span> declaredSpans(final SyntaxNode root) {
    return SyntaxText.nodesOf(root, NAMESPACE).stream()
        .filter(node -> withoutSpaces(SyntaxText.of(node)).startsWith(DECLARE))
        .map(SyntaxNode::span)
        .toList();
  }

  private static boolean isWithin(final Span inner, final List<Span> outers) {
    return outers.stream()
        .anyMatch(
            outer ->
                outer.startOffset() <= inner.startOffset()
                    && inner.endOffset() <= outer.endOffset());
  }

  /** Reports whether the file is a TypeScript definition file, as typescript-eslint reads it. */
  private static boolean isDefinitionFile(final String path) {
    return DEFINITION_FILE.matcher(path.toLowerCase(Locale.ROOT)).find();
  }
}
