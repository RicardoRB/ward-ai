package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.FileLines;
import dev.ward.domain.gate.LineHits;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class CoverageMatchingTest {

  private static final Path ROOT = Path.of("/proj");

  private static LineHits.Entry entry(
      final LineHits.Kind kind, final String name, final int line, final boolean covered) {
    return new LineHits.Entry(
        kind,
        name,
        new FileLines(
            new TreeSet<>(Set.of(line)), covered ? new TreeSet<>(Set.of(line)) : new TreeSet<>()));
  }

  private static LineHits report(final String path, final LineHits.Entry... entries) {
    return new LineHits(ROOT.resolve(path), List.of(entries));
  }

  @Test
  void anLcovPathRelativeToThePackageOfTheReportIsResolvedUpwards() {
    final LineHits report =
        report("web/coverage/lcov.info", entry(LineHits.Kind.PATH, "src/cart.ts", 3, true));

    final CoverageMatching matching =
        CoverageMatching.of(ROOT, List.of(report), Set.of("web/src/cart.ts", "other.ts"));

    assertThat(matching.lines()).containsOnlyKeys("web/src/cart.ts");
    assertThat(matching.namedBy().get("web/src/cart.ts")).containsExactly(report.report());
  }

  @Test
  void anLcovPathThatExistsAtTheRootWinsAndAnAbsolutePathMustBeInsideTheProject() {
    final LineHits report =
        report(
            "coverage/lcov.info",
            entry(LineHits.Kind.PATH, "src/a.ts", 1, true),
            entry(LineHits.Kind.PATH, "/proj/src/b.ts", 1, true),
            entry(LineHits.Kind.PATH, "/elsewhere/src/c.ts", 1, true));

    final CoverageMatching matching =
        CoverageMatching.of(
            ROOT, List.of(report), Set.of("src/a.ts", "src/b.ts", "src/c.ts", "coverage/src/a.ts"));

    assertThat(matching.lines()).containsOnlyKeys("src/a.ts", "src/b.ts");
  }

  @Test
  void aJaCoCoPackageAndFileMatchTheSourceWhosePathEndsWithIt() {
    final LineHits report =
        report(
            "target/site/jacoco/jacoco.xml",
            entry(LineHits.Kind.PACKAGE_FILE, "com/shop/Cart.java", 3, false));

    final CoverageMatching matching =
        CoverageMatching.of(
            ROOT,
            List.of(report),
            Set.of("src/main/java/com/shop/Cart.java", "src/main/java/Cart.java"));

    assertThat(matching.lines()).containsOnlyKeys("src/main/java/com/shop/Cart.java");
  }

  @Test
  void aClassInTwoModulesIsMatchedToTheModuleOfTheReport() {
    final LineHits report =
        report(
            "api/target/site/jacoco/jacoco.xml",
            entry(LineHits.Kind.PACKAGE_FILE, "com/shop/Cart.java", 3, true));

    final CoverageMatching matching =
        CoverageMatching.of(
            ROOT,
            List.of(report),
            Set.of(
                "domain/src/main/java/com/shop/Cart.java", "api/src/main/java/com/shop/Cart.java"));

    assertThat(matching.lines()).containsOnlyKeys("api/src/main/java/com/shop/Cart.java");
  }

  @Test
  void twoReportsOfOneFileCoverALineWhenEitherDoes() {
    final LineHits first = report("a/lcov.info", entry(LineHits.Kind.PATH, "x.ts", 12, false));
    final LineHits second = report("b/lcov.info", entry(LineHits.Kind.PATH, "x.ts", 12, true));

    final CoverageMatching matching =
        CoverageMatching.of(ROOT, List.of(first, second), Set.of("x.ts"));

    assertThat(matching.lines().get("x.ts").covered()).containsExactly(12);
    assertThat(matching.namedBy().get("x.ts")).hasSize(2);
  }

  @Test
  void aReportThatNamesNoSourceFileMatchesNothing() {
    final LineHits report =
        report("lcov.info", entry(LineHits.Kind.PATH, "node_modules/x.js", 1, true));

    assertThat(CoverageMatching.of(ROOT, List.of(report), Set.of("src/a.ts")).lines()).isEmpty();
  }
}
