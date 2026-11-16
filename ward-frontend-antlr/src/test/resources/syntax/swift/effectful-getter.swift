struct Remote {
    var value: Int {
        get async throws {
            try await load()
        }
    }
}
