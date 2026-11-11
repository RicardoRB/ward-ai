package dev.ward.application.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code ward rules list}: the filters, and the selection that {@code --selected} reads. */
class RulesServiceListTest {
  private static final Path ROOT = Path.of("project");

  private final RuleFixtures.FakeSource source = new RuleFixtures.FakeSource();
  private final RuleFixtures.FakeFile file = new RuleFixtures.FakeFile();
  private RulesService service;

  @BeforeEach
  void service() {
    service = new RulesService(source, RuleFixtures.bodies(), file);
  }

  @Test
  void presetKeepsTheRulesThatThePresetSelects() {
    assertThat(ids(RuleFilter.parse("standard", null, null, false)))
        .containsExactly(RuleFixtures.METHOD, RuleFixtures.KOTLIN);
    assertThat(ids(RuleFilter.parse("strict", null, null, false)))
        .containsExactly(RuleFixtures.METHOD, RuleFixtures.FIELD, RuleFixtures.KOTLIN);
    assertThat(ids(RuleFilter.parse("none", null, null, false)))
        .containsExactly(RuleFixtures.STREAMS);
  }

  @Test
  void languageAndCategoryNarrowTheList() {
    assertThat(ids(RuleFilter.parse(null, "kotlin", null, false)))
        .containsExactly(RuleFixtures.KOTLIN);
    assertThat(ids(RuleFilter.parse(null, null, "architecture", false)))
        .containsExactly(RuleFixtures.FIELD, RuleFixtures.STREAMS);
  }

  @Test
  void categoryGroupMatchesAFamilyAndItsSubcategories() {
    source.rules =
        List.of(
            RuleFixtures.categorised("ward:java/inject", "security/injection"),
            RuleFixtures.categorised("ward:java/plain", "security"),
            RuleFixtures.categorised("ward:java/crypto", "security/crypto"),
            RuleFixtures.categorised("ward:java/naming", "naming"));

    assertThat(ids(RuleFilter.parse(null, null, "security", false)))
        .containsExactly("ward:java/inject", "ward:java/plain", "ward:java/crypto");
    assertThat(ids(RuleFilter.parse(null, null, "security/crypto", false)))
        .containsExactly("ward:java/crypto");
  }

  @Test
  void selectedKeepsOnlyTheRulesTheFileSelects() {
    source.configured = Optional.of(RuleFixtures.configuration());

    assertThat(ids(RuleFilter.parse(null, null, null, true)))
        .containsExactly(RuleFixtures.FIELD, RuleFixtures.KOTLIN);
  }

  @Test
  void selectedWithoutAFileUsesTheStandardPreset() {
    assertThat(ids(RuleFilter.parse(null, null, null, true)))
        .containsExactly(RuleFixtures.METHOD, RuleFixtures.KOTLIN);
  }

  @Test
  void anUnknownFilterValueIsRefused() {
    assertThatThrownBy(() -> RuleFilter.parse("loud", null, null, false))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("--preset")
        .hasMessageContaining("strict");
    assertThatThrownBy(() -> RuleFilter.parse(null, "cobol", null, false))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("--language");
  }

  private List<String> ids(final RuleFilter filter) {
    return service.list(ROOT, filter).stream().map(RuleView::id).toList();
  }
}
