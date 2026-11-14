package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.port.ControlFlowLowering;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lowers every function, secondary constructor and {@code init} block of a Kotlin file into a
 * control-flow graph. The variable accesses come from the file's scope analysis. A lambda is
 * lowered inside the graph of the body that holds it; see {@link KotlinValueLowering}. Property
 * initializers and accessors are not lowered.
 */
public final class KotlinControlFlow implements ControlFlowLowering {

  private static final String FUNCTION = "functionDeclaration";
  private static final String CONSTRUCTOR = "secondaryConstructor";
  private static final String INITIALIZER = "anonymousInitializer";
  private static final String PROPERTY = "propertyDeclaration";
  private static final String BLOCK = "block";
  private static final String BODY = "functionBody";
  private static final String PARAMETERS = "functionValueParameters";
  private static final Set<String> TYPES =
      Set.of("classDeclaration", "objectDeclaration", "companionObject");
  private static final Set<String> BODIES =
      Set.of(
          FUNCTION,
          CONSTRUCTOR,
          INITIALIZER,
          "classDeclaration",
          "objectDeclaration",
          "objectLiteral",
          "companionObject",
          "typeAlias");

  /**
   * Lowers the bodies of the unit.
   *
   * @param unit the source unit the tree was parsed from
   * @param tree the syntax tree of the unit
   * @return the graphs; a Kotlin body is always lowered, so there are no failures
   */
  @Override
  public ControlFlowResult lower(final SourceUnit unit, final SyntaxNode tree) {
    final UnitScopes scopes = new KotlinScopeAnalysis(environments -> Set.of()).analyze(unit, tree);
    final Map<Integer, Integer> writeEnds = new HashMap<>();
    KotlinWriteEnds.collect(tree, writeEnds);
    final Collector collector = new Collector(scopes, KotlinTypeNames.of(tree), writeEnds);
    collector.visit(tree, "", false);
    return new ControlFlowResult(collector.graphs, List.of());
  }

  /** Walks the tree once, lowering each body and keeping the owning type's name in scope. */
  private static final class Collector {

    private final UnitScopes scopes;
    private final JavaTypeNames types;
    private final Map<Integer, Integer> writeEnds;
    private final List<ControlFlowGraph> graphs = new ArrayList<>();

    Collector(
        final UnitScopes scopes, final JavaTypeNames types, final Map<Integer, Integer> writeEnds) {
      this.scopes = scopes;
      this.types = types;
      this.writeEnds = writeEnds;
    }

    /** {@code insideBody} is true for the nodes of a function, whose properties are locals. */
    void visit(final SyntaxNode node, final String owner, final boolean insideBody) {
      final String scope = TYPES.contains(node.kind()) ? typeScope(node, owner) : owner;
      switch (node.kind()) {
        case FUNCTION -> lowerFunction(node, scope);
        case CONSTRUCTOR -> lowerConstructor(node, scope);
        case INITIALIZER -> lowerInitializer(node, scope);
        case PROPERTY -> lowerProperty(node, scope, insideBody);
        default -> {
          // Other nodes hold no body of their own.
        }
      }
      final boolean inside = !TYPES.contains(node.kind()) && (insideBody || isBody(node));
      node.children().forEach(child -> visit(child, scope, inside));
    }

    private static boolean isBody(final SyntaxNode node) {
      return FUNCTION.equals(node.kind())
          || CONSTRUCTOR.equals(node.kind())
          || INITIALIZER.equals(node.kind())
          || "objectLiteral".equals(node.kind());
    }

    /** A property of a type or of the file has its initializer and accessors as bodies. */
    private void lowerProperty(final SyntaxNode node, final String scope, final boolean local) {
      if (!local) {
        for (final KotlinPropertyBodies.Body body : KotlinPropertyBodies.of(node)) {
          lower(scope + "#" + body.name(), body.owner(), null, body.body());
        }
      }
    }

    private void lowerFunction(final SyntaxNode node, final String scope) {
      final SyntaxNode body = KotlinFlowSyntax.child(node, BODY);
      if (body == null) {
        return;
      }
      final SyntaxNode identifier = KotlinFlowSyntax.child(node, "identifier");
      final String name = identifier == null ? "<anonymous>" : KotlinFlowSyntax.text(identifier);
      final SyntaxNode block = KotlinFlowSyntax.child(body, BLOCK);
      lower(
          scope + "#" + name,
          node,
          KotlinFlowSyntax.child(node, PARAMETERS),
          block == null ? body : block);
    }

    private void lowerConstructor(final SyntaxNode node, final String scope) {
      lower(
          scope + "#<init>",
          node,
          KotlinFlowSyntax.child(node, PARAMETERS),
          KotlinFlowSyntax.child(node, BLOCK));
    }

    private void lowerInitializer(final SyntaxNode node, final String scope) {
      lower(scope + "#<init>(0)", node, null, KotlinFlowSyntax.child(node, BLOCK));
    }

    private void lower(
        final String base,
        final SyntaxNode owner,
        final SyntaxNode parameters,
        final SyntaxNode body) {
      final String name = base.endsWith("(0)") ? base : base + "(" + count(parameters) + ")";
      final Span span = owner.span();
      final KotlinView view = new KotlinView();
      final KotlinAccesses accesses =
          new KotlinAccesses(scopes, span, nested(owner), writeEnds, view);
      final List<VariableAccess> entry =
          parameters == null ? List.of() : KotlinAccesses.parameters(scopes, parameters.span());
      graphs.add(
          new KotlinBodyLowering(accesses, new KotlinFlowSites(types, view), view)
              .lower(name, entry, parameters, body, span));
    }

    private static int count(final SyntaxNode parameters) {
      return parameters == null
          ? 0
          : KotlinFlowSyntax.children(parameters, "functionValueParameter").size();
    }

    /** The spans of the functions and classes inside a body, which are bodies of their own. */
    private static List<Span> nested(final SyntaxNode owner) {
      final List<Span> found = new ArrayList<>();
      owner.children().forEach(child -> collectNested(child, found));
      return found;
    }

    private static void collectNested(final SyntaxNode node, final List<Span> found) {
      if (BODIES.contains(node.kind())) {
        found.add(node.span());
      } else {
        node.children().forEach(child -> collectNested(child, found));
      }
    }

    private static String typeScope(final SyntaxNode node, final String owner) {
      final SyntaxNode name = KotlinFlowSyntax.child(node, "simpleIdentifier");
      final String written = name == null ? "Companion" : KotlinFlowSyntax.text(name);
      return owner.isEmpty() ? written : owner + "." + written;
    }
  }
}
