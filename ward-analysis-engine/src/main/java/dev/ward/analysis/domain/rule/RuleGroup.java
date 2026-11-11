package dev.ward.analysis.domain.rule;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The category of a rule and the group names that select it. A category is a family, such as {@code
 * style}, or a family and one subcategory, such as {@code style/imports}. A group name is either of
 * those. A family group matches its own rules and the rules of its subcategories; a subcategory
 * group matches only its exact category.
 */
public final class RuleGroup {

  private static final char SEPARATOR = '/';
  private static final String SEPARATOR_TEXT = "/";
  private static final Pattern CATEGORY = Pattern.compile("[a-z][a-z0-9-]*(/[a-z][a-z0-9-]*)?");

  private RuleGroup() {}

  /** Returns whether a category has the shape {@code family} or {@code family/subcategory}. */
  public static boolean isValidCategory(final String category) {
    return CATEGORY.matcher(category).matches();
  }

  /** Returns whether the group name selects the category. */
  public static boolean matches(final String group, final String category) {
    return group.equals(category)
        || !group.contains(SEPARATOR_TEXT) && category.startsWith(group + SEPARATOR_TEXT);
  }

  /**
   * Returns every group name the categories give: each category, and the family of each
   * subcategory, in sorted order. Empty categories are left out.
   */
  public static Set<String> names(final Collection<String> categories) {
    final Set<String> names = new TreeSet<>();
    for (final String category : categories) {
      if (category.isEmpty()) {
        continue;
      }
      names.add(category);
      final int separator = category.indexOf(SEPARATOR);
      if (separator >= 0) {
        names.add(category.substring(0, separator));
      }
    }
    return names;
  }
}
