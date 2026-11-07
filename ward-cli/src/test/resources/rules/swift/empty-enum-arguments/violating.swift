enum Action { case tap(Int) }
func run(_ action: Action) {
    switch action {
    case .tap(_):
        print("tap")
    default:
        break
    }
}
