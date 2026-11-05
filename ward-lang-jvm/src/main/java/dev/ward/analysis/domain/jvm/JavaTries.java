package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.mayThrow;
import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.parts;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Lowers the {@code try} and {@code synchronized} statements of a Java body. */
final class JavaTries {

  private static final String BLOCK = "block";
  private static final String CATCH_CLAUSE = "catchClause";
  private static final String FINALLY_BLOCK = "finallyBlock";
  private static final String RESOURCE_SPECIFICATION = "resourceSpecification";
  private static final String RESOURCES = "resources";
  private static final String RESOURCE = "resource";
  private static final int LOCK_INDEX = 2;
  private static final int BODY_INDEX = 4;

  private final ControlFlowBuilder flow;
  private final JavaStatements statements;

  JavaTries(final ControlFlowBuilder flow, final JavaStatements statements) {
    this.flow = flow;
    this.statements = statements;
  }

  boolean tryStatement(final List<SyntaxNode> parts) {
    final List<SyntaxNode> catches = JavaFlowSyntax.allOf(parts, CATCH_CLAUSE);
    final SyntaxNode finallyBlock = JavaFlowSyntax.firstOf(parts, FINALLY_BLOCK);
    final SyntaxNode resources = JavaFlowSyntax.firstOf(parts, RESOURCE_SPECIFICATION);
    flow.tries().pushTry(!catches.isEmpty(), finallyBlock != null);
    if (resources != null) {
      addResources(resources);
    }
    statements.blockStatements(requireBlock(JavaFlowSyntax.firstOf(parts, BLOCK)));
    flow.tries().endTryBody();
    for (final SyntaxNode catchClause : catches) {
      flow.tries().beginCatch();
      statements.blockStatements(requireBlock(JavaFlowSyntax.firstOf(parts(catchClause), BLOCK)));
      flow.tries().endCatch();
    }
    flow.tries().beginFinally();
    if (finallyBlock != null) {
      statements.blockStatements(requireBlock(JavaFlowSyntax.firstOf(parts(finallyBlock), BLOCK)));
    }
    flow.tries().popTry();
    return true;
  }

  boolean synchronizedStatement(final List<SyntaxNode> parts) {
    final SyntaxNode lock = parts.get(LOCK_INDEX);
    flow.add(lock, mayThrow(lock));
    statements.blockStatements(requireBlock(parts.get(BODY_INDEX)));
    return true;
  }

  /** Adds each try-with-resources resource as a node inside the handler scope. */
  private void addResources(final SyntaxNode specification) {
    final SyntaxNode resources = JavaFlowSyntax.firstOf(parts(specification), RESOURCES);
    for (final SyntaxNode resource : JavaFlowSyntax.allOf(parts(resources), RESOURCE)) {
      flow.add(resource, true);
    }
  }

  private static SyntaxNode requireBlock(final SyntaxNode node) {
    if (node == null || !BLOCK.equals(node.kind())) {
      throw new IllegalStateException("expected a block");
    }
    return node;
  }
}
