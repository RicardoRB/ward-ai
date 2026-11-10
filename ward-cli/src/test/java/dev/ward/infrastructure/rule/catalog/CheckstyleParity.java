package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Element;

/**
 * Runs Checkstyle once over every fixture with each counterpart module at its defaults. A module
 * that cannot be configured without properties is reported as not comparable.
 */
final class CheckstyleParity implements ParityTool {

  private static final Set<String> CHECKER_MODULES =
      Set.of(
          "FileLength",
          "FileTabCharacter",
          "LineLength",
          "NewlineAtEndOfFile",
          "OrderedProperties",
          "RegexpHeader",
          "RegexpMultiline",
          "RegexpOnFilename",
          "RegexpSingleline",
          "UniqueProperties",
          "JavadocPackage",
          "Translation",
          "Header",
          "MultiFileRegexpHeader");
  private static final Pattern REJECTED =
      Pattern.compile("(?:Unable to instantiate '|cannot initialize module )(?:[\\w.]*\\.)?(\\w+)");
  private static final String SUFFIX = "Check";

  @Override
  public String name() {
    return "checkstyle";
  }

  @Override
  public boolean reportsColumns() {
    return true;
  }

  @Override
  public ParityRun run(final ParityEnvironment environment, final List<ParityTarget> targets)
      throws IOException, InterruptedException {
    final Set<String> modules = new TreeSet<>();
    targets.forEach(target -> modules.addAll(target.idsOf(name())));
    final ParityRun run = new ParityRun();
    final Path folder = environment.workFolder("checkstyle");
    final Path report = folder.resolve("report.xml");
    final Path errors = folder.resolve("errors.txt");
    for (int attempt = 0; attempt <= modules.size(); attempt++) {
      Files.writeString(folder.resolve("config.xml"), config(modules), StandardCharsets.UTF_8);
      ParityEnvironment.run(
          List.of(
              ParityEnvironment.java(),
              "-cp",
              environment.classpath("checkstyle"),
              "com.puppycrawl.tools.checkstyle.Main",
              "-c",
              folder.resolve("config.xml").toString(),
              "-f",
              "xml",
              "-o",
              report.toString(),
              environment.fixtures().toString()),
          folder.resolve("stdout.txt"),
          errors);
      final Matcher rejected = REJECTED.matcher(Files.readString(errors, StandardCharsets.UTF_8));
      if (!rejected.find()) {
        break;
      }
      modules.remove(rejected.group(1));
      run.idNotComparable(
          rejected.group(1), "Checkstyle cannot run this module without properties");
    }
    read(environment, report, run);
    return run;
  }

  private static void read(
      final ParityEnvironment environment, final Path report, final ParityRun run)
      throws IOException {
    if (!Files.exists(report)) {
      return;
    }
    for (final Element file :
        ParityXml.elements(ParityXml.parse(report).getDocumentElement(), "file")) {
      final String fixture = environment.fixtureOf(Path.of(file.getAttribute("name"))).orElse("");
      for (final Element error : ParityXml.elements(file, "error")) {
        final String source = error.getAttribute("source");
        String module = source.substring(source.lastIndexOf('.') + 1);
        if (module.endsWith(SUFFIX)) {
          module = module.substring(0, module.length() - SUFFIX.length());
        }
        run.add(
            fixture, module, ParityXml.number(error, "line"), ParityXml.number(error, "column"));
      }
    }
  }

  private static String config(final Set<String> modules) {
    final List<String> checker = new ArrayList<>();
    final List<String> walker = new ArrayList<>();
    for (final String module : modules) {
      (CHECKER_MODULES.contains(module) ? checker : walker)
          .add("<module name=\"" + module + "\"/>");
    }
    return "<?xml version=\"1.0\"?><!DOCTYPE module PUBLIC \"-//Checkstyle//DTD Checkstyle"
        + " Configuration 1.3//EN\""
        + " \"https://checkstyle.org/dtds/configuration_1_3.dtd\"><module name=\"Checker\">"
        + "<property name=\"charset\" value=\"UTF-8\"/>"
        + "<property name=\"fileExtensions\" value=\"java\"/>"
        + String.join("", checker)
        + "<module name=\"TreeWalker\">"
        + String.join("", walker)
        + "</module></module>";
  }
}
