package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.mayThrow;
import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.parts;
import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.text;
import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.unwrap;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/**
 * Lowers the statements of one Java body into a control-flow graph. Each kind of construct is
 * lowered by a helper that shares this body's builder. A nested lambda or class is added as one
 * opaque node; its own body is lowered separately.
 */
final class JavaBodyLowering implements JavaStatements {

  private static final String BLOCK = "block";
  private static final String STATEMENT = "statement";
  private static final String EXPRESSION = "expression";
  private static final String SWITCH_EXPRESSION = "switchExpression";
  private static final String IDENTIFIER = "identifier";
  private static final String LOCAL_VARIABLE = "localVariableDeclaration";
  private static final String LOCAL_TYPE = "localTypeDeclaration";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String SEMICOLON = ";";
  private static final String KEYWORD_IF = "if";
  private static final int CONDITION_INDEX = 2;
  private static final int BODY_INDEX = 4;
  private static final int ELSE_BODY_INDEX = 6;
  private static final int PARTS_WITHOUT_ELSE = 5;
  private static final Set<String> LOOP_KEYWORDS = Set.of("for", "while", "do");

  private final JavaAccesses accesses = new JavaAccesses();
  private final ControlFlowBuilder flow = new ControlFlowBuilder(accesses);
  private final JavaBranches branches = new JavaBranches(flow, accesses);
  private final JavaLoops loops = new JavaLoops(flow, branches, this);
  private final JavaSwitches switches = new JavaSwitches(flow, this);
  private final JavaTries tries = new JavaTries(flow, this, accesses);
  private final JavaJumps jumps = new JavaJumps(flow, branches);

  /**
   * Lowers one body.
   *
   * @param body the name of the body
   * @param parameters the formal or lambda parameters of the body, or null when it has none
   * @param node the block of the body, or the expression of a lambda with an expression body
   * @param span where the body is in the source
   * @return the graph of the body
   * @throws IllegalStateException if the body holds a construct this lowering does not handle
   */
  ControlFlowGraph lower(
      final String body, final SyntaxNode parameters, final SyntaxNode node, final Span span) {
    flow.record(accesses.parameters(parameters));
    if (BLOCK.equals(node.kind())) {
      blockStatements(node);
    } else {
      branches.emitValue(node);
    }
    return flow.build(body, span);
  }

  @Override
  public void blockStatements(final SyntaxNode block) {
    accesses.enterScope();
    for (final SyntaxNode statement : JavaFlowSyntax.allOf(parts(block), BLOCK_STATEMENT)) {
      blockStatement(statement);
    }
    accesses.exitScope();
  }

  @Override
  public void blockStatement(final SyntaxNode node) {
    final SyntaxNode first = parts(node).get(0);
    if (LOCAL_VARIABLE.equals(first.kind())) {
      flow.add(first, mayThrow(first));
    } else if (LOCAL_TYPE.equals(first.kind())) {
      flow.add(first, false);
    } else if (STATEMENT.equals(first.kind())) {
      statement(first, null);
    } else {
      throw unsupported(first);
    }
  }

  @Override
  public void statement(final SyntaxNode node, final String label) {
    final List<SyntaxNode> parts = parts(node);
    final SyntaxNode first = parts.get(0);
    if (BLOCK.equals(first.kind())) {
      blockStatements(first);
    } else if (IDENTIFIER.equals(first.kind())) {
      labelled(text(first), parts.get(CONDITION_INDEX));
    } else if (EXPRESSION.equals(first.kind()) || SWITCH_EXPRESSION.equals(first.kind())) {
      expressionStatement(node, first);
    } else if (!compound(text(first), parts, label) && !jump(node, text(first), parts)) {
      throw unsupported(node);
    }
  }

  /** Lowers the statements that split the graph into several blocks. */
  private boolean compound(final String keyword, final List<SyntaxNode> parts, final String label) {
    return switch (keyword) {
      case KEYWORD_IF -> ifStatement(parts);
      case "for" -> {
        accesses.enterScope();
        final boolean lowered = loops.forStatement(parts, label);
        accesses.exitScope();
        yield lowered;
      }
      case "while" -> loops.whileStatement(parts, label);
      case "do" -> loops.doStatement(parts, label);
      case "try" -> {
        accesses.enterScope();
        final boolean lowered = tries.tryStatement(parts);
        accesses.exitScope();
        yield lowered;
      }
      case "switch" -> switches.switchOn(parts);
      case "synchronized" -> tries.synchronizedStatement(parts);
      default -> false;
    };
  }

  /** Lowers the statements that leave a block, or that are one plain node. */
  private boolean jump(final SyntaxNode node, final String keyword, final List<SyntaxNode> parts) {
    return switch (keyword) {
      case "return" -> jumps.returnStatement(node, parts);
      case "throw" -> jumps.throwStatement(node);
      case "break" -> jumps.breakStatement(node, parts);
      case "continue" -> jumps.continueStatement(node, parts);
      case "yield" -> jumps.yieldStatement(node);
      case "assert" -> jumps.plainStatement(node);
      case SEMICOLON -> true;
      default -> false;
    };
  }

  private void labelled(final String name, final SyntaxNode inner) {
    final SyntaxNode first = parts(inner).get(0);
    if (LOOP_KEYWORDS.contains(text(first))) {
      statement(inner, name);
      return;
    }
    final int after = flow.newBlock();
    flow.jumps().pushLabel(name, after);
    statement(inner, null);
    flow.jumps().popFrame();
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(after);
  }

  private void expressionStatement(final SyntaxNode statement, final SyntaxNode expression) {
    final SyntaxNode value = unwrap(expression);
    if (SWITCH_EXPRESSION.equals(value.kind())) {
      switches.switchOn(parts(value));
    } else {
      branches.valueStatement(statement, expression, false);
    }
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

  private static IllegalStateException unsupported(final SyntaxNode node) {
    return new IllegalStateException("unsupported statement " + node.kind());
  }
}
