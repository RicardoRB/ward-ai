package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.ir.VariableAccesses;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the reads, writes and declarations of the names one Python body binds. A name the body
 * declares {@code global} or {@code nonlocal} is not recorded, and neither are the names of nested
 * functions, classes, lambdas and comprehensions. A {@code del} or an annotation without a value is
 * a {@link dev.ward.analysis.domain.ir.AccessKind#DECLARE}, which unbinds the name.
 */
final class PythonAccesses implements VariableAccesses {

  private static final Set<String> NOTHING = Set.of("global_stmt", "nonlocal_stmt", "pass_stmt");

  private final boolean tracked;
  private final PythonNameAccesses names;
  private final PythonStatementAccesses statements;
  private final Map<SyntaxNode, List<VariableAccess>> planned = new IdentityHashMap<>();
  private final Map<String, Span> declarations = new HashMap<>();

  /**
   * Creates the finder of one body.
   *
   * @param excluded the names the body declares {@code global} or {@code nonlocal}
   * @param tracked false when the body has a star import, so no name of it is known to be local
   */
  PythonAccesses(final Set<String> excluded, final boolean tracked) {
    this.tracked = tracked;
    this.names = new PythonNameAccesses(excluded);
    this.statements = new PythonStatementAccesses(names);
  }

  /**
   * Plans the accesses of a node that the lowering adds with a fixed role, such as the target of a
   * {@code for} loop, which writes its names, or a loop iterable, which reads them.
   *
   * @param node the node the lowering adds next
   * @param writes true when the names of the node are written, false when they are read
   */
  void plan(final SyntaxNode node, final boolean writes) {
    final List<VariableAccess> found = new ArrayList<>();
    if (writes) {
      names.targets(node, found);
    } else {
      names.reads(node, found);
    }
    planned.put(node, found);
  }

  /** Records the parameters, whose own spans are the declarations of their names. */
  void declare(final List<VariableAccess> parameters) {
    parameters.forEach(parameter -> declarations.putIfAbsent(parameter.name(), parameter.span()));
  }

  @Override
  public List<VariableAccess> of(final SyntaxNode node) {
    if (!tracked) {
      return List.of();
    }
    final List<VariableAccess> found = planned.get(node);
    if (found != null) {
      return declared(found);
    }
    final List<VariableAccess> classified = new ArrayList<>();
    classify(node, classified);
    return declared(classified);
  }

  /** Python declares nothing: the first occurrence in the body is the declaration of a name. */
  private List<VariableAccess> declared(final List<VariableAccess> found) {
    final List<VariableAccess> out = new ArrayList<>();
    for (final VariableAccess access : found) {
      final Span declaration = declarations.computeIfAbsent(access.name(), name -> access.span());
      out.add(new VariableAccess(access.name(), access.kind(), access.span(), declaration));
    }
    return out;
  }

  private void classify(final SyntaxNode node, final List<VariableAccess> out) {
    if (NOTHING.contains(node.kind())) {
      return;
    }
    if (!statements.classify(node, out)) {
      names.reads(node, out);
    }
  }
}
