package dev.ward.application.gate.apicompat;

import dev.ward.domain.gate.MainSourceRoots;
import dev.ward.domain.gate.ModuleDirectories;
import dev.ward.domain.gate.RevisionFiles;
import dev.ward.domain.gate.WorkingFiles;

/**
 * What the {@code api-compat} gate needs from its adapters.
 *
 * @param revisions reads Git objects
 * @param working reads the working tree
 * @param layout finds the main source roots in the text of a build file
 * @param modules finds the module directories a build file lists
 * @param surfaces reads the API of source files
 */
record ApiCompatPorts(
    RevisionFiles revisions,
    WorkingFiles working,
    MainSourceRoots layout,
    ModuleDirectories modules,
    ApiSurfaces surfaces) {}
