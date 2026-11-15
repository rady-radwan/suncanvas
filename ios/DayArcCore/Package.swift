// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "DayArcCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "DayArcCore", targets: ["DayArcCore"]),
    ],
    targets: [
        .target(
            name: "DayArcCore",
            resources: [
                .copy("Resources/cities.tsv"),
                .copy("Resources/countries.tsv"),
                .copy("Resources/Fonts"),
            ]
        ),
        .testTarget(
            name: "DayArcCoreTests",
            dependencies: ["DayArcCore"],
            resources: [.copy("Resources/reference_skyfield.csv")]
        ),
    ]
)
