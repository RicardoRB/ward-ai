package dev.ward.bench;

/**
 * One label of a corpus: whether a file is vulnerable to a rule. A file with no label for a rule is
 * not counted as vulnerable, and a finding on it is a false positive.
 *
 * @param file the file, relative to the corpus root, with forward slashes
 * @param rule the rule id the label is about
 * @param vulnerable true when the file contains the weakness the rule reports
 */
record AccuracyLabel(String file, String rule, boolean vulnerable) {}
