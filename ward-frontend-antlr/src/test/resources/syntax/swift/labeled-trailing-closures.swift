func run() {
    monitor.observe { path in
        print(path)
    } stop: {
        print("stopped")
    }
    let binding = Binding {
        value
    } set: { newValue in
        value = newValue
    }
    let interceptor = Interceptor(
        .adapter { @Sendable request, _, completion in
            completion(.success(request))
        } retrier: { @Sendable _, _, _, completion in
            completion(.retry)
        }
    )
}
