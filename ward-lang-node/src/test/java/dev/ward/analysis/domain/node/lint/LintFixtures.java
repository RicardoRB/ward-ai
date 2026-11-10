package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
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

/** Parses TypeScript text and builds the type facts of the typescript-eslint check tests. */
final class LintFixtures {

  static final String PATH = "src/app.ts";

  private LintFixtures() {}

  /** Returns the syntax tree of a TypeScript unit. */
  static SyntaxNode tree(final String text) {
    final ParseResult parsed =
        new AntlrLanguageFrontend().parse(new SourceUnit(PATH, AnalysisLanguage.TYPESCRIPT, text));
    return parsed.tree();
  }

  /** Collects facts by the start position of their span, as {@code line:column}. */
  static final class Facts {
    private final Map<String, TypeRef> types = new HashMap<>();
    private final Map<String, TypeShape> shapes = new HashMap<>();
    private final Map<String, CallTarget> calls = new HashMap<>();
    private final Map<String, TypeMembers> members = new HashMap<>();

    /** Records the type of the expression that starts at {@code position}, as line:column. */
    Facts typed(final String position, final TypeRef type) {
      types.put(position, type);
      return this;
    }

    /** Records the shape of the type that starts at {@code line:column}. */
    Facts shaped(final String position, final TypeShape shape) {
      shapes.put(position, shape);
      return this;
    }

    /** Records the call target of the invocation that starts at {@code line:column}. */
    Facts called(final String position, final CallTarget target) {
      calls.put(position, target);
      return this;
    }

    /** Records the members of the type of the expression that starts at {@code line:column}. */
    Facts membered(final String position, final TypeMembers found) {
      members.put(position, found);
      return this;
    }

    UnitTypeFacts build() {
      final Map<String, SpanFacts> facts = new HashMap<>();
      keys().forEach(start -> facts.put(start, spanFacts(start)));
      return new UnitTypeFacts(facts);
    }

    private SpanFacts spanFacts(final String start) {
      return new SpanFacts(
          types.get(start),
          shapes.get(start),
          calls.get(start),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          null,
          members.get(start));
    }

    private java.util.Set<String> keys() {
      final java.util.Set<String> keys = new java.util.HashSet<>(types.keySet());
      keys.addAll(shapes.keySet());
      keys.addAll(calls.keySet());
      keys.addAll(members.keySet());
      return keys;
    }
  }

  /** Runs a check and returns its findings as {@code line:column}, in order. */
  static List<String> positions(
      final TypedCheck check, final String text, final UnitTypeFacts types) {
    final SourceUnit unit = new SourceUnit(PATH, AnalysisLanguage.TYPESCRIPT, text);
    return starts(check.matches(tree(text), null, null, types, unit));
  }

  /** Runs a syntax-only check and returns its findings as {@code line:column}, in order. */
  static List<String> positions(final RuleCheck check, final String text) {
    return starts(check.matches(tree(text)));
  }

  private static List<String> starts(final List<RuleMatch> matches) {
    return matches.stream()
        .map(match -> match.span().startLine() + ":" + match.span().startColumn())
        .toList();
  }

  static TypeRef named(final String name) {
    return new TypeRef(name, List.of(), 0, false);
  }

  static TypeRef primitive(final String name) {
    return new TypeRef(name, List.of(), 0, true);
  }

  /** A function call target that takes {@code parameters} and returns {@code returns}. */
  static CallTarget call(final String name, final TypeRef returns, final TypeRef... parameters) {
    final TypeRef owner = named("Module");
    return new CallTarget(
        owner,
        name,
        List.of(parameters),
        returns,
        false,
        false,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        Map.of(),
        Map.of(),
        null);
  }
}
