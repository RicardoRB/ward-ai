package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The import control, module directive and module import checks on small sources. */
class ModuleChecksTest {

  private static final String FILE = "M.java";
  private static final String MESSAGE = "{value}";

  @Test
  void importControlAllowsTheFirstMatchingRule() {
    final String source =
        "import java.util.List;\nimport java.util.concurrent.Future;\n"
            + "import java.io.File;\nimport app.Thing;\n";
    final List<String> rules =
        List.of("disallow java.util.concurrent.*", "allow java.util.*", "allow app.Thing");
    final ImportControlCheck check = new ImportControlCheck(rules, Pattern.compile(".*"), MESSAGE);
    assertThat(MatchPositions.at(check.matches(ImportTrees.unit(source))))
        .containsExactly(MatchPositions.col1(2), MatchPositions.col1(MatchPositions.LINE_3));
    assertThat(MatchPositions.at(MatchPositions.textMatches(check, "src/Thing.java", source)))
        .containsExactly(MatchPositions.col1(2), MatchPositions.col1(MatchPositions.LINE_3));
    assertThat(
            MatchPositions.at(
                new ImportControlCheck(rules, Pattern.compile("^src/"), MESSAGE)
                    .matches(
                        ImportTrees.unit(source),
                        null,
                        new SourceUnit("other/Thing.java", AnalysisLanguage.JAVA, source))))
        .isEmpty();
    assertThat(
            MatchPositions.at(
                new ImportControlCheck(List.of(), Pattern.compile(".*"), MESSAGE)
                    .matches(ImportTrees.unit(source))))
        .isEmpty();
    assertThatThrownBy(
            () -> new ImportControlCheck(List.of("deny x"), Pattern.compile(".*"), MESSAGE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new ImportControlCheck(List.of("allow "), Pattern.compile(".*"), MESSAGE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void moduleDirectivesFollowTheOrderGroupingAndBlankLines() {
    final List<String> order = List.of("requires", "exports", "opens", "uses", "provides");
    final ModuleDirectiveOrderCheck check = new ModuleDirectiveOrderCheck(order, true, MESSAGE);
    assertThat(
            MatchPositions.textMatches(
                check, FILE, "module app {\n  requires a;\n\n  exports b;\n}\n"))
        .isEmpty();
    assertThat(
            MatchPositions.at(
                check.matches(
                    ImportTrees.unit("module app {\n  exports b;\n\n  requires a;\n}\n"))))
        .containsExactly("4:3");
    assertThat(
            MatchPositions.at(
                check.matches(ImportTrees.unit("module app {\n  requires a;\n  exports b;\n}\n"))))
        .isEmpty();
    assertThat(
            MatchPositions.at(
                MatchPositions.textMatches(
                    check, FILE, "module app {\n  requires a;\n  exports b;\n}\n")))
        .containsExactly("3:3");
    assertThat(
            MatchPositions.at(
                MatchPositions.textMatches(
                    check, FILE, "module app {\n  requires a;\n\n  requires b;\n}\n")))
        .containsExactly("4:3");
    assertThat(
            MatchPositions.at(
                MatchPositions.textMatches(
                    new ModuleDirectiveOrderCheck(order, false, MESSAGE),
                    FILE,
                    "module app {\n  requires a;\n  exports b;\n}\n")))
        .isEmpty();
  }

  @Test
  void moduleImportsStayAtTheTopOrBottomSortedAndSeparated() {
    final String source =
        "import module java.base;\nimport java.util.List;\nimport module java.sql;\n";
    assertThat(
            MatchPositions.at(
                new ModuleImportOrderCheck(true, false, MESSAGE).matches(ImportTrees.unit(source))))
        .containsExactly(MatchPositions.col1(MatchPositions.LINE_3));
    assertThat(
            MatchPositions.at(
                new ModuleImportOrderCheck(false, false, MESSAGE)
                    .matches(ImportTrees.unit(source))))
        .containsExactly(MatchPositions.col1(1));
    assertThat(
            MatchPositions.at(
                new ModuleImportOrderCheck(true, true, MESSAGE)
                    .matches(
                        ImportTrees.unit(source),
                        null,
                        new SourceUnit(FILE, AnalysisLanguage.JAVA, source))))
        .containsExactly(MatchPositions.col1(2), MatchPositions.col1(MatchPositions.LINE_3));
    assertThat(
            MatchPositions.at(
                MatchPositions.textMatches(
                    new ModuleImportOrderCheck(true, false, MESSAGE),
                    FILE,
                    "import module java.sql;\nimport module java.base;\n")))
        .containsExactly(MatchPositions.col1(2));
  }

  @Test
  void moduleImportsAreLimitedInNumberAndExcluded() {
    final String source =
        "import module java.base;\nimport module java.sql;\nimport java.util.List;\n";
    assertThat(
            MatchPositions.at(
                new AvoidModuleImportCheck(Set.of(), 0, MESSAGE).matches(ImportTrees.unit(source))))
        .containsExactly(MatchPositions.col1(1), MatchPositions.col1(2));
    assertThat(
            MatchPositions.at(
                new AvoidModuleImportCheck(Set.of(), 1, MESSAGE).matches(ImportTrees.unit(source))))
        .containsExactly(MatchPositions.col1(2));
    assertThat(
            MatchPositions.at(
                new AvoidModuleImportCheck(Set.of("java.base"), 0, MESSAGE)
                    .matches(ImportTrees.unit(source))))
        .containsExactly(MatchPositions.col1(2));
  }
}
