package dev.ward.domain.buildpolicy;

/**
 * A build tool version that a project declares.
 *
 * @param tool the tool: {@code maven}, {@code gradle}, {@code npm}, {@code pnpm}, {@code yarn},
 *     {@code bun} or {@code uv}
 * @param version the declared dotted version
 * @param file the project-relative path of the file that declares it
 * @param line the 1-based line, or {@code 0} when unknown
 */
public record ToolVersion(String tool, String version, String file, int line) {}
