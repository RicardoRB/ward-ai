package dev.ward.infrastructure.init;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Language;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** The body of each preset rule file, in the format its tool loads. */
final class PresetRuleFileContent {
  private static final String CHECKSTYLE_DOCTYPE =
      "<!DOCTYPE module PUBLIC \"-//Checkstyle//DTD Checkstyle Configuration 1.3//EN\""
          + " \"https://checkstyle.org/dtds/configuration_1_3.dtd\">\n";

  /** Checkstyle checks that run on whole files, so they must be children of {@code Checker}. */
  private static final Set<String> CHECKER_MODULES =
      Set.of(
          "FileLength",
          "LineLength",
          "NewlineAtEndOfFile",
          "FileTabCharacter",
          "RegexpSingleline",
          "RegexpMultiline",
          "Translation",
          "UniqueProperties");

  /** The options the strict preset has always set on Checkstyle's {@code HiddenField}. */
  private static final String HIDDEN_FIELD =
      "\n[rules.\"ward:java/hidden-field\"]\nignore-constructor-parameter = true\n"
          + "ignore-setter = true\n";

  private PresetRuleFileContent() {}

  /**
   * The rule selection of the {@code ward} gate: the Ward rules of the preset, for the JVM source
   * languages the project has, Java before Kotlin, then Python when the project has it, then Swift.
   * A project without a JVM language selects Java, as it always has.
   */
  static String wardRules(final QualityPreset preset, final Set<Language> languages) {
    final QualityPreset chosen = preset == null ? QualityPreset.STANDARD : preset;
    final List<String> ids = jvmLanguageIds(languages);
    if (languages.contains(Language.PYTHON)) {
      ids.add("python");
    }
    ids.add("swift");
    return "version = 1\n\n[select]\npreset = \""
        + chosen.id()
        + "\"\nlanguages = "
        + ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(", ", "[", "]"))
        + "\n"
        + (chosen == QualityPreset.STRICT && ids.contains("java") ? HIDDEN_FIELD : "");
  }

  private static List<String> jvmLanguageIds(final Set<Language> languages) {
    final List<String> ids = new ArrayList<>();
    if (languages.contains(Language.JAVA)) {
      ids.add("java");
    }
    if (languages.contains(Language.KOTLIN)) {
      ids.add("kotlin");
    }
    if (ids.isEmpty()) {
      ids.add("java");
    }
    return ids;
  }

  static String pmd(final QualityPreset preset) {
    return "<ruleset name=\"Ward "
        + preset.id()
        + " PMD\" xmlns=\"http://pmd.sourceforge.net/ruleset/2.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
        + "  <description>Ward "
        + preset.id()
        + " preset rules.</description>\n"
        + preset.rules().pmdRules().stream()
            .map(rule -> "  <rule ref=\"" + rule + "\"/>\n")
            .reduce("", String::concat)
        + "</ruleset>\n";
  }

  /**
   * The DOCTYPE follows the ownership marker, which stays the first line; Checkstyle accepts
   * comments before it. File-level checks sit under {@code Checker}, the rest under {@code
   * TreeWalker}, each with its preset properties.
   */
  static String checkstyle(final QualityPreset preset) {
    final List<String> modules = preset.rules().checkstyleModules();
    final Map<String, String> properties = preset.rules().checkstyleProperties();
    return CHECKSTYLE_DOCTYPE
        + "<module name=\"Checker\">\n"
        + modules.stream()
            .filter(CHECKER_MODULES::contains)
            .map(module -> checkstyleModule(module, properties, "  "))
            .reduce("", String::concat)
        + "  <module name=\"TreeWalker\">\n"
        + modules.stream()
            .filter(module -> !CHECKER_MODULES.contains(module))
            .map(module -> checkstyleModule(module, properties, "    "))
            .reduce("", String::concat)
        + "  </module>\n</module>\n";
  }

  private static String checkstyleModule(
      final String module, final Map<String, String> properties, final String indent) {
    final String prefix = module + ".";
    final Map<String, String> own = new TreeMap<>();
    properties.forEach(
        (key, value) -> {
          if (key.startsWith(prefix)) {
            own.put(key.substring(prefix.length()), value);
          }
        });
    if (own.isEmpty()) {
      return indent + "<module name=\"" + module + "\"/>\n";
    }
    return indent
        + "<module name=\""
        + module
        + "\">\n"
        + own.entrySet().stream()
            .map(
                property ->
                    indent
                        + "  <property name=\""
                        + property.getKey()
                        + "\" value=\""
                        + property.getValue()
                        + "\"/>\n")
            .reduce("", String::concat)
        + indent
        + "</module>\n";
  }

  static String spotbugs(final QualityPreset preset) {
    return "<FindBugsFilter>\n"
        + preset.rules().spotbugsExcludes().stream()
            .map(bug -> "  <Match><Bug pattern=\"" + bug + "\"/></Match>\n")
            .reduce("", String::concat)
        + "</FindBugsFilter>\n";
  }

  /** {@code ExternalRules} reads the rules from an {@code <enforcer><rules>} document. */
  static String enforcer(final QualityPreset preset) {
    return "<enforcer>\n<rules>\n"
        + preset.rules().enforcerRules().stream()
            .map(
                rule ->
                    "requireMavenVersion".equals(rule)
                        ? "  <requireMavenVersion><version>[3.9,)</version></requireMavenVersion>\n"
                        : "  <" + rule + "/>\n")
            .reduce("", String::concat)
        + "</rules>\n</enforcer>\n";
  }
}
