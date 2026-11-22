package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.SourceFormatter;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.format.java.JavaFormatter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What {@code ward format} writes for Java passes the rules that look at the same layout. */
class FormattedJavaRulesTest {
  private static final String ANNOTATION_LOCATION = "ward:java/openjdk-annotation-location";
  private static final String LEFT_CURLY = "ward:java/left-curly";
  private static final String RECORD =
      """
      package x;

      /** Example. */
      public record Example(
              @Deprecated(
                      since = "a message long enough that the annotation does not fit on one line at all",
                      forRemoval = false
              ) @SuppressWarnings("x") String first,
              @SuppressWarnings("y") String second
      ) {}
      """;
  private static final String RECORD_ONE_PER_LINE =
      """
      package x;

      /** Example. */
      public record Example(
              @Deprecated(
                      since = "a message long enough that the annotation does not fit on one line at all",
                      forRemoval = false
              )
              @SuppressWarnings("x")
              String first,
              @SuppressWarnings("y") String second
      ) {}
      """;
  private static final String ARRAY =
      """
      package x;

      /** An annotation with an array element. */
      public class Sample {
          /** Uses an array that does not fit on one line. */
          @Names({"first-name-of-the-list", "second-name-of-the-list", "third-name-of-the-list", "fourth"})
          public static final class User {}
      }
      """;

  @Test
  void formattedRecordComponentsPassAnnotationLocation() {
    assertThat(findings(ANNOTATION_LOCATION, format(RECORD))).isEmpty();
    assertThat(findings(ANNOTATION_LOCATION, format(RECORD_ONE_PER_LINE))).isEmpty();
  }

  @Test
  void formattedAnnotationArrayPassesLeftCurly() {
    assertThat(findings(LEFT_CURLY, format(ARRAY))).isEmpty();
  }

  /** The formatted text, or the input itself when the formatter leaves it unchanged. */
  static String format(final String source) {
    final SourceFormatter formatter = new JavaFormatter();
    final FormatResult result =
        formatter.format(source, FormatOptions.defaults(formatter.options()));
    return switch (result) {
      case FormatResult.Formatted formatted -> formatted.text();
      case FormatResult.Unchanged unchanged -> source;
      case FormatResult.Refused refused -> throw new AssertionError(refused.reason());
    };
  }

  private static List<AnalysisFinding> findings(final String ruleId, final String text) {
    final RuleDefinition rule =
        CatalogRuleDefinitions.shipped().stream()
            .filter(candidate -> ruleId.equals(candidate.id()))
            .findFirst()
            .orElseThrow();
    return new RuleFixtureRunner()
        .run(rule, "Sample.java", text, FixtureTypeFacts.of(Optional.empty()));
  }
}
