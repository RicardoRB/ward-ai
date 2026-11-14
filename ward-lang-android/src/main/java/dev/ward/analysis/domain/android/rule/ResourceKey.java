package dev.ward.analysis.domain.android.rule;

/**
 * An Android resource by its type and its name, written the way the generated {@code R} class
 * writes it: a dot of an XML name is an underscore, so {@code Theme.App} and {@code Theme_App} are
 * one resource.
 *
 * @param type the resource type, such as {@code string} or {@code layout}
 * @param name the resource name with dots replaced by underscores
 */
record ResourceKey(String type, String name) {

  /** Creates the key of a resource named as in XML or in the {@code R} class. */
  static ResourceKey of(final String type, final String name) {
    return new ResourceKey(type, name.replace('.', '_'));
  }
}
