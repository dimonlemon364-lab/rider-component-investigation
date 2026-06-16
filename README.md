# Component Investigation — Show Relations for Component/Class (Rider)

A JetBrains **Rider** plugin. Right-click a C#/F#/VB type → **Show Relations for Component/Class**
to see, in the native Find Usages view, every place the class is used **and how** — usages of the
class itself (instantiation, type usage) and of each member (methods, properties, fields,
constants, events) — grouped by **member**, then by **access type** (read / write / invocation /
instantiation / type usage). An **Export to Markdown** button writes the full report (with a real
usage example per call site) to a linkable `.md` file; `path:line` links in that file open the
target file at the line in the editor.

## Architecture

Rider = IntelliJ **JVM frontend** (Kotlin) + ReSharper **.NET backend** (C#) talking over the
**rd protocol**. C#/F#/VB analysis (find usages, read/write classification) runs in the backend.

```
.
├── protocol/                     rd model (Kotlin) -> generates frontend Kotlin + backend C#
│   └── src/main/kotlin/model/ComponentInvestigationModel.kt
├── src/dotnet/                    ReSharper backend (C#)
│   ├── ComponentInvestigation.sln
│   └── ComponentInvestigation.Rider/
│       ├── ShowRelationsHost.cs   [SolutionComponent] wires the getRelations rd call
│       ├── RelationFinder.cs      resolve type, enumerate members, FindReferences, map entries
│       ├── AccessClassifier.cs    IReference -> Read/Write/Invocation/Instantiation/...
│       └── Model/Generated/       generated C# model (rdgen output)
├── src/main/kotlin/com/componentinvestigation/   frontend (Kotlin)
│   ├── ShowRelationsAction.kt          editor/project-view action; calls the backend
│   ├── RelationsUsagePresenter.kt      builds usages, shows native view, adds Export button
│   ├── RelationUsage.kt                usage carrying member + access metadata
│   ├── MemberUsageGroupingRule.kt      member -> access grouping (+ provider)
│   ├── RelationMarkdownExporter.kt     renders + writes the Markdown report
│   └── RelationMarkdownLinkOpener.kt   Markdown preview `path:line` link handler
├── src/main/resources/META-INF/
│   ├── plugin.xml
│   └── component-investigation-markdown.xml   optional markdown integration
├── build.gradle.kts              rider(), riderModel, compileDotNet, bundles backend DLLs
├── settings.gradle.kts           includes :protocol, rd-gen plugin
└── gradle.properties             platformVersion / dotnet / rdGen versions
```

## Build & run

```
./gradlew :protocol:rdgen   # generate the protocol (Kotlin + C#)
./gradlew compileDotNet      # build the ReSharper backend
./gradlew buildPlugin        # full plugin zip (frontend + backend)
./gradlew runIde             # launch Rider with the plugin
```

> [!IMPORTANT]
> Requires a JDK and the **.NET SDK** on PATH. Confirm `platformVersion`, `rdGenVersion`
> (gradle.properties) and `SdkVersion` (src/dotnet/Directory.Build.props) all match the same
> Rider build — see `plan.md` → *Risks* for the version-alignment caveats and the Markdown
> link-opener EP note.

## Plugin configuration file

The plugin configuration file is a [plugin.xml][file:plugin.xml] file located in the `src/main/resources/META-INF`
directory.
It provides general information about the plugin, its dependencies, extensions, and listeners.

You can read more about this file in the [Plugin Configuration File][docs:plugin.xml] section of our documentation.

If you're still not quite sure what this is all about, read [Introduction to IntelliJ Platform][docs:intro].

## Predefined Run/Debug configurations

Within the default project structure, there is a `.run` directory provided containing predefined *Run/Debug
configurations* that expose corresponding Gradle tasks:

| Configuration name | Description                                                                                                                                                                         |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Run Plugin         | Runs [`:runIde`][gh:intellij-platform-gradle-plugin-runIde] IntelliJ Platform Gradle Plugin task. Use the *Debug* icon for plugin debugging.                                        |
| Run Tests          | Runs [`:test`][gradle:lifecycle-tasks] Gradle task.                                                                                                                                 |
| Run Verifications  | Runs [`:verifyPlugin`][gh:intellij-platform-gradle-plugin-verifyPlugin] IntelliJ Platform Gradle Plugin task to check the plugin compatibility against the specified IntelliJ IDEs. |

> [!NOTE]
> You can find the logs from the running task in the `idea.log` tab.

## Publishing the plugin

> [!TIP]
> Make sure to follow all guidelines listed in [Publishing a Plugin][docs:publishing] to follow all recommended and
> required steps.

Releasing a plugin to [JetBrains Marketplace](https://plugins.jetbrains.com) is a straightforward operation that uses
the `publishPlugin` Gradle task provided by
the [intellij-platform-gradle-plugin][gh:intellij-platform-gradle-plugin-docs].

You can also upload the plugin to the [JetBrains Plugin Repository](https://plugins.jetbrains.com/plugin/upload)
manually via UI.

## Useful links

- [IntelliJ Platform SDK Plugin SDK][docs]
- [IntelliJ Platform Gradle Plugin Documentation][gh:intellij-platform-gradle-plugin-docs]
- [IntelliJ Platform Explorer][jb:ipe]
- [JetBrains Marketplace Quality Guidelines][jb:quality-guidelines]
- [IntelliJ Platform UI Guidelines][jb:ui-guidelines]
- [JetBrains Marketplace Paid Plugins][jb:paid-plugins]
- [IntelliJ SDK Code Samples][gh:code-samples]

[docs]: https://plugins.jetbrains.com/docs/intellij

[docs:intro]: https://plugins.jetbrains.com/docs/intellij/intellij-platform.html?from=IJPluginTemplate

[docs:plugin.xml]: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html?from=IJPluginTemplate

[docs:publishing]: https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate

[file:plugin.xml]: ./src/main/resources/META-INF/plugin.xml

[gh:code-samples]: https://github.com/JetBrains/intellij-sdk-code-samples

[gh:intellij-platform-gradle-plugin]: https://github.com/JetBrains/intellij-platform-gradle-plugin

[gh:intellij-platform-gradle-plugin-docs]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html

[gh:intellij-platform-gradle-plugin-runIde]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#runIde

[gh:intellij-platform-gradle-plugin-verifyPlugin]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#verifyPlugin

[gradle:lifecycle-tasks]: https://docs.gradle.org/current/userguide/java_plugin.html#lifecycle_tasks

[jb:github]: https://github.com/JetBrains/.github/blob/main/profile/README.md

[jb:forum]: https://platform.jetbrains.com/

[jb:quality-guidelines]: https://plugins.jetbrains.com/docs/marketplace/quality-guidelines.html

[jb:paid-plugins]: https://plugins.jetbrains.com/docs/marketplace/paid-plugins-marketplace.html

[jb:quality-guidelines]: https://plugins.jetbrains.com/docs/marketplace/quality-guidelines.html

[jb:ipe]: https://jb.gg/ipe

[jb:ui-guidelines]: https://jetbrains.github.io/ui
