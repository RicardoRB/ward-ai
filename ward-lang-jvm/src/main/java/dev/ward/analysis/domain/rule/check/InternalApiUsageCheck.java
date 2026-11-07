package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reports an import, or a call, that reaches the internal API of the JDK: {@code sun.*}, {@code
 * com.sun.*} and {@code jdk.internal.*}. An import is read from the text of the file; a call is
 * read from the type facts of its target, so a call whose target is unknown reports nothing.
 */
public final class InternalApiUsageCheck implements RuleCheck {

  private static final String IMPORT = "importDeclaration";
  private static final String EXPRESSION = "expression";
  private static final String IMPORT_PREFIX = "import";
  private static final String STATIC_PREFIX = "importstatic";
  private static final List<String> INTERNAL = List.of("sun.", "com.sun.", "jdk.internal.");
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the import or call; {@code {value}} is the name used
   */
  public InternalApiUsageCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return imports(root);
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    final List<RuleMatch> calls =
        SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
            .map(SyntaxWalk.Visit::node)
            .flatMap(node -> callReport(node, types).stream())
            .toList();
    return Stream.concat(imports(root).stream(), calls.stream()).toList();
  }

  private List<RuleMatch> imports(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(IMPORT)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(node -> isInternal(importedName(node)))
        .map(node -> CheckSupport.at(node.span(), fill(importedName(node))))
        .toList();
  }

  private List<RuleMatch> callReport(final SyntaxNode node, final UnitTypeFacts types) {
    return types
        .callTarget(node.span())
        .filter(target -> isInternal(declaringName(target)))
        .map(target -> CheckSupport.at(node.span(), fill(declaringName(target))))
        .stream()
        .toList();
  }

  private static String declaringName(final CallTarget target) {
    return target.declaringType().name();
  }

  private static boolean isInternal(final String name) {
    return INTERNAL.stream().anyMatch(name::startsWith);
  }

  private String fill(final String name) {
    return CheckSupport.fill(message, name, "");
  }

  /** Returns the imported name, without the keyword, the static marker or the semicolon. */
  private static String importedName(final SyntaxNode node) {
    final String text = SyntaxWalk.text(node);
    final String body =
        text.startsWith(STATIC_PREFIX)
            ? text.substring(STATIC_PREFIX.length())
            : text.substring(IMPORT_PREFIX.length());
    return body.endsWith(";") ? body.substring(0, body.length() - 1) : body;
  }
}
