package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the sites and accesses that the Java lowering adds to say what a call or guard proves. */
class JavaSyntheticSitesTest {
  private static final String PRIMITIVE =
      """
      class C {
        void run(String key, Object other) {
          int a = key.length();
          boolean b = key.equals("x");
          char c = key.charAt(0);
          int d = other.hashCode();
          String local = other.toString();
          log(local.length());
        }
      }
      """;

  private static final String RESPONSE =
      """
      class C {
        void json(Response response, String body) throws Exception {
          response.setContentType("application/json");
          response.getWriter().write(body);
        }

        void html(Response response, String body) throws Exception {
          response.setContentType("text/html");
          response.getWriter().write(body);
        }

        void none(Response response, String body) throws Exception {
          response.getWriter().write(body);
        }

        void header(Response response, String body) throws Exception {
          response.setContentType("application/json");
          response.setHeader("Content-Type", body);
          response.getWriter().write(body);
        }
      }
      """;

  private static final String GUARD =
      """
      import java.util.Set;

      class C {
        private static final Set<String> ALLOWED = Set.of("a", "b");
        private Set<String> open;

        void exits(String value) {
          if (!ALLOWED.contains(value)) {
            throw new IllegalArgumentException();
          }
          use(value);
        }

        void returns(String value) {
          if (!ALLOWED.contains(value)) return;
          use(value);
        }

        void continues(String value) {
          if (!ALLOWED.contains(value)) {
            log(value);
          }
          use(value);
        }

        void notFinal(String value) {
          if (!open.contains(value)) throw new IllegalArgumentException();
          use(value);
        }

        void shadowed(Set<String> ALLOWED, String value) {
          if (!ALLOWED.contains(value)) throw new IllegalArgumentException();
          use(value);
        }

        void withElse(String value) {
          if (!ALLOWED.contains(value)) throw new IllegalArgumentException(); else log(value);
          use(value);
        }
      }
      """;

  /** Scenario: a String call that returns a number or boolean gives a site; others do not. */
  @Test
  void stringCallsThatReturnPrimitivesAreMarked() {
    final List<String> names = names(PRIMITIVE, "run(2)", JavaPrimitiveCalls.NAME);
    assertThat(names).hasSize(3);
  }

  /** Scenario: a body that sets only JSON content types marks its writes. */
  @Test
  void jsonBodyMarksOnlyJsonWrites() {
    assertThat(names(RESPONSE, "json(2)", JavaJsonResponses.NAME)).hasSize(1);
    assertThat(names(RESPONSE, "html(2)", JavaJsonResponses.NAME)).isEmpty();
    assertThat(names(RESPONSE, "none(2)", JavaJsonResponses.NAME)).isEmpty();
    assertThat(names(RESPONSE, "header(2)", JavaJsonResponses.NAME)).isEmpty();
  }

  /** Scenario: a guard whose then-branch exits gives a write of the guarded variable past it. */
  @Test
  void allowListGuardKillsTheGuardedVariable() {
    assertThat(cleanWrites("exits(1)")).isEqualTo(1);
    assertThat(cleanWrites("returns(1)")).isEqualTo(1);
    assertThat(cleanWrites("continues(1)")).isZero();
    assertThat(cleanWrites("notFinal(1)")).isZero();
    assertThat(cleanWrites("shadowed(2)")).isZero();
    assertThat(cleanWrites("withElse(1)")).isZero();
  }

  private static List<String> names(final String source, final String body, final String name) {
    return graph(source, body).blocks().stream()
        .map(BasicBlock::sites)
        .flatMap(List::stream)
        .map(FlowSite::name)
        .filter(name::equals)
        .toList();
  }

  /** Counts the writes of {@code value} that are not a parameter's or an initializer's. */
  private static long cleanWrites(final String body) {
    return graph(GUARD, body).blocks().stream()
        .map(BasicBlock::accesses)
        .flatMap(List::stream)
        .filter(access -> access.kind() == AccessKind.WRITE)
        .map(VariableAccess::name)
        .filter("value"::equals)
        .count();
  }

  private static ControlFlowGraph graph(final String source, final String body) {
    return JavaFlowFixtures.lower(source).graphs().stream()
        .filter(g -> g.body().endsWith("#" + body))
        .findFirst()
        .orElseThrow();
  }
}
