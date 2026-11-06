package dev.ward.analysis.domain.python;

import static dev.ward.analysis.domain.python.PythonTypeFactsFixtures.facts;
import static dev.ward.analysis.domain.python.PythonTypeFactsFixtures.stubs;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests annotations, initializer inference and call targets of the Python type facts. */
class PythonTypeFactsTest {

  private static final Set<FactKind> CALL = Set.of(FactKind.CALL);
  private static final Set<FactKind> TYPE = Set.of(FactKind.TYPE);

  @Test
  void annotatedParameterCallTargetResolvesToBuiltinListAppend() {
    final String source = "def f(items: list[str]):\n    items.append(1)\n";

    final CallTarget target = facts(source, "items.append(1)", CALL).call();

    assertThat(target.declaringType().name()).isEqualTo("list");
    assertThat(target.name()).isEqualTo("append");
    assertThat(target.returnType()).isEqualTo(TypeRef.of("None"));
  }

  @Test
  void annotatedParameterTypeKeepsItsArguments() {
    final TypeRef type = facts("def f(items: list[str]):\n    items\n", "items", TYPE).type();

    assertThat(type.name()).isEqualTo("list");
    assertThat(type.arguments()).containsExactly(TypeRef.of("str"));
  }

  @Test
  void localInitializedOnceIsInferredFromItsInitializer() {
    final CallTarget target = facts("s = []\ns.append(1)\n", "s.append(1)", CALL).call();

    assertThat(target.declaringType().name()).isEqualTo("list");
  }

  @Test
  void localAssignedAgainIsUnknown() {
    assertThat(facts("s = []\ns = 3\ns.append(1)\n", "s.append(1)", CALL).call()).isNull();
  }

  @Test
  void constructorOfSourceClassHasItsClassAsType() {
    assertThat(facts("class Box:\n    pass\nb = Box()\n", "Box()", TYPE).type().name())
        .isEqualTo("Box");
  }

  @Test
  void unannotatedParameterIsUnknown() {
    assertThat(facts("def g(x):\n    x.append(1)\n", "x.append(1)", CALL).call()).isNull();
  }

  @Test
  void unsupportedAnnotationIsUnknown() {
    final String source =
        "from typing import Optional\ndef h(items: Optional[list[str]]):\n    items.append(1)\n";

    assertThat(facts(source, "items.append(1)", CALL).call()).isNull();
  }

  @Test
  void bundledStandardModuleStubGivesTheCallTarget() {
    final CallTarget target = facts("import json\njson.dumps(1)\n", "json.dumps(1)", CALL).call();

    assertThat(target.declaringType().name()).isEqualTo("json");
    assertThat(target.name()).isEqualTo("dumps");
    assertThat(target.returnType()).isEqualTo(TypeRef.of("str"));
  }

  @Test
  void stubDeclaredTwiceIsUnknown() {
    final PythonStubs stubs = stubs(Map.of("twice", "def f() -> int: ...\ndef f() -> str: ...\n"));

    assertThat(facts(stubs, "import twice\ntwice.f()\n", "twice.f()", TYPE).type()).isNull();
  }
}
