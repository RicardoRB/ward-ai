package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The sites that say a value was checked: the operands of a comparison, and the uses of a value
 * that a condition proved with a validator of the program, inside the statements that condition
 * guards. Which validators prove anything is decided later, from their bodies.
 */
final class PythonValidatedSites {
  /** The name of the site of the operands of a comparison. */
  static final String COMPARISON = "comparison";

  private static final String DOT = ".";
  private static final String COMP_OP = "comp_op";
  private static final String QUALIFIED = "[A-Za-z_][\\w.]*";
  private static final Set<String> OTHER_BODIES =
      Set.of("funcdef", "classdef", "decorated", "async_funcdef", "lambdef", "lambdef_nocond");

  private final Map<String, String> imports;
  private final String owner;
  private final Set<String> functions;
  private final Deque<List<Use>> regions = new ArrayDeque<>();

  /**
   * Creates the sites of one body of a module.
   *
   * @param imports what the names bound by the imports of the file stand for
   * @param owner the dotted name of the module of the file, or empty when it has none
   * @param functions the functions the module defines at its top level, which a bare call names
   */
  PythonValidatedSites(
      final Map<String,
      String> imports,
      final String owner,
      final Set<String> functions
  ) {
    this.imports = imports;
    this.owner = owner;
    this.functions = functions;
  }

  /**
   * One use of a value that a condition proved with a validator: the validator, the argument it
   * checked and the tokens of the expression that is that argument.
   */
  record Use(
      String name,
      String qualifier,
      Optional<String> receiver,
      int arity,
      List<String> tokens
  ) {}

  /** Starts a region of statements in which the uses are of validated values. */
  void enter(final List<Use> uses) {
    regions.push(uses);
  }

  /** Ends the region that {@link #enter} started last. */
  void leave() {
    regions.pop();
  }

  /** The module of a dotted qualifier whose first name an import binds. */
  Optional<String> receiverOf(final String qualifier) {
    if (!qualifier.matches(QUALIFIED)) {
      return Optional.empty();
    }
    final int dot = qualifier.indexOf(DOT);
    final String root = dot < 0 ? qualifier : qualifier.substring(0, dot);
    final String module = imports.get(root);
    if (module == null) {
      return Optional.empty();
    }
    return Optional.of(dot < 0 ? module : module + qualifier.substring(dot));
  }

  /** The module of this file, when the bare name is a function it defines at its top level. */
  Optional<String> own(final String qualifier, final String name) {
    return Optional.of(owner)
        .filter(module -> !module.isEmpty() && qualifier.isEmpty() && functions.contains(name));
  }

  /** Resolves the call of {@code callee} written as in the condition of a guard. */
  Optional<Use> use(
      final String callee,
      final int index,
      final int arity,
      final List<String> tokens
  ) {
    final int dot = callee.lastIndexOf(DOT);
    final String qualifier = dot < 0 ? "" : callee.substring(0, dot);
    final String bare = callee.substring(dot + 1);
    final String imported = qualifier.isEmpty() ? imports.get(bare) : null;
    if (imported != null) {
      final int last = imported.lastIndexOf(DOT);
      final Optional<String> module =
          Optional.of(imported.substring(0, Math.max(last, 0))).filter(m -> !m.isEmpty());
      return module.map(
          found -> new Use(
              "host:" + imported.substring(last + 1) + "@" + index,
              "",
              Optional.of(found),
              arity,
              tokens
          )
      );
    }
    return receiverOf(qualifier)
        .or(() -> own(qualifier, bare))
        .map(
            found -> new Use(
                "host:" + bare + "@" + index,
                qualifier,
                Optional.of(found),
                arity,
                tokens
            )
        );
  }

  /** The uses of validated values inside the statement, one site for each. */
  void add(final SyntaxNode node, final List<FlowSite> found) {
    validated(node, found);
  }

  /**
   * The operands of a comparison: whatever they hold, they are compared and never become text, so
   * the site says they are not the text of a query.
   */
  static Optional<FlowSite> comparison(final SyntaxNode node) {
    if (!"comparison".equals(node.kind())
        || node.children().stream().noneMatch(child -> COMP_OP.equals(child.kind()))) {
      return Optional.empty();
    }
    final List<Span> operands =
        node.children().stream()
            .filter(child -> !COMP_OP.equals(child.kind()))
            .map(SyntaxNode::span)
            .toList();
    return Optional.of(
        new FlowSite(
            FlowSiteKind.VALIDATED,
            COMPARISON,
            "",
            Optional.empty(),
            List.of(),
            operands,
            node.span()
        )
    );
  }

  /** The uses of validated values inside the statement, one site for each. */
  private void validated(final SyntaxNode node, final List<FlowSite> found) {
    if (regions.isEmpty() || OTHER_BODIES.contains(node.kind())) {
      return;
    }
    final List<SyntaxNode> nodes = PythonSyntax.tokens(node);
    final List<String> texts = PythonTokens.texts(nodes);
    regions.stream()
        .flatMap(List::stream)
        .forEach(use -> found.addAll(occurrences(use, nodes, texts)));
  }

  private static List<FlowSite> occurrences(
      final Use use,
      final List<SyntaxNode> nodes,
      final List<String> texts
  ) {
    final List<FlowSite> found = new ArrayList<>();
    final int size = use.tokens().size();
    for (int from = 0; from + size <= texts.size(); from++) {
      final boolean attribute = from > 0 && DOT.equals(texts.get(from - 1));
      if (!attribute && texts.subList(from, from + size).equals(use.tokens())) {
        found.add(site(use, PythonTokens.span(nodes, new PythonTokens.Range(from, from + size))));
      }
    }
    return found;
  }

  private static FlowSite site(final Use use, final Span span) {
    return new FlowSite(
        FlowSiteKind.VALIDATED,
        use.name(),
        use.qualifier(),
        use.receiver(),
        List.of("ward:arity:" + use.arity()),
        List.of(span),
        span
    );
  }
}
