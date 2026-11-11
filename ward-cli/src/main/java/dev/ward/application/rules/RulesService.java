package dev.ward.application.rules;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleGroup;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.application.gate.RuleConfigFile;
import dev.ward.application.gate.RuleSelection;
import dev.ward.domain.config.WardException;
import dev.ward.domain.rules.RuleSelectionEdit;
import dev.ward.domain.rules.RuleSelectionFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * The {@code ward rules} use cases: list and show the rules, and enable or disable them in {@code
 * ward-rules.toml}. The service reads and writes through ports only.
 */
public final class RulesService {
  private static final Logger LOGGER = Logger.getLogger(RulesService.class.getName());

  private final AnalysisRuleSource source;
  private final RuleBodyReader bodies;
  private final RuleSelectionFile file;

  /** Creates the service over the rule source, the body reader and the selection file. */
  @Inject
  public RulesService(
      final AnalysisRuleSource source, final RuleBodyReader bodies, final RuleSelectionFile file) {
    this.source = source;
    this.bodies = bodies;
    this.file = file;
  }

  /**
   * Lists the rules that match the filters, in catalog order.
   *
   * @param root the project root, where {@code ward-rules.toml} is read
   * @param filter the filters
   * @throws WardException if {@code --selected} is set and the configuration file has problems
   */
  public List<RuleView> list(final Path root, final RuleFilter filter) {
    final List<RuleDefinition> catalog = source.load().rules();
    final Set<String> selected = filter.selected() ? selectedIds(root, catalog) : null;
    return catalog.stream()
        .filter(rule -> matchesPreset(rule, filter.preset()))
        .filter(rule -> filter.language() == null || rule.covers(filter.language()))
        .filter(
            rule ->
                filter.category() == null || RuleGroup.matches(filter.category(), rule.category()))
        .filter(rule -> selected == null || selected.contains(rule.id()))
        .map(rule -> RuleViews.of(bodies, rule))
        .toList();
  }

  /**
   * Shows one rule.
   *
   * @throws WardException if no rule has the id; the message suggests the closest id
   */
  public RuleView show(final String id) {
    return RuleViews.of(bodies, rule(id, source.load().rules()));
  }

  /**
   * Adds each id to {@code include} in {@code ward-rules.toml}, creating the file when it is
   * missing. Every id is checked before anything is written.
   *
   * @throws WardException if an id is unknown or the file cannot be edited
   */
  public void enable(final Path root, final List<String> ids) {
    change(root, ids, true);
  }

  /**
   * Adds each id to {@code exclude} in {@code ward-rules.toml}, creating the file when it is
   * missing. Every id is checked before anything is written.
   *
   * @throws WardException if an id is unknown or the file cannot be edited
   */
  public void disable(final Path root, final List<String> ids) {
    change(root, ids, false);
  }

  private void change(final Path root, final List<String> ids, final boolean enable) {
    final List<RuleDefinition> catalog = source.load().rules();
    final List<String> unknown =
        ids.stream()
            .filter(id -> catalog.stream().noneMatch(rule -> rule.id().equals(id)))
            .map(id -> RuleIdSuggestion.unknown(id, catalog))
            .toList();
    if (!unknown.isEmpty()) {
      throw new WardException(String.join("\n", unknown));
    }
    final String edited = edit(file.read(root).orElse(null), ids, enable);
    file.save(root, edited);
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("event=rules-edit enable=" + enable + " ids=" + ids.size());
    }
  }

  private static String edit(final String text, final List<String> ids, final boolean enable) {
    try {
      return enable ? RuleSelectionEdit.enable(text, ids) : RuleSelectionEdit.disable(text, ids);
    } catch (final IllegalArgumentException exception) {
      throw new WardException("ward-rules.toml: " + exception.getMessage(), exception);
    }
  }

  private Set<String> selectedIds(final Path root, final List<RuleDefinition> catalog) {
    final Optional<RuleConfigFile> configured = source.configuration(root, null);
    final RuleConfigFile.Select select =
        configured.map(RuleConfigFile::select).orElse(RuleConfigFile.Select.EMPTY);
    final RuleSelection.Result result = RuleSelection.selected(select, catalog);
    final List<String> problems = new ArrayList<>();
    configured.ifPresent(config -> problems.addAll(config.problems()));
    problems.addAll(result.problems());
    if (!problems.isEmpty()) {
      throw new WardException(String.join("\n", problems));
    }
    return result.ids();
  }

  private static boolean matchesPreset(final RuleDefinition rule, final RulePreset preset) {
    if (preset == null) {
      return true;
    }
    return preset == RulePreset.NONE ? rule.presets().isEmpty() : preset.selects(rule.presets());
  }

  private static RuleDefinition rule(final String id, final List<RuleDefinition> catalog) {
    return catalog.stream()
        .filter(rule -> rule.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new WardException(RuleIdSuggestion.unknown(id, catalog)));
  }
}
