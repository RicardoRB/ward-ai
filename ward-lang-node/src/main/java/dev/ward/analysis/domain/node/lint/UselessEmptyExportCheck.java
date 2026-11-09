package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reports an empty {@code export {}} in a module that has another import or export. */
public final class UselessEmptyExportCheck implements RuleCheck {

  private static final String EXPORT = "exportStatement";
  private static final String IMPORT = "importStatement";
  private static final String DECLARED_EXPORT = "sourceElement";
  private static final String EXPORT_WORD = "export";
  private static final String EMPTY = "export{}";
  private static final Set<String> SCOPES =
      Set.of("block", "functionBody", "classTail", "namespaceDeclaration", "typeBody", "enumBody");

  private final String message;

  /** Creates the check with the message of the rule. */
  public UselessEmptyExportCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<SyntaxNode> modules = new ArrayList<>();
    collectModuleItems(root, modules);
    final List<SyntaxNode> empty =
        modules.stream().filter(UselessEmptyExportCheck::isEmpty).toList();
    if (empty.size() == modules.size()) {
      return List.of();
    }
    return empty.stream().map(node -> new RuleMatch(node.span(), message)).toList();
  }

  private static boolean isEmpty(final SyntaxNode node) {
    return EXPORT.equals(node.kind()) && EMPTY.equals(SyntaxText.of(node).replace(";", ""));
  }

  private static void collectModuleItems(final SyntaxNode node, final List<SyntaxNode> out) {
    if (EXPORT.equals(node.kind()) || IMPORT.equals(node.kind())) {
      out.add(node);
      return;
    }
    if (DECLARED_EXPORT.equals(node.kind()) && SyntaxText.hasToken(node, EXPORT_WORD)) {
      out.add(node);
      return;
    }
    if (!SCOPES.contains(node.kind())) {
      node.children().forEach(child -> collectModuleItems(child, out));
    }
  }
}
