package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builders of the spans, syntax nodes and scope views that the Swift style check tests share. */
final class SwiftTestNodes {

  private SwiftTestNodes() {}

  static Span span(final int start, final int end) {
    return new Span(1, start + 1, 1, end + 1, start, end);
  }

  static SyntaxNode node(
      final String kind, final int start, final int end, final SyntaxNode... children) {
    return new SyntaxNode(kind, span(start, end), Map.of(), List.of(children));
  }

  static SyntaxNode leaf(final String kind, final String text, final int start) {
    return new SyntaxNode(
        kind, span(start, start + text.length()), Map.of("text", text), List.of());
  }

  static ScopeView view(final UnitScopes scopes) {
    return new ScopeView(
        scopes,
        Map.of(),
        Set.of(),
        new ScopeAnalysis() {
          @Override
          public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
            return scopes;
          }

          @Override
          public Set<String> builtins(final Set<String> environments) {
            return Set.of();
          }
        });
  }
}
