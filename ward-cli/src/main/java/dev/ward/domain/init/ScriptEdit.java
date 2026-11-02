package dev.ward.domain.init;

import java.nio.file.Path;

/**
 * A build script another module edited, with its content before the edit, so rollback can restore
 * it.
 *
 * @param file the module build script
 * @param originalContent the script content before the change
 */
public record ScriptEdit(Path file, String originalContent) {}
