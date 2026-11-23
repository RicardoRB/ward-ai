package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Walks a Rust syntax tree and records its scopes. The walk dispatches on the kind of a node; the
 * helpers it owns each handle one family of constructs: items, patterns, paths and macros, and
 * statements and expressions.
 */
final class RustScopeWalk {
  /** Nodes that hold no name the scopes need: attributes, visibility, labels and literals. */
  private static final Set<String> SKIPPED =
      Set.of(
          "outerAttribute",
          "innerAttribute",
          "visibility",
          "abi",
          "lifetime",
          "useDeclaration",
          "externCrate",
          "loopLabel",
          "literalExpression",
          "comment"
      );

  /** Items that are a scope of their own, which holds their generic parameters. */
  private static final Set<String> SCOPED_ITEMS =
      Set.of(
          "function_",
          "structStruct",
          "tupleStruct",
          "enumeration",
          "union_",
          "trait_",
          "inherentImpl",
          "traitImpl",
          "typeAlias",
          "constantItem",
          "staticItem"
      );

  final ScopeBuilder builder;
  final RustScopeStack scopes;
  final RustScopeItems items;
  final RustScopeDeclarations declarations;
  final RustScopePatterns patterns;
  final RustScopePaths paths;
  final RustScopeFlow flow;
  final RustScopeBranches branches;
  final RustScopeExpressions expressions;
  final RustScopeMacros macros;
  private final Map<String, Consumer<SyntaxNode>> handlers = new HashMap<>();

  RustScopeWalk(final ScopeBuilder builder) {
    this.builder = builder;
    this.scopes = new RustScopeStack(builder);
    this.items = new RustScopeItems(this);
    this.declarations = new RustScopeDeclarations(this);
    this.patterns = new RustScopePatterns(this);
    this.paths = new RustScopePaths(this);
    this.flow = new RustScopeFlow(this);
    this.branches = new RustScopeBranches(this);
    this.expressions = new RustScopeExpressions(this);
    this.macros = new RustScopeMacros(this);
    handlers.put("crate", items::crate);
    handlers.put("module", items::inlineModule);
    handlers.put("genericParams", items::genericParams);
    handlers.put("selfParam", items::selfParam);
    handlers.put("functionParamPattern", patterns::parameter);
    handlers.put("pathInExpression", paths::expressionPath);
    handlers.put("typePath", paths::typePath);
    handlers.put("genericArgsBinding", paths::binding);
    handlers.put("macroInvocation", macros::invocation);
    handlers.put("macroInvocationSemi", macros::invocation);
    handlers.put("macroRulesDefinition", macros::invocation);
    handlers.put("blockExpression", flow::block);
    handlers.put("letStatement", flow::letStatement);
    handlers.put("ifExpression", branches::conditional);
    handlers.put("predicateLoopExpression", branches::conditional);
    handlers.put("iteratorLoopExpression", branches::forLoop);
    handlers.put("matchArms", branches::matchArms);
    handlers.put("closureExpression", branches::closure);
    handlers.put("closureParam", patterns::closureParameter);
    handlers.put("expression", expressions::expression);
    handlers.put("structExprField", expressions::fieldInit);
    handlers.put("enumExprField", expressions::fieldInit);
    SCOPED_ITEMS.forEach(kind -> handlers.put(kind, items::scoped));
    SKIPPED.forEach(kind -> handlers.put(kind, node -> {}));
  }

  /** Visits {@code node} and everything under it. */
  void visit(final SyntaxNode node) {
    handlers.getOrDefault(node.kind(), this::generic).accept(node);
  }

  /** Visits the children of {@code node} in order. */
  void generic(final SyntaxNode node) {
    node.children().forEach(this::visit);
  }
}
