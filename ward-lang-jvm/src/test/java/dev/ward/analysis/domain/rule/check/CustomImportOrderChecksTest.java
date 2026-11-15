package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The custom import order check: token groups, sorting, blank lines and bad options. */
class CustomImportOrderChecksTest {
  private static final String MESSAGE = "{value}";
  private static final String FILE = "M.java";

  @Test
  void customImportOrderChecksTokenGroupsAndSorting() {
    final CustomImportOrderCheck.Rules rules =
        new CustomImportOrderCheck.Rules(
            "STATIC###THIRD_PARTY_PACKAGE",
            "^$",
            "^(java|javax)\\.",
            ".*"
        );
    final String ordered =
        "import static org.x.A.b;\n\nimport java.util.List;\nimport org.example.Zed;\n"
            + "import org.example.Alpha;\n";
    assertThat(
        MatchPositions.at(
            MatchPositions.textMatches(
                new CustomImportOrderCheck(rules, true, true, MESSAGE),
                FILE,
                ordered
            )
        )
    )
        .containsExactly(MatchPositions.col1(MatchPositions.LINE_5));
    assertThat(
        MatchPositions.at(
            MatchPositions.textMatches(
                new CustomImportOrderCheck(rules, false, true, MESSAGE),
                FILE,
                ordered
            )
        )
    )
        .isEmpty();
  }

  @Test
  void customImportOrderChecksBlankLinesBetweenGroups() {
    final CustomImportOrderCheck.Rules rules =
        new CustomImportOrderCheck.Rules(
            "STATIC###THIRD_PARTY_PACKAGE",
            "^$",
            "^(java|javax)\\.",
            ".*"
        );
    assertThat(
        MatchPositions.at(
            MatchPositions.textMatches(
                new CustomImportOrderCheck(rules, true, true, MESSAGE),
                FILE,
                "import static org.x.A.b;\nimport java.util.List;\n"
            )
        )
    )
        .containsExactly(MatchPositions.col1(2));
    assertThat(
        MatchPositions.at(
            MatchPositions.textMatches(
                new CustomImportOrderCheck(rules, true, true, MESSAGE),
                FILE,
                "import org.example.Alpha;\n\nimport org.example.Zed;\n"
            )
        )
    )
        .containsExactly(MatchPositions.col1(MatchPositions.LINE_3));
  }

  @Test
  void customImportOrderChecksSingleGroupAndSpecialImports() {
    final CustomImportOrderCheck.Rules onlyStatic =
        new CustomImportOrderCheck.Rules("STATIC", "^$", "^java\\.", ".*");
    assertThat(
        MatchPositions.at(
            MatchPositions.textMatches(
                new CustomImportOrderCheck(onlyStatic, true, true, MESSAGE),
                FILE,
                "import java.util.List;\n"
            )
        )
    )
        .containsExactly(MatchPositions.col1(1));
    final CustomImportOrderCheck.Rules grouped =
        new CustomImportOrderCheck.Rules(
            "SPECIAL_IMPORTS####STANDARD_JAVA_PACKAGE###THIRD_PARTY_PACKAGE",
            "^org\\.x\\..*",
            "^java\\.",
            ".*"
        );
    assertThat(
        MatchPositions.at(
            MatchPositions.textMatches(
                new CustomImportOrderCheck(grouped, true, true, MESSAGE),
                FILE,
                "import java.util.List;\nimport org.x.A;\n"
            )
        )
    )
        .isEmpty();
  }

  @Test
  void customImportOrderRejectsUnknownTokensAndBadPatterns() {
    assertThatThrownBy(
        () ->
            new CustomImportOrderCheck(
                new CustomImportOrderCheck.Rules("STATIC###SAME_PACKAGE(3)", "^$", "^j", ".*"),
                true,
                true,
                MESSAGE
            )
    )
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
        () ->
            new CustomImportOrderCheck(
                new CustomImportOrderCheck.Rules("STATIC", "(", "^j", ".*"),
                true,
                true,
                MESSAGE
            )
    )
        .isInstanceOf(IllegalArgumentException.class);
  }
}
