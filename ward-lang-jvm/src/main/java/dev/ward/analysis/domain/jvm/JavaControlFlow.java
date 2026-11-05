package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ControlFlowLowering;
import dev.ward.analysis.domain.port.ControlFlowResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lowers every method, constructor, initializer block and lambda of a Java file into a control-flow
 * graph. A body that uses a construct the lowering does not handle is reported as a failure, and
 * the other bodies are still lowered.
 */
public final class JavaControlFlow implements ControlFlowLowering {

  private static final String RULE_ID = "control-flow";
  private static final String BLOCK = "block";
  private static final String IDENTIFIER = "identifier";
  private static final String FORMAL_PARAMETER = "formalParameter";
  private static final String FORMAL_PARAMETER_LIST = "formalParameterList";
  private static final Set<String> TYPES =
      Set.of("classDeclaration", "interfaceDeclaration", "enumDeclaration", "recordDeclaration");

  /**
   * Lowers the bodies of the unit.
   *
   * @param unit the source unit the tree was parsed from
   * @param tree the syntax tree of the unit
   * @return the graphs and the failures
   */
  @Override
  public ControlFlowResult lower(final SourceUnit unit, final SyntaxNode tree) {
    final Collector collector = new Collector(unit);
    collector.visit(tree, "");
    return new ControlFlowResult(collector.graphs, collector.failures);
  }

  /** Walks the tree once, lowering each body and keeping the owning type's name in scope. */
  private static final class Collector {

    private final SourceUnit unit;
    private final List<ControlFlowGraph> graphs = new ArrayList<>();
    private final List<AnalysisFinding> failures = new ArrayList<>();

    Collector(final SourceUnit unit) {
      this.unit = unit;
    }

    void visit(final SyntaxNode node, final String owner) {
      final String scope = TYPES.contains(node.kind()) ? typeScope(node, owner) : owner;
      lowerBody(node, scope);
      for (final SyntaxNode child : node.children()) {
        visit(child, scope);
      }
    }

    private void lowerBody(final SyntaxNode node, final String scope) {
      switch (node.kind()) {
        case "methodDeclaration" -> lowerMethod(node, scope);
        case "constructorDeclaration" -> lowerConstructor(node, scope);
        case "classBodyDeclaration" -> lowerInitializer(node, scope);
        case "lambdaExpression" -> lowerLambda(node);
        default -> {
          // Other nodes hold no body of their own.
        }
      }
    }

    private void lowerMethod(final SyntaxNode node, final String scope) {
      final SyntaxNode methodBody =
          JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), "methodBody");
      final SyntaxNode block =
          methodBody == null
              ? null
              : JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(methodBody), BLOCK);
      if (block != null) {
        final String name =
            JavaFlowSyntax.text(JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), IDENTIFIER));
        final String params = parameters(node);
        lower(scope + "#" + name + "(" + params + ")", block);
      }
    }

    private void lowerConstructor(final SyntaxNode node, final String scope) {
      final SyntaxNode block = JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), BLOCK);
      if (block != null) {
        lower(scope + "#<init>(" + parameters(node) + ")", block);
      }
    }

    private void lowerInitializer(final SyntaxNode node, final String scope) {
      final SyntaxNode block = JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), BLOCK);
      if (block != null) {
        final boolean isStatic =
            JavaFlowSyntax.parts(node).stream()
                .anyMatch(part -> "static".equals(JavaFlowSyntax.text(part)));
        lower(scope + (isStatic ? "#<clinit>(0)" : "#<init>(0)"), block);
      }
    }

    private void lowerLambda(final SyntaxNode node) {
      final SyntaxNode lambdaBody =
          JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), "lambdaBody");
      if (lambdaBody != null) {
        final SyntaxNode body = JavaFlowSyntax.parts(lambdaBody).get(0);
        final Span span = body.span();
        lower("<lambda>@" + span.startLine() + ":" + span.startColumn(), body);
      }
    }

    private void lower(final String name, final SyntaxNode node) {
      try {
        graphs.add(new JavaBodyLowering().lower(name, node, node.span()));
      } catch (IllegalStateException failure) {
        failures.add(
            new AnalysisFinding(
                RULE_ID,
                AnalysisSeverity.WARNING,
                "control flow not lowered for " + name + ": " + failure.getMessage(),
                unit.path(),
                node.span().startLine(),
                node.span().startColumn(),
                null));
      }
    }

    private static String typeScope(final SyntaxNode node, final String owner) {
      final String name =
          JavaFlowSyntax.text(JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), IDENTIFIER));
      return owner.isEmpty() ? name : owner + "." + name;
    }

    private static String parameters(final SyntaxNode node) {
      final SyntaxNode formals =
          JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), "formalParameters");
      return formals == null ? "0" : String.valueOf(countParameters(formals));
    }

    private static int countParameters(final SyntaxNode formals) {
      int count = 0;
      for (final SyntaxNode part : JavaFlowSyntax.parts(formals)) {
        if (FORMAL_PARAMETER.equals(part.kind())) {
          count++;
        } else if (FORMAL_PARAMETER_LIST.equals(part.kind())) {
          count += countParameters(part);
        }
      }
      return count;
    }
  }
}
