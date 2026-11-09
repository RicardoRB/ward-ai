package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.Nullness;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests the Kotlin checks that read type facts, with facts given for each node by hand. */
final class KotlinTypedChecksTest {
  private static final String MESSAGE = "message";
  private static final String STRING = "java.lang.String";
  private static final String NON_NULL_MARKER = "dev.ward.kotlin.NonNull";
  private static final String NULLABLE_MARKER = "dev.ward.kotlin.Nullable";
  private static final int SECOND_LOOKUP_LINE = 3;
  private static final int SAFE_CALL_LINE = 3;

  @Test
  void referentialEqualityReportsOnlyIdentityOnForbiddenOperands() {
    final String source =
        "fun f(a: String, b: String) {\n    val x = a === b\n    val y = a == b\n}\n";
    final SyntaxNode root = KotlinTypedCheckFixtures.parse(source);
    final UnitTypeFacts facts =
        KotlinTypedCheckFixtures.typed(root, "comparison", TypeRef.of(STRING));
    final List<RuleMatch> found =
        new KotlinReferentialEqualityCheck(Set.of("kotlin.String"), MESSAGE)
            .matches(root, null, null, facts, KotlinTypedCheckFixtures.unit(source));
    assertThat(found).extracting(match -> match.span().startLine()).containsExactly(2);
    final UnitTypeFacts ints =
        KotlinTypedCheckFixtures.typed(root, "comparison", TypeRef.of("int"));
    assertThat(
            new KotlinReferentialEqualityCheck(Set.of("kotlin.String"), MESSAGE)
                .matches(root, null, null, ints, KotlinTypedCheckFixtures.unit(source)))
        .isEmpty();
  }

  @Test
  void mapLookupWithNotNullOperatorReportsOnlyMaps() {
    final String source =
        "fun f(m: Map<String, String>) {\n    val a = m[\"k\"]!!\n    val b = m.get(\"k\")!!\n}\n";
    final SyntaxNode root = KotlinTypedCheckFixtures.parse(source);
    final UnitTypeFacts maps =
        KotlinTypedCheckFixtures.typed(root, "atomicExpression", TypeRef.of("java.util.Map"));
    assertThat(
            new KotlinMapGetNotNullCheck(MESSAGE)
                .matches(root, null, null, maps, KotlinTypedCheckFixtures.unit(source)))
        .extracting(match -> match.span().startLine())
        .containsExactly(2, SECOND_LOOKUP_LINE);
    final UnitTypeFacts none = UnitTypeFacts.none();
    assertThat(
            new KotlinMapGetNotNullCheck(MESSAGE)
                .matches(root, null, null, none, KotlinTypedCheckFixtures.unit(source)))
        .isEmpty();
  }

  @Test
  void ignoredReturnValueReportsAnnotatedStatementCallsOnly() {
    final String source = "fun f(r: R) {\n    r.save()\n}\n";
    final SyntaxNode root = KotlinTypedCheckFixtures.parse(source);
    final CallTarget target = KotlinTypedCheckFixtures.target(List.of("com.x.CheckReturnValue"));
    final UnitTypeFacts annotated = KotlinTypedCheckFixtures.calls(root, "2:5", target);
    final KotlinIgnoredReturnValueCheck check =
        new KotlinIgnoredReturnValueCheck(
            Set.of("*.CheckResult", "*.CheckReturnValue"),
            Set.of("*.CanIgnoreReturnValue"),
            MESSAGE);
    assertThat(check.matches(root, null, null, annotated, KotlinTypedCheckFixtures.unit(source)))
        .hasSize(1);
    final CallTarget exempt =
        KotlinTypedCheckFixtures.target(
            List.of("com.x.CheckReturnValue", "com.x.CanIgnoreReturnValue"));
    assertThat(
            check.matches(
                root,
                null,
                null,
                KotlinTypedCheckFixtures.calls(root, "2:5", exempt),
                KotlinTypedCheckFixtures.unit(source)))
        .isEmpty();
  }

  @Test
  void redundantSuspendReportsOnlyBodiesWithoutSuspendingCalls() {
    final String source = "suspend fun a(): Int {\n    return 1\n}\n";
    final SyntaxNode root = KotlinTypedCheckFixtures.parse(source);
    final KotlinRedundantSuspendCheck check = new KotlinRedundantSuspendCheck(MESSAGE);
    assertThat(
            check.matches(
                root, null, null, UnitTypeFacts.none(), KotlinTypedCheckFixtures.unit(source)))
        .extracting(match -> match.span().startLine())
        .containsExactly(1);
    final String withCall = "suspend fun a(): Int {\n    b()\n}\n";
    final SyntaxNode callRoot = KotlinTypedCheckFixtures.parse(withCall);
    final CallTarget suspending =
        KotlinTypedCheckFixtures.callTarget(
            "b", List.of(TypeRef.of("kotlin.coroutines.Continuation")));
    assertThat(
            check.matches(
                callRoot,
                null,
                null,
                KotlinTypedCheckFixtures.calls(callRoot, "2:5", suspending),
                KotlinTypedCheckFixtures.unit(withCall)))
        .isEmpty();
    assertThat(
            check.matches(
                callRoot,
                null,
                null,
                UnitTypeFacts.none(),
                KotlinTypedCheckFixtures.unit(withCall)))
        .isEmpty();
  }

  @Test
  void nullableOperationReportsOnlyTheNullnessItIsAbout() {
    final String source = "fun f(p: String) {\n    val a = p!!\n    val b = p?.length\n}\n";
    final SyntaxNode root = KotlinTypedCheckFixtures.parse(source);
    final UnitTypeFacts nonNull =
        KotlinTypedCheckFixtures.marked(root, "atomicExpression", NON_NULL_MARKER);
    assertThat(
            new KotlinNullableOperationCheck(
                    KotlinNullableOperationCheck.Operation.NOT_NULL, Nullness.NON_NULL, MESSAGE)
                .matches(root, null, null, nonNull, KotlinTypedCheckFixtures.unit(source)))
        .extracting(match -> match.span().startLine())
        .containsExactly(2);
    assertThat(
            new KotlinNullableOperationCheck(
                    KotlinNullableOperationCheck.Operation.SAFE_CALL, Nullness.NON_NULL, MESSAGE)
                .matches(root, null, null, nonNull, KotlinTypedCheckFixtures.unit(source)))
        .extracting(match -> match.span().startLine())
        .containsExactly(SAFE_CALL_LINE);
    final UnitTypeFacts nullable =
        KotlinTypedCheckFixtures.marked(root, "atomicExpression", NULLABLE_MARKER);
    assertThat(
            new KotlinNullableOperationCheck(
                    KotlinNullableOperationCheck.Operation.NOT_NULL, Nullness.NULLABLE, MESSAGE)
                .matches(root, null, null, nullable, KotlinTypedCheckFixtures.unit(source)))
        .extracting(match -> match.span().startLine())
        .containsExactly(2);
  }
}
