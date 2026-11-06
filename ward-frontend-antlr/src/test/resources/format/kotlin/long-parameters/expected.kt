class Repository(
        private val connectionPool: ConnectionPool,
        private val clock: Clock,
        private val retryPolicy: RetryPolicy,
        val name: String
)

fun createSession(
        userIdentifier: String,
        workspaceIdentifier: String,
        expiresAfterMinutes: Int,
        scopes: List<String>
): Session {
    return Session(userIdentifier)
}
