// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "App",
    dependencies: [
        // .package(url: "https://github.com/apple/swift-collections.git", from: "1.0.0"),
        .package(url: "https://github.com/apple/swift-log.git", from: "1.6.0"),
        .package(path: "../Local"),
    ],
    targets: [.target(name: "App", dependencies: [.product(name: "Logging", package: "swift-log")])]
)
