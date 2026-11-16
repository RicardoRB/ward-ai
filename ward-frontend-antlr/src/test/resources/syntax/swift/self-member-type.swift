protocol Serializer {
    associatedtype Output
    func serialize(data: Data?, error: (any Error)?) throws -> Self.Output
}
