// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "Shop",
    targets: [
        .target(name: "Core"),
        .target(name: "App", dependencies: ["Core"]),
    ]
)
