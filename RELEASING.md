# Releasing AutoAmethyst

Releases are compiled by **GitHub Actions from committed source**. Do not upload a locally built JAR to the Release page.

## 1. Prepare the version

Update `plugin_version` in `gradle.properties`, the matching changelog entry and any version-specific README references. The version and tag must match exactly:

```text
plugin_version=2.0.0
Git tag=v2.0.0
Release JAR=AutoAmethyst-2.0.0.jar
```

Run on Java 21:

```bash
./gradlew updateAccessWidener
./gradlew test build
```

Then test the built plugin in Minecraft 1.21.4. See [TESTING.md](TESTING.md).

## 2. Push and review the rewrite branch

From the `rewrite/v2-reliable-harvesting` branch:

```bash
git status
git add -A
git diff --cached --check
git commit -m "Release AutoAmethyst 2.0.0 rewrite"
git push -u origin rewrite/v2-reliable-harvesting
```

Open a pull request targeting `master` and confirm **Build and Test** passes. Do not tag until the pull request is merged.

## 3. Create the release tag

After the merge, update local `master` and tag that exact commit:

```bash
git switch master
git pull --ff-only origin master
grep '^plugin_version=' gradle.properties
git tag -a v2.0.0 -m "AutoAmethyst 2.0.0"
git push origin v2.0.0
```

A tag push starts **Build and Release AutoAmethyst**. The workflow:

1. Validates that the tag matches the version in `gradle.properties`.
2. Validates the Gradle wrapper and sets up Java 21.
3. Builds and tests the tagged source.
4. Ensures the output JAR has the correct `rusherhack-plugin.json` version and Minecraft target.
5. Uploads that JAR and `SHA256SUMS.txt` as a GitHub Release, with generated release notes.

> [!WARNING]
> A failed workflow must be fixed before announcing the release. Don't manually attach a substitute JAR and don't reuse a released version number for changed code.

## 4. Verify the published files

Download **both** `AutoAmethyst-2.0.0.jar` and `SHA256SUMS.txt` from GitHub Releases into the same directory:

```bash
sha256sum -c SHA256SUMS.txt
```

The command should report `AutoAmethyst-2.0.0.jar: OK`. Also check that the GitHub Actions run for the tag completed successfully.

## Release versioning

For a patch, update the version/changelog, merge the change and push a new tag such as `v2.0.1`. For pre-releases, use tags such as `v2.1.0-beta.1`: the workflow automatically marks tags containing a hyphen as prereleases.
