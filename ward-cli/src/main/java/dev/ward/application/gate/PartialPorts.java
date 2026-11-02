package dev.ward.application.gate;

import dev.ward.domain.config.PathExistence;
import dev.ward.domain.project.WorkspaceMembers;

/**
 * The ports a partial check uses to read the edited paths.
 *
 * @param existence decides which edited paths still exist
 * @param members names the workspace package that holds each edited path
 */
public record PartialPorts(PathExistence existence, WorkspaceMembers members) {}
