package dev.ward.domain.init;

/**
 * A member script that already exists with a different command than init would write. Init keeps it
 * unless the user approves the replacement.
 *
 * @param member the member's package name
 * @param script the script name, which is the turbo task it runs
 * @param current the command the script runs now
 * @param proposed the command init would write
 */
public record ScriptReplacement(String member, String script, String current, String proposed) {}
