# Smooth Quest Gacha

FTB Quests gacha rewards, pools, rarity, networking, and animated client screen.

Minecraft **1.20.1 / Fabric**. Maintained by **Marj4n** as part of the Smooth Odyssey mod ecosystem.

## Source layout

Marj4n code follows `org.marj4n.smooth_quest_gacha`. See [Code structure](docs/CODE_STRUCTURE.md) for source folders, exceptions and validation rules.

## Development

- Import the repository as a Gradle project in IntelliJ IDEA.
- Run `python scripts/check_structure.py` for a dependency-free source/metadata check.
- Build using the included `gradlew.bat` / `gradlew` where available and the required Minecraft/Fabric dependencies.
- A passing structure check **does not** prove the mod compiles, launches, or works in Smooth Odyssey.

**Important:** The published source snapshot is missing GachaConfigManager and local FTB JAR dependencies; a layout migration alone does not make this repository buildable.
