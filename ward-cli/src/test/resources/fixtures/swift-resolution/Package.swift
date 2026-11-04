// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "Resolution",
    targets: [
        .target(name: "Domain"),
        .target(name: "Shop", dependencies: ["Domain"], path: "Modules/Shop"),
        .testTarget(name: "ShopTests", dependencies: ["Shop"]),
    ]
)
