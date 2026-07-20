// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "VentingModePackage",
    platforms: [
        .macOS(.v13),
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "VentingCore",
            targets: ["VentingCore"]
        )
    ],
    targets: [
        .target(
            name: "VentingCore",
            path: "Sources/VentingCore"
        ),
        .testTarget(
            name: "VentingCoreTests",
            dependencies: ["VentingCore"],
            path: "Tests/VentingCoreTests"
        )
    ]
)
