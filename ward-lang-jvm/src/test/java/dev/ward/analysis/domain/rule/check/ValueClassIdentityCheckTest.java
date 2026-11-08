package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValueClassIdentityCheckTest {

  private static final ValueClassIdentityCheck CHECK = new ValueClassIdentityCheck("message");
  private static final TypeRef INTEGER = TypeRef.of("java.lang.Integer");
  private static final TypeRef STRING = TypeRef.of("java.lang.String");
  private static final TypeRef BOOLEAN = TypeRef.of("java.lang.Boolean");

  private static SpanFacts type(final TypeRef type) {
    return ParsedFacts.type(type);
  }

  private static SpanFacts generic(final String name, final TypeRef... arguments) {
    return type(new TypeRef(name, List.of(arguments), 0, false));
  }

  private static SpanFacts call(final String owner, final String name) {
    return new SpanFacts(
        null,
        null,
        new CallTarget(
            TypeRef.of(owner),
            name,
            List.of(),
            null,
            false,
            false,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  private static List<String> run(final String body, final String node, final SpanFacts facts) {
    return new ParsedFacts("class C {\n  void t(Integer n) {\n    " + body + "\n  }\n}\n")
        .at("expression", node, facts)
        .run(CHECK);
  }

  @Test
  void synchronizingOnAValueBasedClassIsReported() {
    assertThat(CHECK.needs().facts()).containsExactlyInAnyOrder(FactKind.TYPE, FactKind.CALL);
    final String body = "synchronized (n) {\n    }";
    assertThat(run(body, "n", type(INTEGER))).containsExactly("3:5");
    assertThat(run(body, "n", type(STRING))).isEmpty();
    assertThat(run(body, "n", SpanFacts.UNKNOWN)).isEmpty();
  }

  @Test
  void referencesAndMapsKeyedByValueBasedClassesAreReported() {
    final String weak = "Object r = new java.lang.ref.WeakReference<>(n);";
    final String node = "newjava.lang.ref.WeakReference<>(n)";
    assertThat(run(weak, node, generic("java.lang.ref.WeakReference", INTEGER)))
        .containsExactly("3:16");
    assertThat(run(weak, node, generic("java.lang.ref.WeakReference", STRING))).isEmpty();
    assertThat(run(weak, node, generic("java.lang.ref.WeakReference"))).isEmpty();
    final String map = "Object m = new java.util.IdentityHashMap<>();";
    final String made = "newjava.util.IdentityHashMap<>()";
    assertThat(run(map, made, generic("java.util.IdentityHashMap", STRING, INTEGER))).hasSize(1);
    assertThat(run(map, made, generic("java.util.IdentityHashMap", STRING, BOOLEAN))).isEmpty();
    final String weakMap = "Object m = new java.util.WeakHashMap<>();";
    assertThat(
            run(
                weakMap,
                "newjava.util.WeakHashMap<>()",
                generic("java.util.WeakHashMap", INTEGER, STRING)))
        .hasSize(1);
  }

  @Test
  void identityHashCodeAndMonitorCallsOnAValueBasedClassAreReported() {
    final ParsedFacts hash =
        new ParsedFacts(
                "class C {\n  int t(Integer n) {\n    return System.identityHashCode(n);\n  }\n}\n")
            .at(
                "expression",
                "System.identityHashCode(n)",
                call("java.lang.System", "identityHashCode"))
            .at("expression", "n", type(INTEGER));
    assertThat(hash.run(CHECK)).containsExactly("3:12");
    final ParsedFacts wait =
        new ParsedFacts(
                "class C {\n  void t(Integer n) throws Exception {\n    n.wait();\n  }\n}\n")
            .at("expression", "n.wait()", call("java.lang.Object", "wait"))
            .at("expression", "n", type(INTEGER));
    assertThat(wait.run(CHECK)).containsExactly("3:5");
    final ParsedFacts other =
        new ParsedFacts("class C {\n  void t(String n) throws Exception {\n    n.wait();\n  }\n}\n")
            .at("expression", "n.wait()", call("java.lang.Object", "wait"))
            .at("expression", "n", type(STRING));
    assertThat(other.run(CHECK)).isEmpty();
  }

  @Test
  void aCacheBuiltWithWeakKeysOrValuesOfAValueBasedClassIsReported() {
    final String source =
        "class C {\n  Object t() {\n    return CacheBuilder.newBuilder().weakKeys().build();\n  }\n}\n";
    final String node = "CacheBuilder.newBuilder().weakKeys().build()";
    final SpanFacts build =
        withType(
            call("com.google.common.cache.CacheBuilder", "build"),
            generic("c.Cache", INTEGER, STRING));
    assertThat(new ParsedFacts(source).at("expression", node, build).run(CHECK))
        .containsExactly("3:12");
    final SpanFacts valueKeyed =
        withType(
            call("com.google.common.cache.CacheBuilder", "build"),
            generic("c.Cache", STRING, INTEGER));
    assertThat(new ParsedFacts(source).at("expression", node, valueKeyed).run(CHECK)).isEmpty();
    assertThat(
            new ParsedFacts(source.replace("weakKeys", "weakValues"))
                .at("expression", node.replace("weakKeys", "weakValues"), valueKeyed)
                .run(CHECK))
        .hasSize(1);
    assertThat(new ParsedFacts("class C {}").plain(CHECK)).isEmpty();
  }

  private static SpanFacts withType(final SpanFacts call, final SpanFacts type) {
    return new SpanFacts(
        type.type(), null, call.call(), List.of(), List.of(), List.of(), List.of(), null, null);
  }
}
