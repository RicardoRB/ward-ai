struct Panel<Content: View>: View {
    @ViewBuilder var content: () -> Content

    init(isOpen: Bool, @ViewBuilder content: @escaping () -> Content) {
        self.content = content
    }

    func refresh() {
        Task { @MainActor in
            await reload()
        }
        Task(priority: .background) { [weak self] in
            guard let self else { return }
            let lines = await self.load { @Sendable _, name in name }
            print(lines)
        }
    }

    func status(of state: State) -> String {
        switch state {
        case .ready: return "ready"
        @unknown default: return "unknown"
        }
    }
}
