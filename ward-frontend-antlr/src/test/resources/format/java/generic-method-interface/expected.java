interface Codec {
    /** Reads a value. */
    <T> T fromJson(String json, Class<T> type);

    // Plain comment.
    <T> T other(Class<T> type);
}
