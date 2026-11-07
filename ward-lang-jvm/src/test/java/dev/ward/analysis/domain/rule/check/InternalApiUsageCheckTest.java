package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InternalApiUsageCheckTest extends ClassTreeNodes {

  private static final String MESSAGE = "{value}";
  private static final String HELPER = "com.sun.tools.Helper";
  private static final String VOID = "void";
  private static final String RUN = "run";

  @Test
  void reportsImportsOfTheInternalJdkApi() {
    final SyntaxNode root =
        compilationUnit(
            importOf("importsun.misc.Unsafe;"),
            importOf("importstaticjdk.internal.misc.Unsafe.getUnsafe;"),
            importOf("importjava.util.List;"));

    assertThat(internalApi(root, UnitTypeFacts.none()))
        .containsExactly("sun.misc.Unsafe", "jdk.internal.misc.Unsafe.getUnsafe");
  }

  @Test
  void reportsCallsWhoseTargetIsInTheInternalJdkApi() {
    final SyntaxNode root =
        compilationUnit(node(EXPRESSION, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, RUN, LINE, COLUMN)));
    final CallTarget target =
        new CallTarget(TypeRef.of(HELPER), RUN, List.of(), TypeRef.of(VOID), false, false);
    final Map<String, SpanFacts> facts = new HashMap<>();
    facts.put(
        LINE + ":" + COLUMN,
        new SpanFacts(null, null, target, List.of(), List.of(), List.of(), List.of(), null));

    assertThat(internalApi(root, new UnitTypeFacts(facts))).containsExactly(HELPER);
    assertThat(internalApi(root, UnitTypeFacts.none())).isEmpty();
  }

  private static List<String> internalApi(final SyntaxNode root, final UnitTypeFacts types) {
    return Trees.messages(
        new InternalApiUsageCheck(MESSAGE).matches(root, null, null, types, null));
  }
}
