package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The rules that a {@code [select]} table selects, for commands that show the selection. */
public final class RuleSelection {

  private RuleSelection() {}

  /**
   * Returns the ids that {@code select} selects from {@code catalog}, the same ids the gate runs.
   *
   * @param select the {@code [select]} table
   * @param catalog every rule that exists
   * @return the selected ids and the problems that the table reports
   */
  public static Result selected(
      final RuleConfigFile.Select select, final List<RuleDefinition> catalog) {
    final RuleProblems problems = new RuleProblems(RuleConfigFile.DEFAULT_NAME, List.of());
    final Map<String, RuleDefinition> chosen =
        new RuleSelector(select, catalog, problems).selected();
    return new Result(Set.copyOf(chosen.keySet()), problems.messages());
  }

  /**
   * The selected ids and the problems found while selecting them.
   *
   * @param ids the selected rule ids
   * @param problems one message per value that names nothing
   */
  public record Result(Set<String> ids, List<String> problems) {
    /** Copies both sets. */
    public Result {
      ids = Set.copyOf(ids);
      problems = List.copyOf(problems);
    }
  }
}
