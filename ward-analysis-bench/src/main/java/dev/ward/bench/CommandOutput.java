package dev.ward.bench;

/** The exit code and standard output of one command run, whatever its exit code is. */
record CommandOutput(int exitCode, String stdout) {}
