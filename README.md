# Etendo Backup Gradle Plugin

This Gradle plugin allows you to generate and restore backups of Etendo Core easily and efficiently. 💾🔒

## Installation

To use this plugin, add it as a dependency in your `build.gradle` file using the Gradle plugin installation method:

```
plugins {
    id 'com.etendoerp.etendobackup' version "1.0.0"
}
```

## Usage
For detailed information on how to use this plugin and its functionalities, refer to the documentation in the [Etendo Wiki](https://docs.etendo.software/developer-guide/etendo-classic/developer-tools/etendo-backup-restore-tool/).

## Publishing a New Version of the Plugin
To publish a new version of the plugin, follow these steps:

1. Clone the repository.

2. Open the Hotfix or Release branch using Git Flow, as appropriate:

    ```
    git flow <hotfix|release> start <version>
    ```
   
3. Execute the `upgradePluginVersion` task to increment the version:

    ```
    ./gradlew upgradePluginVersion -Ptype=[major|minor|patch]
    ```
   
   The type flag should have the value of the version type you want to publish. Typically, use `patch` for hotfixes and `minor` or `major` for releases.

4. Add the version change in the build.gradle, commit, and close the branch with git flow:

    ```
    git add build.gradle
    git commit -m "Update version to <version> :zap:"
    git flow <hotfix|release> finish <version>
    git push --all
    git push --tags
    ```

5. Finally, on the main branch, execute the publish task:

    ```
    ./gradlew publish
    ```

**Warning** 💡 If you run the publishing task from a branch other than `main`, it will publish a `SNAPSHOT` version to the Nexus repository.
