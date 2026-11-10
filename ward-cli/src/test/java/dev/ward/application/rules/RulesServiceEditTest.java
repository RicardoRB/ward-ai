package dev.ward.application.rules;

import static dev.ward.application.rules.RuleFixtures.METHOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code ward rules show, enable, disable}: the rule view, and the edits of the selection file. */
class RulesServiceEditTest {
  private static final String FIELD = "java.no-field-di";
  private static final String METHOD_NAME = "java.method-length";
  private static final Path ROOT = Path.of("project");

  private final RuleFixtures.FakeSource source = new RuleFixtures.FakeSource();
  private final RuleFixtures.FakeFile file = new RuleFixtures.FakeFile();
  private RulesService service;

  @BeforeEach
  void service() {
    service = new RulesService(source, RuleFixtures.bodies(), file);
  }

  @Test
  void showGivesTheMessageOptionsAndDefaults() {
    final RuleView view = service.show(METHOD);

    assertThat(view.description()).isEqualTo("text of body-" + METHOD);
    assertThat(view.presets()).containsExactly("standard");
    assertThat(view.options()).hasSize(1);
    assertThat(view.options().get(0).name()).isEqualTo("max");
    assertThat(view.options().get(0).type()).isEqualTo("integer");
    assertThat(view.options().get(0).min()).isEqualTo(1L);
    assertThat(view.options().get(0).max()).isNull();
  }

  @Test
  void showSuggestsTheClosestIdForAnUnknownOne() {
    assertThatThrownBy(() -> service.show("ward:java/method-lenght"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("unknown rule id")
        .hasMessageContaining("did you mean \"" + METHOD + "\"");
  }

  @Test
  void enableCreatesTheFileWhenItIsMissing() {
    service.enable(ROOT, List.of(METHOD));

    assertThat(file.writes).isEqualTo(1);
    assertThat(file.written).contains("[select]\ninclude = [\"" + METHOD_NAME + "\"]");
  }

  @Test
  void enableKeepsCommentsOfAnExistingFile() {
    file.text = Optional.of("# keep me\n[select]\ninclude = [\"" + FIELD + "\"] # field\n");

    service.enable(ROOT, List.of(METHOD));

    assertThat(file.written)
        .contains("# keep me")
        .contains("# field")
        .contains("\"" + FIELD + "\", \"" + METHOD_NAME + "\"");
  }

  @Test
  void disableWritesAnExcludeEntry() {
    file.text = Optional.of("[select]\ninclude = [\"" + METHOD_NAME + "\"]\n");

    service.disable(ROOT, List.of(METHOD));

    assertThat(file.written)
        .isEqualTo("[select]\nexclude = [\"" + METHOD_NAME + "\"]\ninclude = []\n");
  }

  @Test
  void anUnknownIdStopsTheWholeEditAndWritesNothing() {
    assertThatThrownBy(() -> service.enable(ROOT, List.of(METHOD, "ward:java/nope")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("ward:java/nope");

    assertThat(file.writes).isZero();
  }
}
