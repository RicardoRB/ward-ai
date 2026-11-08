package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A parsed Java source with type facts keyed on the spans of its nodes, for check tests. */
final class ParsedFacts {
  static final TypeRef OBJECT = TypeRef.of("java.lang.Object");

  private final SourceUnit unit;
  private final SyntaxNode tree;
  private final Map<String, SpanFacts> facts = new HashMap<>();

  ParsedFacts(final String source) {
    unit = new SourceUnit("A.java", AnalysisLanguage.JAVA, source);
    tree = new AntlrLanguageFrontend().parse(unit).tree();
  }

  /** Sets the facts of every node of {@code kind} whose text is {@code text}. */
  ParsedFacts at(final String kind, final String text, final SpanFacts known) {
    SyntaxWalk.descendants(tree).stream()
        .filter(node -> kind.equals(node.kind()) && text.equals(SyntaxWalk.text(node)))
        .forEach(node -> facts.put(UnitTypeFacts.key(node.span()), known));
    return this;
  }

  /** Runs {@code check} and returns each finding as {@code line:column}. */
  List<String> run(final RuleCheck check) {
    final List<RuleMatch> found =
        check.matches(
            tree, FlowView.NONE, ScopeView.NONE, new UnitTypeFacts(Map.copyOf(facts)), unit);
    return found.stream().map(m -> m.span().startLine() + ":" + m.span().startColumn()).toList();
  }

  /** Runs the check without type facts and returns its findings. */
  List<RuleMatch> plain(final RuleCheck check) {
    return check.matches(tree);
  }

  /** Returns the span of the first node of {@code kind} whose text is {@code text}. */
  Span span(final String kind, final String text) {
    return SyntaxWalk.descendants(tree).stream()
        .filter(node -> kind.equals(node.kind()) && text.equals(SyntaxWalk.text(node)))
        .findFirst()
        .orElseThrow()
        .span();
  }

  static SpanFacts members(final TypeMembers members) {
    return new SpanFacts(
        null, null, null, List.of(), List.of(), List.of(), List.of(), null, members);
  }

  static SpanFacts type(final TypeRef type) {
    return new SpanFacts(type, null, null, List.of(), List.of(), List.of(), List.of(), null, null);
  }

  static SpanFacts typed(
      final TypeRef type,
      final TypeShape.Kind kind,
      final boolean isFinal,
      final TypeRef... above) {
    return new SpanFacts(
        type,
        new TypeShape(kind, false, isFinal, false, List.of()),
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        new TypeMembers(List.of(), List.of(), List.of(above), List.of()));
  }

  static CallTarget method(
      final String owner,
      final String name,
      final List<TypeRef> parameters,
      final String... annotations) {
    return new CallTarget(
        TypeRef.of(owner),
        name,
        parameters,
        TypeRef.of("void"),
        false,
        false,
        List.of(annotations),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  static TypeMembers withMethods(
      final List<CallTarget> declared, final List<CallTarget> inherited) {
    return new TypeMembers(declared, List.of(), List.of(OBJECT), List.of(), inherited, List.of());
  }
}
