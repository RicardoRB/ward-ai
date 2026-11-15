package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Builds the project that Android Lint reads for one fixture file: a manifest as {@code
 * AndroidManifest.xml} with a target SDK (Lint's backup and export checks depend on it), a network
 * security configuration under {@code res/xml} with a manifest that names it, a menu under {@code
 * res/menu}, any other file as a layout, and the file of an {@code unused-resources} fixture at its
 * {@code filename.txt} path beside its {@code project} folder.
 */
final class AndroidLintProject {

  static final String UNUSED_RESOURCES = "unused-resources";
  private static final String MANIFEST = "AndroidManifest.xml";
  private static final String NAMESPACE =
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"";
  private static final String USES_SDK =
      "<uses-sdk android:minSdkVersion=\"24\" android:targetSdkVersion=\"34\"/>";
  private static final Pattern ROOT = Pattern.compile("<([A-Za-z][\\w.:-]*)");
  private static final Pattern MANIFEST_TAG = Pattern.compile("(<manifest[^>]*>)");

  private AndroidLintProject() {}

  /**
   * Puts the fixture into {@code project}.
   *
   * @return the project-relative path of the fixture file
   */
  static String place(
      final String rule, final Path directory, final Path fixture, final Path project)
      throws IOException {
    final String text = Files.readString(fixture, StandardCharsets.UTF_8);
    if (UNUSED_RESOURCES.equals(rule)) {
      copyTree(directory.resolve("project"), project);
      final String path = Files.readString(directory.resolve("filename.txt")).strip();
      write(project.resolve(path), text);
      return path;
    }
    final Matcher root = ROOT.matcher(text.replaceAll("(?s)<\\?.*?\\?>|<!--.*?-->", ""));
    final String target = target(root.find() ? root.group(1) : "");
    if (MANIFEST.equals(target)) {
      write(project.resolve(MANIFEST), withSdk(text));
    } else {
      write(project.resolve(target), text);
      write(project.resolve(MANIFEST), syntheticManifest(target));
    }
    return target;
  }

  private static String syntheticManifest(final String target) {
    final String config =
        target.startsWith("res/xml/") ? " android:networkSecurityConfig=\"@xml/f\"" : "";
    return "<manifest "
        + NAMESPACE
        + ">"
        + USES_SDK
        + "<application android:allowBackup=\"false\""
        + config
        + "/></manifest>";
  }

  private static String target(final String rootElement) {
    return switch (rootElement) {
      case "manifest" -> MANIFEST;
      case "network-security-config" -> "res/xml/f.xml";
      case "menu" -> "res/menu/f.xml";
      default -> "res/layout/f.xml";
    };
  }

  private static String withSdk(final String manifest) {
    return manifest.contains("<uses-sdk")
        ? manifest
        : MANIFEST_TAG.matcher(manifest).replaceFirst("$1" + USES_SDK);
  }

  private static void write(final Path file, final String text) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, text, StandardCharsets.UTF_8);
  }

  private static void copyTree(final Path from, final Path to) throws IOException {
    try (Stream<Path> files = Files.walk(from)) {
      for (final Path source : files.filter(Files::isRegularFile).toList()) {
        write(to.resolve(from.relativize(source).toString()), Files.readString(source));
      }
    }
  }
}
