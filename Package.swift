// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "NomNomCore",
    platforms: [
        .iOS(.v17),
        .macOS(.v13)
    ],
    products: [
        .library(name: "NomNomCore", targets: ["NomNomCore"])
    ],
    targets: [
        .target(
            name: "NomNomCore",
            path: "Sources/NomNomCore"
        ),
        .testTarget(
            name: "NomNomCoreTests",
            dependencies: ["NomNomCore"],
            path: "Tests/NomNomCoreTests"
        )
    ]
)
