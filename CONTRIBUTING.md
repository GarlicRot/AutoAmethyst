# Contributing

Thanks for helping improve AutoAmethyst.

1. Search [existing issues](https://github.com/GarlicRot/AutoAmethyst/issues) before opening a duplicate.
2. Open a focused issue describing the Minecraft version, RusherHack version, current behavior and expected behavior.
3. Make code changes on your own branch. Keep the module small: only expose a new setting when it makes a meaningful difference to ordinary players.
4. Use **Java 21** and run `./gradlew updateAccessWidener` followed by `./gradlew test build`.
5. Include a short manual test description when changing mining packets, targeting, server confirmation or rendering.
6. Open a pull request rather than pushing straight to `master`.

**Compatibility:** Minecraft 1.21.4 is the verified compile/in-game target for v2.0.0. Do not expand `Minecraft-Versions` in plugin metadata without building and testing the additional version.

**License note:** the repository currently contains an **All rights reserved** notice, not a permissive open-source license. Discuss reuse or redistribution permissions with the repository owner before incorporating third-party code.
