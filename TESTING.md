# Testing AutoAmethyst 2.0

**Build target:** Minecraft 1.21.4 · Java 21 · compatible RusherHack installation.

## Automated checks

```bash
./gradlew updateAccessWidener
./gradlew test build
```

The Gradle tests validate harvest stage selection, reach caps, successful verification handoff and bounded retry timing. They do **not** simulate all Minecraft networking or server-side rejection behaviors.

## Manual regression checklist

- [ ] Mature cluster is detected and highlighted.
- [ ] A fully-grown cluster breaks and drops expected vanilla items.
- [ ] Budding Amethyst and ordinary Amethyst Blocks remain intact.
- [ ] The next cluster is selected after a confirmed break without a fixed 16-tick delay.
- [ ] Rejected or unconfirmed breaks retry without packet spam.
- [ ] Immature buds remain when **Mature Clusters** is selected.
- [ ] **All Growth** selects immature buds only when explicitly enabled.
- [ ] Line-of-sight checks stop mining behind solid obstructions.
- [ ] Normal reach limits are respected in Survival; Creative uses its corresponding cap.
- [ ] Manual attack input, held item use, open menus and spectator mode suspend automation.
- [ ] Toggling **Show Targets** doesn't affect harvesting.
- [ ] Disconnecting, changing dimensions and switching tools do not leave a stuck mining operation.

## Results already reported

**v2.0.0-beta.1:** six hands-on scenarios passed: mature cluster discovery, complete break, normal drops, Budding Amethyst protection, progression to the next target, and retries after failed breaks.

**v2.0.0-beta.2:** user confirmed the faster harvesting behavior worked correctly after installing the JAR in the Minecraft 1.21.4 Prism Launcher instance. The build also completed with `./gradlew clean test build`.

These observations support the working release candidate; they are not a substitute for checks across different servers, latency conditions or future Minecraft versions.
