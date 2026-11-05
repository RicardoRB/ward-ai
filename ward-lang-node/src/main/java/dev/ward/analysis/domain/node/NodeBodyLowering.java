package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/**
 * Lowers the statements of one TypeScript or JavaScript body into a control-flow graph. Each kind
 * of construct is lowered by a helper that shares this body's builder. A nested function is added
 * as one opaque node; its own body is lowered separately.
 */
final class NodeBodyLowering implements NodeStatements {

  private static final String BLOCK = "block";
  private static final String EXPRESSION_STATEMENT = "expressionStatement";
  private static final String ITERATION = "iterationStatement";
  private static final String IF_STATEMENT = "ifStatement";
  private static final String LABELLED = "labelledStatement";
  private static final String VARIABLE = "variableStatement";
  private static final String EMPTY = "emptyStatement_";
  private static final int CONDITION_INDEX = 2;
  private static final int BODY_INDEX = 4;
  private static final int ELSE_BODY_INDEX = 6;
  private static final int PARTS_WITHOUT_ELSE = 5;

  /** Declarations whose bodies are lowered as graphs of their own; the node is one plain step. */
  private static final Set<String> DECLARATIONS =
      Set.of(
          "functionDeclaration",
          "generatorFunctionDeclaration",
          "arrowFunctionDeclaration",
          "classDeclaration",
          "interfaceDeclaration",
          "typeAliasDeclaration",
          "enumDeclaration",
          "abstractDeclaration",
          "namespaceDeclaration",
          "importStatement",
          "exportStatement");

  /** Plain statements, which may call or await and so may throw. */
  private static final Set<String> PLAIN =
      Set.of("yieldStatement", "debuggerStatement", "withStatement");

  private final NodeAccesses accesses = new NodeAccesses();
  private final NodeFlowSites sites;
  private final NodeParameterSites parameterSites;
  private final ControlFlowBuilder flow;
  private final NodeBranches branches;
  private final NodeLoops loops;
  private final NodeSwitches switches;
  private final NodeTries tries;
  private final NodeJumps jumps;

  /**
   * Starts the lowering of one body.
   *
   * @param imports the named imports of the file the body is in
   */
  NodeBodyLowering(final NodeImports imports) {
    final NodeTypes types = new NodeTypes(imports);
    this.sites = new NodeFlowSites(types, accesses::isSkipped);
    this.parameterSites = new NodeParameterSites(types);
    this.flow = new ControlFlowBuilder(accesses, sites);
    this.branches = new NodeBranches(flow, accesses);
    this.loops = new NodeLoops(flow, branches, this, accesses);
    this.switches = new NodeSwitches(flow, this);
    this.tries = new NodeTries(flow, this, accesses);
    this.jumps = new NodeJumps(flow, branches);
  }

  /**
   * Lowers a body made of statements.
   *
   * @param body the name of the body
   * @param statements the statement nodes of the body, in source order
   * @param span where the body is in the source
   * @return the graph of the body
   * @throws IllegalStateException if the body holds a construct this lowering does not handle
   */
  ControlFlowGraph lowerStatements(
      final String body,
      final SyntaxNode function,
      final List<SyntaxNode> statements,
      final Span span) {
    flow.record(accesses.parameters(function), parameterSites.of(function));
    for (final SyntaxNode statement : statements) {
      statement(statement, null);
    }
    return flow.build(body, span);
  }

  /**
   * Lowers an arrow function whose body is one expression.
   *
   * @param body the name of the body
   * @param function the arrow function, whose parameters the body declares
   * @param expression the expression that is the body
   * @param span where the body is in the source
   * @return the graph of the body
   */
  ControlFlowGraph lowerExpression(
      final String body, final SyntaxNode function, final SyntaxNode expression, final Span span) {
    flow.record(accesses.parameters(function), parameterSites.of(function));
    branches.emitValue(expression);
    return flow.build(body, span);
  }

  @Override
  public void statement(final SyntaxNode node, final String label) {
    final SyntaxNode part = NodeFlowSyntax.parts(node).get(0);
    if (!compound(part, label) && !jump(part) && !simple(part)) {
      throw NodeSteps.unsupported(part);
    }
  }

  @Override
  public void blockStatements(final SyntaxNode block) {
    accesses.enterScope();
    for (final SyntaxNode statement : NodeFlowSyntax.statements(block)) {
      statement(statement, null);
    }
    accesses.exitScope();
  }

  /** Lowers the statements that split the graph into several blocks. */
  private boolean compound(final SyntaxNode part, final String label) {
    return switch (part.kind()) {
      case BLOCK -> {
        blockStatements(part);
        yield true;
      }
      case IF_STATEMENT -> ifStatement(NodeFlowSyntax.parts(part));
      case ITERATION -> {
        accesses.enterScope();
        final boolean lowered = loops.iteration(NodeFlowSyntax.parts(part), label);
        accesses.exitScope();
        yield lowered;
      }
      case "tryStatement" -> tries.tryStatement(NodeFlowSyntax.parts(part));
      case "switchStatement" -> switches.switchOn(NodeFlowSyntax.parts(part));
      case LABELLED -> labelled(part);
      case EXPRESSION_STATEMENT -> expressionStatement(part);
      case VARIABLE -> branches.variableStatement(part);
      default -> false;
    };
  }

  /** Lowers the statements that leave a block. */
  private boolean jump(final SyntaxNode part) {
    return switch (part.kind()) {
      case "returnStatement" -> jumps.returnStatement(part);
      case "throwStatement" -> jumps.throwStatement(part);
      case "breakStatement" -> jumps.breakStatement(part);
      case "continueStatement" -> jumps.continueStatement(part);
      default -> false;
    };
  }

  /** Adds the statements that are one plain step, which the graph does not split for. */
  private boolean simple(final SyntaxNode part) {
    if (DECLARATIONS.contains(part.kind())) {
      NodeSteps.add(flow, part, false);
      return true;
    }
    if (PLAIN.contains(part.kind())) {
      NodeSteps.add(flow, part, NodeExpressions.mayThrow(part));
      return true;
    }
    return EMPTY.equals(part.kind());
  }

  private boolean ifStatement(final List<SyntaxNode> parts) {
    final int thenBlock = flow.newBlock();
    final int after = flow.newBlock();
    final boolean hasElse = parts.size() > PARTS_WITHOUT_ELSE;
    final int elseBlock = hasElse ? flow.newBlock() : after;
    branches.condition(parts.get(CONDITION_INDEX), thenBlock, elseBlock);
    flow.moveTo(thenBlock);
    statement(parts.get(BODY_INDEX), null);
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    if (hasElse) {
      flow.moveTo(elseBlock);
      statement(parts.get(ELSE_BODY_INDEX), null);
      flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    }
    flow.moveTo(after);
    return true;
  }

  private boolean labelled(final SyntaxNode part) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(part);
    final String name = NodeFlowSyntax.text(parts.get(0));
    final SyntaxNode inner = parts.get(CONDITION_INDEX);
    if (ITERATION.equals(NodeFlowSyntax.parts(inner).get(0).kind())) {
      statement(inner, name);
      return true;
    }
    final int after = flow.newBlock();
    flow.jumps().pushLabel(name, after);
    statement(inner, null);
    flow.jumps().popFrame();
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(after);
    return true;
  }

  private boolean expressionStatement(final SyntaxNode part) {
    branches.valueStatement(part, NodeFlowSyntax.parts(part).get(0), false);
    return true;
  }
}
