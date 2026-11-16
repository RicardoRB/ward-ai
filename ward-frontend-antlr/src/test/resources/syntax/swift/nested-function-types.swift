protocol Retrier {
    func retry(_ request: Request,
               dueTo error: any Error,
               _ completion: @escaping @Sendable (RetryResult) -> Void) -> Void
}

// MARK: - Next

struct Next {}
