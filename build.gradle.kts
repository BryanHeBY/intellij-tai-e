import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
    alias(libs.plugins.intellijGrammarKit) // Plugin for generating lexers and parsers for IntelliJ plugins
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

// Configure project's dependencies
repositories {
    mavenCentral()
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
}

val taieVersion = "0.5.1-SNAPSHOT"

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    implementation(libs.annotations)
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
    implementation("net.pascal-lab:tai-e:$taieVersion")
    "net.pascal-lab:tai-e:$taieVersion".let {
        compileOnly(it) { isTransitive = false }
        testCompileOnly(it) { isTransitive = false }
        runtimeOnly(it)
    }
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("org.slf4j:slf4j-nop:2.0.7")

    // Process YAML configuration files
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.0")

    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.3")
}

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(17)
}

// Configure Gradle IntelliJ Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    pluginName = properties("pluginName")
    version = properties("platformVersion")
    type = properties("platformType")

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins = properties("platformPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
}

grammarKit {
    jflexRelease.set("1.7.0-1")
    grammarKitRelease.set("2021.1.2")
    intellijRelease.set("203.7717.81")
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = properties("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
koverReport {
    defaults {
        xml {
            onCheck = true
        }
    }
}

// Config generated parser to source
sourceSets["main"].java.srcDirs("src/main/gen")

tasks.jar {
    configurations.runtimeClasspath.get().filter {
        it.name == "tai-e-0.5.1-SNAPSHOT.jar"
    }.singleFile.let {
//        from(zipTree(it))
        from(configurations.runtimeClasspath.get().filter { it.exists() }.map { if (it.isDirectory) it else zipTree(it) })

    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.prepareSandbox {
    exclude {
        it.name == "tai-e-0.5.1-SNAPSHOT.jar"
    }
}

// Clean old parser
tasks.register<Delete>("cleanTirParser") {
    delete("src/main/gen/pascal/taie/intellij/tir/syntax")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks {
    compileKotlin {
        dependsOn.add(generateParser)
    }

    generateLexer {
        dependsOn.add("cleanTirParser")
        sourceFile.set(File("src/main/syntax/tir.flex"))
        targetDir.set("src/main/gen/pascal/taie/intellij/tir/syntax/")
        targetClass.set("TirLexer")
    }

    generateParser {
        dependsOn.add(generateLexer)
        sourceFile.set(File("src/main/syntax/tir.bnf"))
        targetRoot.set("src/main/gen/")
        pathToParser.set("pascal/taie/intellij/tir/syntax/TirParser")
        pathToPsiRoot.set("pascal/taie/intellij/tir/syntax/psi")
    }

    // No searchable options found.
    // If plugin is not supposed to provide custom settings exposed in UI,
    // disable building searchable options to decrease the build time.
    // See: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-options
    buildSearchableOptions {
        enabled = false
    }

    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }

    patchPluginXml {
        version = properties("pluginVersion")
        sinceBuild = properties("pluginSinceBuild")
        untilBuild = properties("pluginUntilBuild")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with (it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = properties("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    runIde { maxHeapSize = "2g" }

    signPlugin {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token = environment("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = properties("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }
}
