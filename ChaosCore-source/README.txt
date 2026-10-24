ChaosCore 1.0.0 — source project
Target: Purpur/Paper 1.21.11, Java 21

IMPORTANT:
This ZIP is source code, not an installable plugin JAR.
Build it with Gradle on a computer with Java 21 and internet access:
  gradle build
The output JAR will be in build/libs/ChaosCore-1.0.0.jar

Initial features:
- +1 chaos for a player kill
- 5-minute repeat-kill cooldown per killer/victim pair
- lifetime chaos thresholds: 5 => Strength I, 10 => Strength II
- /chaos and /chaos top
- player data saved in plugins/ChaosCore/players.yml

Notes:
- Economy selling is not implemented yet.
- This is an initial prototype; test on a backup/test server first.
