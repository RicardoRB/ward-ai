// File header comment.
package demo;

/**
 * Describes the service.
 */
class Service {
    // own line before a field
    private int count = 0; // trailing on a field
    /* block comment */
    void run() {
        step(); // trailing on a call
        /*
         * multi-line block comment
         */
        finish();
    }
}
