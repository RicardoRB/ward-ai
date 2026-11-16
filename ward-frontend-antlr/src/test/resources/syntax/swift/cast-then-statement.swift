func configure(_ window: Window) {
    let root = window.rootController as! SplitController
    root.topController!.item.leftButton = window.displayButton
    window.delegate = self
}
