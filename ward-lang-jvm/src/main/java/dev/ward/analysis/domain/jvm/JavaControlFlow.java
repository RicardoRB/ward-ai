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
    final Collector collector = new Collector(unit, JavaTypeNames.of(tree));
    collector.visit(tree, "", List.of());
    return new ControlFlowResult(collector.graphs, collector.failures);
  }

  /** Walks the tree once, lowering each body and keeping the owning type's name in scope. */
  private static final class Collector {

    private final SourceUnit unit;
    private final JavaTypeNames types;
    private final List<ControlFlowGraph> graphs = new ArrayList<>();
    private final List<AnalysisFinding> failures = new ArrayList<>();

    Collector(final SourceUnit unit, final JavaTypeNames types) {
      this.unit = unit;
      this.types = types;
    }

    void visit(final SyntaxNode node, final String owner, final List<JavaClassFields.Field> outer) {
      final boolean isType = TYPES.contains(node.kind());
      final String scope = isType ? typeScope(node, owner) : owner;
      final List<JavaClassFields.Field> fields =
          isType ? JavaClassFields.merged(outer, node) : outer;
      lowerBody(node, scope, fields);
      for (final SyntaxNode child : node.children()) {
        visit(child, scope, fields);
      }
    }

    private void lowerBody(
        final SyntaxNode node, final String scope, final List<JavaClassFields.Field> fields) {
      switch (node.kind()) {
        case "methodDeclaration" -> lowerMethod(node, scope, fields);
        case "constructorDeclaration" -> lowerConstructor(node, scope, fields);
        case "classBodyDeclaration" -> lowerInitializer(node, scope, fields);
        case "lambdaExpression" -> lowerLambda(node, fields);
        default -> {
          // Other nodes hold no body of their own.
        }
      }
    }

    private void lowerMethod(
        final SyntaxNode node, final String scope, final List<JavaClassFields.Field> fields) {
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
        lower(
            scope + "#" + name + "(" + params + ")",
            JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), "formalParameters"),
            block,
            fields);
      }
    }

    private void lowerConstructor(
        final SyntaxNode node, final String scope, final List<JavaClassFields.Field> fields) {
      final SyntaxNode block = JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), BLOCK);
      if (block != null) {
        lower(
            scope + "#<init>(" + parameters(node) + ")",
            JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), "formalParameters"),
            block,
            fields);
      }
    }

    private void lowerInitializer(
        final SyntaxNode node, final String scope, final List<JavaClassFields.Field> fields) {
      final SyntaxNode block = JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), BLOCK);
      if (block != null) {
        final boolean isStatic =
            JavaFlowSyntax.parts(node).stream()
                .anyMatch(part -> "static".equals(JavaFlowSyntax.text(part)));
        lower(scope + (isStatic ? "#<clinit>(0)" : "#<init>(0)"), null, block, fields);
      }
    }

    private void lowerLambda(final SyntaxNode node, final List<JavaClassFields.Field> fields) {
      final SyntaxNode lambdaBody =
          JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), "lambdaBody");
      if (lambdaBody != null) {
        final SyntaxNode body = JavaFlowSyntax.parts(lambdaBody).get(0);
        final Span span = body.span();
        final SyntaxNode parameters =
            JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), "lambdaParameters");
        lower("<lambda>@" + span.startLine() + ":" + span.startColumn(), parameters, body, fields);
      }
    }

    private void lower(
        final String name,
        final SyntaxNode parameters,
        final SyntaxNode node,
        final List<JavaClassFields.Field> fields) {
      try {
        graphs.add(new JavaBodyLowering(types, fields).lower(name, parameters, node, node.span()));
      } catch (IllegalStateException failure) {
        failures.add(
            new AnalysisFinding(
                RULE_ID,
                AnalysisSeverity.WARNING,
                "control flow not lowered for " + name + ": " + failure.getMessage(),
                unit.path(),
                node.span().startLine(),
                node.span().startColumn(),
                null,
                List.of()));
      }
    }

    /** A top-level type is named {@code pkg:Type}, so the owner of a body carries its package. */
    private String typeScope(final SyntaxNode node, final String owner) {
      final String name =
          JavaFlowSyntax.text(JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), IDENTIFIER));
      if (!owner.isEmpty()) {
        return owner + "." + name;
      }
      return types.packageName().isEmpty() ? name : types.packageName() + ":" + name;
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
