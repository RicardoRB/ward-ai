package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowSites;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
  private static final Set<String> OTHER_BODIES =
      Set.of("funcdef", "classdef", "decorated", "async_funcdef", "lambdef", "lambdef_nocond");

  private final Map<String, String> imports;

  /**
   * Creates the finder of one body.
   *
   * @param imports what the names bound by the imports of the file stand for
   */
  PythonFlowSites(final Map<String, String> imports) {
    this.imports = imports;
  }

  @Override
  public List<FlowSite> of(final SyntaxNode node) {
    final List<FlowSite> found = new ArrayList<>();
    walk(node, found);
    return found;
  }

  private void walk(final SyntaxNode node, final List<FlowSite> found) {
    if (OTHER_BODIES.contains(node.kind())) {
      return;
    }
    if (ATOM_EXPR.equals(node.kind())) {
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
      final List<FlowSite> found) {
    if (PythonCallArguments.opens(trailer, OPEN_CALL)) {
      final List<SyntaxNode> values = PythonCallArguments.values(trailer);
      values.forEach(value -> walk(value, found));
      call(chain, values).ifPresent(found::add);
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
        chain.span());
  }

  private Optional<FlowSite> call(final Chain chain, final List<SyntaxNode> values) {
    if (chain.name().isEmpty()) {
      return Optional.empty();
    }
    final String qualifier = chain.qualifier();
    final String imported = qualifier.isEmpty() ? imports.get(chain.name()) : null;
    if (imported != null) {
      final int dot = imported.lastIndexOf(DOT);
      final String module = dot < 0 ? "" : imported.substring(0, dot);
      return Optional.of(
          site(
              imported.substring(dot + 1),
              "",
              Optional.of(module).filter(m -> !m.isEmpty()),
              chain,
              values));
    }
    return Optional.of(site(chain.name(), qualifier, receiverOf(qualifier), chain, values));
  }

  private static FlowSite site(
      final String name,
      final String qualifier,
      final Optional<String> receiver,
      final Chain chain,
      final List<SyntaxNode> values) {
    final List<Span> spans = values.stream().map(SyntaxNode::span).toList();
    return new FlowSite(
        FlowSiteKind.CALL, name, qualifier, receiver, List.of(), spans, chain.span());
  }

  /** The module of a dotted qualifier whose first name an import binds. */
  private Optional<String> receiverOf(final String qualifier) {
    if (!qualifier.matches("[A-Za-z_][\\w.]*")) {
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
          end.endOffset());
    }

    private static String text(final SyntaxNode node) {
      final StringBuilder text = new StringBuilder();
      PythonSyntax.tokens(node).forEach(token -> text.append(PythonSyntax.text(token)));
      return text.toString();
    }
  }
}
