package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Records the supertypes that a class or interface names, so the taint analysis can tell which
 * classes of the project implement a type, as {@code Renderer r; r.render(x)} needs. Each
 * supertype becomes a graph with no code whose body name is {@code scope#ward:super:<type>}; no
 * call can name such a body, so it only carries the fact across files.
 */
final class JavaSupertypes {
  /** The prefix of the body name that carries a supertype. */
  static final String MARK = "ward:super:";

  private static final String TYPE_LIST = "typeList";
  private static final String TYPE_TYPE = "typeType";
  private static final String EXTENDS = "extends";
  private static final String IMPLEMENTS = "implements";

  private JavaSupertypes() {}

  /** Returns one graph per extended or implemented type of the declaration. */
  static List<ControlFlowGraph> of(
      final SyntaxNode declaration,
      final String scope,
      final JavaTypeNames types
  ) {
    final List<ControlFlowGraph> graphs = new ArrayList<>();
    boolean listed = false;
    for (final SyntaxNode part : JavaFlowSyntax.parts(declaration)) {
      final String text = JavaFlowSyntax.text(part);
      if (EXTENDS.equals(text) || IMPLEMENTS.equals(text)) {
        listed = true;
      } else if (listed) {
        listed = false;
        names(part).stream()
            .flatMap(name -> types.resolve(name).stream())
            .map(name -> marker(scope, name, declaration))
            .forEach(graphs::add);
      }
    }
    return graphs;
  }

  private static List<String> names(final SyntaxNode part) {
    if (TYPE_TYPE.equals(part.kind())) {
      return List.of(JavaFlowSyntax.text(part));
    }
    return TYPE_LIST.equals(part.kind())
        ? JavaFlowSyntax.allOf(JavaFlowSyntax.parts(part), TYPE_TYPE).stream()
            .map(JavaFlowSyntax::text)
            .toList()
        : List.of();
  }

  private static ControlFlowGraph marker(
      final String scope,
      final String type,
      final SyntaxNode declaration
  ) {
    return new ControlFlowGraph(
        scope + "#" + MARK + type,
        declaration.span(),
        List.of(
            new BasicBlock(0, List.of(), List.of(), List.of(), List.of()),
            new BasicBlock(1, List.of(), List.of(), List.of(), List.of())
        ),
        0,
        1
    );
  }
}
