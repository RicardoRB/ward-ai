enum Action { case tap(Int) }
func run(_ action: Action) {
    switch action {
    case .tap:
        print("tap")
    default:
        break
    }
}
