package dev.ward.domain.config;

import java.nio.file.Path;

/**
 * An edited path inside the project root.
 *
 * @param relative the path relative to the project root, with {@code /} separators
 * @param name the file name
 * @param exists whether the file still exists
 * @param absolute the canonical absolute path
 */
record EditedPath(String relative, String name, boolean exists, Path absolute) {}
