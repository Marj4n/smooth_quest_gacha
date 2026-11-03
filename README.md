# Smooth Quest Gacha

Adds a configurable gacha reward type to FTB Quests for the Smooth Odyssey modpack. Quest authors can grant rolls from configured reward pools.

## Build

Use JDK 21 with the included Gradle wrapper. The mod targets Java 17.

```sh
./gradlew build
```

The regular JAR is in `build/libs/`. Runtime: Minecraft 1.20.1, Fabric API and FTB Quests with its dependencies. Keep the compile dependencies in `libs/` available when building.

## Source layout

`src/main/java` contains reward configuration and FTB Quests integration; `src/client/java` contains client code. Game metadata and assets live in the corresponding resource folders. Existing `org.Marj4n` package names are retained for compatibility.

## License

By Marj4n. See [LICENSE.txt](LICENSE.txt).

## Reuse permissions

Reusing or modifying Marj4n-owned source code or assets requires prior written permission. Contact Marj4n through [nivekts.vercel.app](https://nivekts.vercel.app/) with the intended use. See [LICENSE.txt](LICENSE.txt) for gameplay permissions and exceptions. Third-party licenses, existing grants and GitHub platform rights remain applicable.
