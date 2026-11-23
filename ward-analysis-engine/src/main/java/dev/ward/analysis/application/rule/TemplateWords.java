package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.rule.ProjectFiles;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The identifiers that the template files of a project spell, such as the {@code get_n_down} of
 * {@code {{ project.get_n_down }}} or the {@code LoginForm} of {@code Forms.auth.LoginForm()}.
 */
final class TemplateWords {
  private static final Set<String> SUFFIXES = Set.of(".html", ".jinja", ".jinja2", ".j2", ".txt");
  private static final Pattern WORD = Pattern.compile("[A-Za-z_]\\w*");

  private TemplateWords() {}

  /** Every identifier of every template file of {@code files}. */
  static Set<String> of(final ProjectFiles files) {
    final Set<String> words = new HashSet<>();
    for (final String path : files.paths()) {
      if (SUFFIXES.stream().anyMatch(path::endsWith)) {
        final Matcher matcher = WORD.matcher(files.text(path).orElse(""));
        while (matcher.find()) {
          words.add(matcher.group());
        }
      }
    }
    return words;
  }
}
