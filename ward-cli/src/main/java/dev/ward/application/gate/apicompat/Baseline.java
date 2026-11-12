package dev.ward.application.gate.apicompat;

/**
 * The revision the working tree is compared with.
 *
 * @param ref the tag or ref that names it, as shown to the user
 * @param commit the commit ID the ref names
 */
record Baseline(String ref, String commit) {}
