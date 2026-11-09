package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests the Kotlin checks that read type facts, with facts given for each node by hand. */
final class KotlinTypedChecksTest {
  private static final String MESSAGE = "message";
  private static final String STRING = "java.lang.String";

  @Test
  void referentialEqualityReportsOnlyIdentityOnForbiddenOperands() {
    final String source =
        "fun f(a: String, b: String) {\n    val x = a === b\n    val y = a == b\n}\n";
    final SyntaxNode root = parse(source);
    final UnitTypeFacts facts = typed(root, "comparison", TypeRef.of(STRING));
    final List<RuleMatch> found =
        new KotlinReferentialEqualityCheck(Set.of("kotlin.String"), MESSAGE)
            .matches(root, null, null, facts, unit(source));
    assertThat(found).extracting(match -> match.span().startLine()).containsExactly(2);
    final UnitTypeFacts ints = typed(root, "comparison", TypeRef.of("int"));
    assertThat(
            new KotlinReferentialEqualityCheck(Set.of("kotlin.String"), MESSAGE)
                .matches(root, null, null, ints, unit(source)))
        .isEmpty();
  }

  @Test
  void mapLookupWithNotNullOperatorReportsOnlyMaps() {
    final String source =
        "fun f(m: Map<String, String>) {\n    val a = m[\"k\"]!!\n    val b = m.get(\"k\")!!\n}\n";
    final SyntaxNode root = parse(source);
    final UnitTypeFacts maps = typed(root, "atomicExpression", TypeRef.of("java.util.Map"));
    assertThat(new KotlinMapGetNotNullCheck(MESSAGE).matches(root, null, null, maps, unit(source)))
        .extracting(match -> match.span().startLine())
        .containsExactly(2, 3);
    final UnitTypeFacts none = UnitTypeFacts.none();
    assertThat(new KotlinMapGetNotNullCheck(MESSAGE).matches(root, null, null, none, unit(source)))
        .isEmpty();
  }

  @Test
  void ignoredReturnValueReportsAnnotatedStatementCallsOnly() {
    final String source = "fun f(r: R) {\n    r.save()\n}\n";
    final SyntaxNode root = parse(source);
    final CallTarget target = target(List.of("com.x.CheckReturnValue"));
    final UnitTypeFacts annotated = calls(root, "2:5", target);
    final KotlinIgnoredReturnValueCheck check =
        new KotlinIgnoredReturnValueCheck(
            Set.of("*.CheckResult", "*.CheckReturnValue"),
            Set.of("*.CanIgnoreReturnValue"),
            MESSAGE);
    assertThat(check.matches(root, null, null, annotated, unit(source))).hasSize(1);
    final CallTarget exempt =
        target(List.of("com.x.CheckReturnValue", "com.x.CanIgnoreReturnValue"));
    assertThat(check.matches(root, null, null, calls(root, "2:5", exempt), unit(source))).isEmpty();
  }

  @Test
  void redundantSuspendReportsOnlyBodiesWithoutSuspendingCalls() {
    final String source = "suspend fun a(): Int {\n    return 1\n}\n";
    final SyntaxNode root = parse(source);
    final KotlinRedundantSuspendCheck check = new KotlinRedundantSuspendCheck(MESSAGE);
    assertThat(check.matches(root, null, null, UnitTypeFacts.none(), unit(source)))
        .extracting(match -> match.span().startLine())
        .containsExactly(1);
    final String withCall = "suspend fun a(): Int {\n    b()\n}\n";
    final SyntaxNode callRoot = parse(withCall);
    final CallTarget suspending =
        new CallTarget(
            TypeRef.of("app.A"),
            "b",
            List.of(TypeRef.of("kotlin.coroutines.Continuation")),
            null,
            true,
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
    assertThat(
            check.matches(callRoot, null, null, calls(callRoot, "2:5", suspending), unit(withCall)))
        .isEmpty();
    assertThat(check.matches(callRoot, null, null, UnitTypeFacts.none(), unit(withCall))).isEmpty();
  }

  private static CallTarget target(final List<String> annotations) {
    return new CallTarget(
        TypeRef.of("app.Repo"),
        "save",
        List.of(),
        null,
        false,
        false,
        annotations,
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

  /** Gives every node of {@code kind} the type {@code type}. */
  private static UnitTypeFacts typed(final SyntaxNode root, final String kind, final TypeRef type) {
    final Map<String, SpanFacts> facts = new HashMap<>();
    for (final SyntaxNode node : nodes(root, kind)) {
      facts.put(UnitTypeFacts.startKey(node.span()), facts(type, null));
    }
    return new UnitTypeFacts(facts);
  }

  /** Gives the postfix call starting at {@code key} the call target {@code target}. */
  private static UnitTypeFacts calls(
      final SyntaxNode root, final String key, final CallTarget target) {
    final Map<String, SpanFacts> facts = new HashMap<>();
    for (final SyntaxNode node : nodes(root, "postfixUnaryExpression")) {
      if (key.equals(UnitTypeFacts.startKey(node.span()))) {
        facts.put(key, facts(null, target));
      }
    }
    return new UnitTypeFacts(facts);
  }

  private static SpanFacts facts(final TypeRef type, final CallTarget call) {
    return new SpanFacts(type, null, call, List.of(), List.of(), List.of(), List.of(), null, null);
  }

  private static List<SyntaxNode> nodes(final SyntaxNode root, final String kind) {
    final List<SyntaxNode> found = new ArrayList<>();
    collect(root, kind, found);
    return found;
  }

  private static void collect(
      final SyntaxNode node, final String kind, final List<SyntaxNode> found) {
    if (kind.equals(node.kind())) {
      found.add(node);
    }
    node.children().forEach(child -> collect(child, kind, found));
  }

  private static SyntaxNode parse(final String source) {
    return new AntlrLanguageFrontend().parse(unit(source)).tree();
  }

  private static SourceUnit unit(final String source) {
    return new SourceUnit("a.kt", AnalysisLanguage.KOTLIN, source);
  }
}
