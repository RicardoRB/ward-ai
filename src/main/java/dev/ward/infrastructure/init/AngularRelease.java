package dev.ward.infrastructure.init;

/** Verified compatibility data for one supported Angular major. */
record AngularRelease(String major, String angularEslint, String node, String typescript) {}
