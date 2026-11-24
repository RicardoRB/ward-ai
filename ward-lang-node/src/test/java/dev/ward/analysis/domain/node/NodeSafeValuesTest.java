package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.NodeUnsafeValueCheck;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Which values the sink check accepts: constants, numbers, and names that only they reach. */
class NodeSafeValuesTest {
  private static final Pattern MARKUP = Pattern.compile("\\.innerHTML=");
  private static final Pattern REQUEST = Pattern.compile("^fetch\\(");

  @Test
  void constantsAndNumbersAreSafe() {
    assertThat(markup("function f(a, b) { el.innerHTML = `<i>${a * b}</i>`; }")).isEmpty();
    assertThat(markup("function f(x) { el.innerHTML = Number(x) + '%'; }")).isEmpty();
    assertThat(markup("function f(xs) { el.innerHTML = '<b>' + xs.length + '</b>'; }")).isEmpty();
    assertThat(markup("function f(n) { el.innerHTML = n.toFixed(2) + ' KB'; }")).isEmpty();
    assertThat(markup("function f(a) { el.innerHTML = a > 1 ? 'many' : 'one'; }")).isEmpty();
    assertThat(markup("function f(a) { el.innerHTML = -a; }")).isEmpty();
  }

  @Test
  void parametersAndCallsAreNotSafe() {
    assertThat(markup("function f(a) { el.innerHTML = a; }")).hasSize(1);
    assertThat(markup("function f(a) { el.innerHTML = '<b>' + a + '</b>'; }")).hasSize(1);
    assertThat(markup("function f(a) { el.innerHTML = a.toUpperCase(); }")).hasSize(1);
    assertThat(markup("function f(a) { el.innerHTML = a ? 'x' : a; }")).hasSize(1);
  }

  @Test
  void aNameIsSafeOnlyWhenEveryAssignmentIs() {
    assertThat(
        markup(
            "function f(n, c) { let m = 'a'; if (c) { m += n * 2; } else { m -= 1; } "
                + "el.innerHTML = m; }"
        )
    ).isEmpty();
    assertThat(
        markup("function f(t, c) { let m = 'a'; if (c) { m = t; } el.innerHTML = m; }")
    ).hasSize(1);
    assertThat(markup("function f(t) { let m = 'a'; m += t; el.innerHTML = m; }")).hasSize(1);
    assertThat(markup("function f(xs) { for (const x of xs) { el.innerHTML = x; } }")).hasSize(1);
    assertThat(markup("function f() { let m = 'a'; while (m) { m += 'b'; } el.innerHTML = m; }"))
        .isEmpty();
  }

  @Test
  void aSocketAddressIsALocalHost() {
    assertThat(
        request(
            "function f(s) { const a = s.address() as {address: string}; "
                + "const h = a.address; return fetch(`http://${h}:80/`); }"
        )
    ).isEmpty();
    assertThat(request("function f(s) { return fetch(`http://${s.address().address}/x`); }"))
        .isEmpty();
    assertThat(request("function f(s) { return fetch(`http://[${s.address().address}]:1/`); }"))
        .isEmpty();
  }

  @Test
  void otherHostsAreReported() {
    assertThat(request("function f(c) { return fetch(`http://${c.host}/x`); }")).hasSize(1);
    assertThat(request("function f(s) { return fetch(`http://${s.address().address}.x.com/`); }"))
        .hasSize(1);
    assertThat(request("function f(p) { return fetch(`http://example.com:${p}/`); }")).hasSize(1);
    assertThat(request("function f(p) { return fetch(`http://x/${p}`); }")).hasSize(1);
    assertThat(request("function f(h) { return fetch(`${h}://x/`); }")).hasSize(1);
    assertThat(request("function f(h) { return fetch(h); }")).hasSize(1);
  }

  private static List<RuleMatch> markup(final String source) {
    return new NodeUnsafeValueCheck(MARKUP, false, NodeSafeValues.Mode.MARKUP, "m")
        .matches(tree(source).tree(), flow(source));
  }

  private static List<RuleMatch> request(final String source) {
    return new NodeUnsafeValueCheck(REQUEST, true, NodeSafeValues.Mode.LOCAL_HOST, "m")
        .matches(tree(source).tree(), flow(source));
  }

  private static ParseResult tree(final String source) {
    return new AntlrLanguageFrontend().parse(unit(source));
  }

  private static FlowView flow(final String source) {
    final ParseResult parsed = tree(source);
    return new FlowView(new NodeControlFlow().lower(unit(source), parsed.tree()).graphs());
  }

  private static SourceUnit unit(final String source) {
    return new SourceUnit("src/a.ts", AnalysisLanguage.TYPESCRIPT, source);
  }
}
