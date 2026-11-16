let g = { x in x + 1 }
func run() async {
    await withContinuation { continuation in
        onResponse {
            continuation.resume(returning: $0)
        }
    }
    stream.onResponse { response in
        initialResponse = response
    }
    items.forEach { _ in print("item") }
}
let isOffline: (Failure) -> Bool = { error in
    if let error = error.underlying {
        return error.isOffline
    }
    return false
}
let adapter = Adapter { request, _ in
    var request = request
    request.retries += 1
    return request
}
