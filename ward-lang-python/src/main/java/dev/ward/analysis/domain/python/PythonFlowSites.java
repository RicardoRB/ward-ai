package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowSites;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds the flow sites of one Python statement: calls and member reads, with the module of their
 * receiver when an import tells it. Inner sites come before the sites that contain them, and a
 * nested function, class or lambda is another body, so it is not entered.
 */
final class PythonFlowSites implements FlowSites {
  private static final String ATOM_EXPR = "atom_expr";
  private static final String OPEN_CALL = "(";
  private static final String OPEN_INDEX = "[";
  private static final String DOT = ".";
  private static final String AWAIT = "await";
  private static final String SUBPROCESS = "subprocess";
  private static final String HANDLE = "database-handle";
  private static final String VECTOR = "argument-vector";
  private static final Pattern HANDLE_SOURCE =
      Pattern.compile("(?:[A-Za-z_][\\w.]*\\.)?(?:connect|cursor)\\(.*\\)", Pattern.DOTALL);
  private static final Set<String> OTHER_BODIES =
      Set.of("funcdef", "classdef", "decorated", "async_funcdef", "lambdef", "lambdef_nocond");

  private final Map<String, String> imports;
  private final PythonValidatedSites validated;
  /** Names of this body assigned from a call to {@code cursor} or {@code connect}, so far. */
  private final Set<String> handles = new HashSet<>();

  /**
   * Creates the finder of one body.
   *
   * @param imports what the names bound by the imports of the file stand for
   */
  PythonFlowSites(final Map<String, String> imports) {
    this(imports, new PythonValidatedSites(imports, "", Set.of()));
  }

  /**
   * Creates the finder of one body of a module.
   *
   * @param imports what the names bound by the imports of the file stand for
   * @param validated the sites of the checked values of the body
   */
  PythonFlowSites(final Map<String, String> imports, final PythonValidatedSites validated) {
    this.imports = imports;
    this.validated = validated;
  }

  @Override
  public List<FlowSite> of(final SyntaxNode node) {
    final List<FlowSite> found = new ArrayList<>();
    walk(node, found);
    validated.add(node, found);
    learn(node);
    return found;
  }

  /**
   * Records what a plain assignment binds: a name assigned from a call to {@code cursor} or {@code
   * connect} is a database handle until it is assigned something else. shortcut: statement order
   * stands for flow order and only plain names are followed (no attributes, no {@code with ... as});
   * upgrade by following the scope bindings.
   */
  private void learn(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (!"expr_stmt".equals(node.kind())
        || parts.size() != PythonSyntax.THREE_PARTS
        || !PythonSyntax.isToken(parts.get(1), "=")) {
      return;
    }
    final String target = Chain.text(parts.get(0));
    if (HANDLE_SOURCE.matcher(Chain.text(parts.get(2))).matches()) {
      handles.add(target);
    } else {
      handles.remove(target);
    }
  }

  private void walk(final SyntaxNode node, final List<FlowSite> found) {
    if (OTHER_BODIES.contains(node.kind())) {
      return;
    }
    PythonQuerySites.of(node).ifPresent(found::add);
    PythonValidatedSites.comparison(node).ifPresent(found::add);
    final List<SyntaxNode> sum = PythonQuerySites.operandsOf(node);
    if (!sum.isEmpty()) {
      sum.forEach(operand -> walk(operand, found));
    } else if (ATOM_EXPR.equals(node.kind())) {
      chain(node, found);
    } else {
      node.children().forEach(child -> walk(child, found));
    }
  }

  /** An atom followed by trailers: each {@code .name} not called is a read, each call a site. */
  private void chain(final SyntaxNode node, final List<FlowSite> found) {
    final List<SyntaxNode> parts = new ArrayList<>(node.children());
    if (!parts.isEmpty() && AWAIT.equals(PythonSyntax.text(parts.get(0)))) {
      parts.remove(0);
    }
    final SyntaxNode atom = parts.get(0);
    walk(atom, found);
    final Chain chain = new Chain(atom);
    for (int index = 1; index < parts.size(); index++) {
      final SyntaxNode trailer = parts.get(index);
      final boolean calledNext =
          index + 1 < parts.size() && PythonCallArguments.opens(parts.get(index + 1), OPEN_CALL);
      step(chain, trailer, calledNext, found);
    }
  }

  private void step(
      final Chain chain,
      final SyntaxNode trailer,
      final boolean calledNext,
      final List<FlowSite> found
  ) {
    if (PythonCallArguments.opens(trailer, OPEN_CALL)) {
      final List<SyntaxNode> values = PythonCallArguments.values(trailer);
      values.forEach(value -> walk(value, found));
      found.addAll(call(chain, values, trailer));
      chain.afterCall(trailer);
    } else if (PythonCallArguments.opens(trailer, OPEN_INDEX)) {
      trailer.children().forEach(child -> walk(child, found));
      chain.afterIndex(trailer);
    } else {
      chain.member(trailer);
      if (!calledNext) {
        found.add(read(chain));
      }
    }
  }

  private FlowSite read(final Chain chain) {
    final String qualifier = chain.qualifier();
    return new FlowSite(
        FlowSiteKind.MEMBER_READ,
        chain.name(),
        qualifier,
        receiverOf(qualifier),
        List.of(),
        List.of(),
        chain.span()
    );
  }

  private List<FlowSite> call(
      final Chain chain,
      final List<SyntaxNode> values,
      final SyntaxNode trailer
  ) {
    if (chain.name().isEmpty()) {
      return List.of();
    }
    final String qualifier = chain.qualifier();
    final String imported = qualifier.isEmpty() ? imports.get(chain.name()) : null;
    if (imported != null) {
      final int dot = imported.lastIndexOf(DOT);
      final String module = dot < 0 ? "" : imported.substring(0, dot);
      return withVector(
          site(
              imported.substring(dot + 1),
              "",
              Optional.of(module).filter(m -> !m.isEmpty()),
              chain,
              PythonCallArguments.spans(module, values, trailer)
          ),
          values,
          trailer
      );
    }
    final Optional<String> receiver =
        receiverOf(qualifier)
            .or(() -> Optional.of(HANDLE).filter(h -> handles.contains(qualifier)))
            .or(() -> validated.own(qualifier, chain.name()));
    return withVector(
        site(
            chain.name(),
            qualifier,
            receiver,
            chain,
            PythonCallArguments.spans(receiver.orElse(""), values, trailer)
        ),
        values,
        trailer
    );
  }

  /** The call site, and for a vector given to {@code subprocess} the site of the vector. */
  private static List<FlowSite> withVector(
      final FlowSite call,
      final List<SyntaxNode> values,
      final SyntaxNode trailer
  ) {
    if (!PythonCallArguments.isVector(call.receiverType().orElse(""), values, trailer)) {
      return List.of(call);
    }
    final FlowSite vector =
        new FlowSite(
            FlowSiteKind.CALL,
            VECTOR,
            "",
            Optional.of(SUBPROCESS),
            List.of(),
            List.of(values.get(0).span()),
            call.span()
        );
    return List.of(call, vector);
  }

  private static FlowSite site(
      final String name,
      final String qualifier,
      final Optional<String> receiver,
      final Chain chain,
      final List<Span> spans
  ) {
    return new FlowSite(
        FlowSiteKind.CALL,
        name,
        qualifier,
        receiver,
        List.of(),
        spans,
        chain.span()
    );
  }

  /** The module of a dotted qualifier whose first name an import binds. */
  private Optional<String> receiverOf(final String qualifier) {
    return validated.receiverOf(qualifier);
  }

  /** The running state of one atom and its trailers. */
  private static final class Chain {
    private final Span start;
    private Span end;
    private String path;
    private String name;
    private String qualifier;

    Chain(final SyntaxNode atom) {
      this.start = atom.span();
      this.end = atom.span();
      this.path = text(atom);
      final boolean plain =
          atom.children().size() == 1 && PythonNames.NAME.equals(atom.children().get(0).kind());
      this.name = plain ? path : "";
      this.qualifier = "";
    }

    void member(final SyntaxNode trailer) {
      final SyntaxNode word = trailer.children().get(trailer.children().size() - 1);
      qualifier = path;
      name = text(word);
      path = path + DOT + name;
      end = trailer.span();
    }

    void afterCall(final SyntaxNode trailer) {
      path = path + text(trailer);
      name = "";
      qualifier = path;
      end = trailer.span();
    }

    void afterIndex(final SyntaxNode trailer) {
      afterCall(trailer);
    }

    String name() {
      return name;
    }

    String qualifier() {
      return qualifier;
    }

    Span span() {
      return new Span(
          start.startLine(),
          start.startColumn(),
          end.endLine(),
          end.endColumn(),
          start.startOffset(),
          end.endOffset()
      );
    }

    static String text(final SyntaxNode node) {
      final StringBuilder text = new StringBuilder();
      PythonSyntax.tokens(node).forEach(token -> text.append(PythonSyntax.text(token)));
      return text.toString();
    }
  }
}
