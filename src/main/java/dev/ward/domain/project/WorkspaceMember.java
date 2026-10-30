package dev.ward.domain.project;

import java.nio.file.Path;

/**
 * A package that a workspace root lists as a member.
 *
 * @param name the package name, or its path below the workspace root when it has none
 * @param directory the member directory
 * @param project the member inspected as a project of its own
 */
public record WorkspaceMember(String name, Path directory, Project project) {}
