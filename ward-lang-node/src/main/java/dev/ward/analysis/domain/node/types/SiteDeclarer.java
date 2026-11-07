package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.Map;
import java.util.Set;

/** Finds the declarations of names in a parse tree. */
final class SiteDeclarer {
  private static final Set<String> PARAMETER_KINDS =
      Set.of(
          "requiredParameter",
          "optionalParameter",
          "restParameter",
          "formalParameterArg",
          "lastFormalParameterArg");

  private SiteDeclarer() {}

  static void declare(
      final SyntaxNode node,
      final Map<SyntaxNode, SyntaxNode> parents,
      final Map<String, UnitModel.Site> sites) {
    switch (node.kind()) {
      case "variableDeclaration" -> {
        final SyntaxNode list = parents.get(node);
        final boolean isConst =
            declaresConst(list) || list != null && declaresConst(parents.get(list));
        record(sites, TreeText.variableName(node), UnitModel.SiteKind.VARIABLE, node, isConst);
      }
      case "functionDeclaration" ->
          record(
              sites, TreeKit.child(node, "identifier"), UnitModel.SiteKind.FUNCTION, node, false);
      case "classDeclaration" ->
          record(sites, TreeKit.child(node, "identifier"), UnitModel.SiteKind.CLASS, node, false);
      case "arrowFunctionParameters" -> {
        final SyntaxNode single = TreeKit.child(node, "propertyName");
        if (single != null) {
          record(sites, single, UnitModel.SiteKind.PARAMETER, node, false);
        }
      }
      default -> {
        if (PARAMETER_KINDS.contains(node.kind())) {
          record(
              sites, TreeKit.find(node, "identifier"), UnitModel.SiteKind.PARAMETER, node, false);
        }
      }
    }
  }

  static boolean declaresConst(final SyntaxNode node) {
    final SyntaxNode modifier = node == null ? null : TreeKit.child(node, "varModifier");
    return modifier != null && "const".equals(TreeText.text(modifier));
  }

  private static void record(
      final Map<String, UnitModel.Site> sites,
      final SyntaxNode name,
      final UnitModel.SiteKind kind,
      final SyntaxNode node,
      final boolean isConst) {
    final SyntaxNode identifier =
        name == null
            ? null
            : "identifier".equals(name.kind()) ? name : TreeKit.find(name, "identifier");
    if (identifier != null) {
      sites.put(UnitTypeFacts.key(identifier.span()), new UnitModel.Site(kind, node, isConst));
    }
  }
}
