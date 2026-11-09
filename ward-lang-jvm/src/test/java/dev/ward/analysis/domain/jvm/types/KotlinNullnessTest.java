package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.Nullness;
import org.junit.jupiter.api.Test;

/**
 * Tests the nullness that the Kotlin typer gives a use of a variable, one test per limit of the
 * approved narrow query: written types, smart casts, inferred types, Java members and vars.
 */
class KotlinNullnessTest {

  /** The typer walks the members of classes, so every source sits in one. */
  private static Probe kt(final String members) {
    return Probe.kotlin("class App {\n" + members + "}\n");
  }

  @Test
  void writtenTypeWithoutQuestionMarkIsNonNullAndWithItIsNullable() {
    final Probe probe =
        kt("  fun f(p: String, q: String?) {\n    val useP = p\n    val useQ = q\n  }\n");

    assertThat(probe.nullness("p")).isEqualTo(Nullness.NON_NULL);
    assertThat(probe.nullness("q")).isEqualTo(Nullness.NULLABLE);
  }

  @Test
  void nullableUseStaysNullableWithoutAGuardAndIsUnknownAfterOne() {
    final Probe unguarded = kt("  fun f(c: String?) {\n    val early = c\n  }\n");
    assertThat(unguarded.nullness("c")).isEqualTo(Nullness.NULLABLE);

    final Probe guarded =
        kt("  fun f(c: String?) {\n    if (c != null) {\n        val late = c\n    }\n  }\n");
    assertThat(guarded.nullness("c")).isEqualTo(Nullness.UNKNOWN);
  }

  @Test
  void inferredTypeIsNonNullOnlyForALiteralInitializer() {
    final Probe probe =
        kt(
            "  fun f(c: String?) {\n"
                + "    val lit = \"x\"\n"
                + "    val alias = c\n"
                + "    val nothing = null\n"
                + "    val useLit = lit\n"
                + "    val useAlias = alias\n"
                + "    val useNothing = nothing\n"
                + "  }\n");

    assertThat(probe.nullness("lit")).isEqualTo(Nullness.NON_NULL);
    assertThat(probe.nullness("alias")).isEqualTo(Nullness.UNKNOWN);
    assertThat(probe.nullness("nothing")).isEqualTo(Nullness.UNKNOWN);
  }

  @Test
  void javaMemberResultKeepsItsUnknownNullness() {
    final Probe probe = kt("  fun f(x: Any) {\n    val t = java.util.Objects.toString(x)\n  }\n");

    assertThat(probe.nullness("java.util.Objects.toString(x)")).isEqualTo(Nullness.UNKNOWN);
  }

  @Test
  void nullableVarIsUnknownWhileANonNullVarKeepsItsNonNullness() {
    final Probe probe =
        kt(
            "  fun f(p: String?) {\n"
                + "    var vNul: String? = p\n"
                + "    val useVar = vNul\n"
                + "    var vOk = \"x\"\n"
                + "    val useOk = vOk\n"
                + "  }\n");

    assertThat(probe.nullness("vNul")).isEqualTo(Nullness.UNKNOWN);
    assertThat(probe.nullness("vOk")).isEqualTo(Nullness.NON_NULL);
  }
}
