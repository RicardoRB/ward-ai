package dev.ward.infrastructure.finding;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits output from {@code turbo run} into blocks by package prefix.
 *
 * <p>When turbo output carries prefixes of the form {@code <package>:<task>: }, each prefixed line
 * is placed in a block for that package with the prefix removed. Lines without a prefix, such as
 * package-manager banners, are dropped when prefixed lines exist. When no prefixed line exists, the
 * whole output is one block with an empty package name.
 */
public final class TurboOutput {
  // A package or task name has no spaces or parentheses, so a tsc diagnostic is not a prefix.
  private static final int PACKAGE_GROUP = 1;
  private static final int TEXT_GROUP = 3;
  private static final Pattern PREFIX =
      Pattern.compile("^([A-Za-z0-9@/._-]+):([A-Za-z0-9@/._-]+): (.*)$");

  private TurboOutput() {}

  /**
   * One output block associated with a workspace member package name.
   *
   * @param packageKey the package name, or empty when unprefixed
   * @param text the accumulated text output for this block
   */
  public record Block(String packageKey, String text) {}

  /**
   * Parses output into per-package blocks.
   *
   * @param output the full command output
   * @return a list of output blocks
   */
  public static List<Block> parse(final String output) {
    if (output == null || output.isEmpty()) {
      return List.of(new Block("", ""));
    }
    final Map<String, StringBuilder> blocks = new LinkedHashMap<>();
    for (final String line : output.split("\\r?\\n")) {
      prefix(line).ifPresent(block -> append(blocks, block.packageKey(), block.text()));
    }
    if (blocks.isEmpty()) {
      return List.of(new Block("", output));
    }
    return blocks.entrySet().stream()
        .map(entry -> new Block(entry.getKey(), entry.getValue().toString()))
        .toList();
  }

  private static Optional<Block> prefix(final String line) {
    final Matcher matcher = PREFIX.matcher(line);
    return matcher.matches()
        ? Optional.of(new Block(matcher.group(PACKAGE_GROUP), matcher.group(TEXT_GROUP)))
        : Optional.empty();
  }

  private static void append(
      final Map<String, StringBuilder> blocks, final String packageKey, final String text) {
    blocks.computeIfAbsent(packageKey, key -> new StringBuilder()).append(text).append('\n');
  }
}
