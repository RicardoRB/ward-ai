package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The calls and types that {@link LawOfDemeterCheck} trusts. */
class LawOfDemeterTrustTest extends LawOfDemeterTrees {
  private static final String MESSAGE = "{value}";
  private static final String RUN_METHOD = "run";
  private static final String RESULT = "sample.Result";

  @Test
  void trustsTheCallsThatReturnTheirReceiversTypeOrLiveInTheJdkOrAreGetters() {
    final SyntaxNode builder =
        compilationUnit(classNamed(CLASS_NAME, member(method(RUN_METHOD, 0, 0, chainOn(FOREIGN)))));
    final SyntaxNode getter =
        compilationUnit(
            classNamed(CLASS_NAME, member(method(RUN_METHOD, 0, 0, chainOf(FOREIGN, "getName"))))
        );
    final SyntaxNode plain =
        compilationUnit(classNamed(CLASS_NAME, member(method(RUN_METHOD, 0, 0, chainOf(FOREIGN)))));

    assertThat(lawOfDemeter(builder, typesOf(OTHER))).isEmpty();
    assertThat(lawOfDemeter(getter, resultTypes(OTHER, RESULT))).isEmpty();
    assertThat(lawOfDemeter(plain, resultTypes("java.util.List", RESULT))).isEmpty();
    assertThat(lawOfDemeter(plain, resultTypes("javax.sql.DataSource", RESULT))).isEmpty();
    assertThat(lawOfDemeter(plain, resultTypes(OTHER, RESULT))).containsExactly(display(OTHER));
  }

  @Test
  void trustsTheTypesThatMatchATrustedPattern() {
    final SyntaxNode plain =
        compilationUnit(classNamed(CLASS_NAME, member(method(RUN_METHOD, 0, 0, chainOf(FOREIGN)))));
    final LawOfDemeterCheck check =
        new LawOfDemeterCheck(MESSAGE, List.of(Pattern.compile(".*\\.Other")));

    assertThat(check.matches(plain, null, null, resultTypes(OTHER, RESULT), null)).isEmpty();
    assertThat(lawOfDemeter(plain, resultTypes(OTHER, RESULT))).hasSize(1);
  }

  /** Facts for {@code value.lookup()}, whose inner call starts one column after its receiver. */
  private UnitTypeFacts resultTypes(final String value, final String result) {
    final Map<String, SpanFacts> facts = new HashMap<>();
    facts.put(LINE + ":" + COLUMN, typedFacts(value));
    facts.put(LINE + ":" + (COLUMN + 1), typedFacts(result));
    return new UnitTypeFacts(facts);
  }

  private static SpanFacts typedFacts(final String type) {
    return new SpanFacts(
        TypeRef.of(type),
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null
    );
  }

  private static String display(final String type) {
    return TypeRef.of(type).display();
  }

  private static List<String> lawOfDemeter(final SyntaxNode root, final UnitTypeFacts types) {
    return Trees.messages(
        new LawOfDemeterCheck(MESSAGE, List.of()).matches(root, null, null, types, null)
    );
  }
}
