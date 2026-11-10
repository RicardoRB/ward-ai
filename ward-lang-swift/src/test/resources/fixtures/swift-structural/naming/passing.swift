struct UserStore {
    let retryCount = 1
    enum Mode { case fastPath, slowPath }
    func _loadConfig() {}
    func `default`() {}
}
