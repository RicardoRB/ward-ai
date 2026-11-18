struct UserStore {
    let retryCount = 1
    enum Mode { case fastPath, slowPath }
    func _loadConfig() {}
    func `default`() {}
}

func match(_ code: Int) {
    switch code {
    case SDK_LIMIT: break
    default: break
    }
}

func == (lhs: UserStore, rhs: UserStore) -> Bool { true }
