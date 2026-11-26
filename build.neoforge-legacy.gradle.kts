plugins {
    id("net.neoforged.moddev")
}

val mc = stonecutter.current.version
val modId = property("mod.id") as String
val mcNext = mc.split(".").let { "${it[0]}.${it[1].toInt() + 1}" }

version = "${property("mod.version")}+$mc-neoforge"
group = property("mod.group") as String
base.archivesName = modId

neoForge {
    version = property("deps.neoforge") as String

    runs {
        register("client") {
            client()
            gameDirectory = file("run/")
            programArguments.addAll("--username", "Aeronaut")
        }
        register("server") {
            server()
            gameDirectory = file("run-server/")
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets["main"])
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

// 1.21.1 is a different Minecraft API from 26.x (entity render states, ValueInput/ValueOutput, Identifier, the item
// and recipe formats), so the files that touch it live a second time in src/legacy. Everything else is shared with
// the 26.x build. The legacy copy of a file replaces the shared one of the same path; selftest/ is not built here.
val legacyJava = layout.buildDirectory.dir("legacy-src/java")
val legacyOnly = rootProject.file("src/legacy/java")
val prepareLegacySources = tasks.register<Sync>("prepareLegacySources") {
    dependsOn("stonecutterGenerate")
    val replaced = fileTree(legacyOnly).files.map { legacyOnly.toPath().relativize(it.toPath()).toString() }
    from(layout.buildDirectory.dir("generated/stonecutter/main/java")) {
        exclude(replaced)
        exclude("io/github/profetgit/hotairballoons/selftest/**")
        exclude("io/github/profetgit/hotairballoons/mixin/HumanoidMobRendererMixin.java", "io/github/profetgit/hotairballoons/mixin/MinecraftMixin.java")
    }
    from(legacyOnly)
    into(legacyJava)
}
tasks.compileJava {
    dependsOn(prepareLegacySources)
    setSource(legacyJava)
}

// Resources the same way: the legacy copy of a file replaces the shared one (pack.mcmeta, the recipe and advancement
// formats and the mixin list differ in 1.21.1).
val legacyRes = layout.buildDirectory.dir("legacy-src/resources")
val legacyResOnly = rootProject.file("src/legacy/resources")
val prepareLegacyResources = tasks.register<Sync>("prepareLegacyResources") {
    dependsOn("stonecutterGenerate")
    val replaced = fileTree(legacyResOnly).files.map { legacyResOnly.toPath().relativize(it.toPath()).toString() }
    from(layout.buildDirectory.dir("generated/stonecutter/main/resources")) { exclude(replaced) }
    from(legacyResOnly)
    into(legacyRes)
}
sourceSets.main {
    resources.setSrcDirs(listOf(legacyRes))
}

tasks.processResources {
    dependsOn(prepareLegacyResources)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("fabric.mod.json", "META-INF/mods.toml")
    val props = mapOf(
        "version" to project.version.toString(),
        "mc" to mc,
        "mc_next" to mcNext,
        "name" to project.property("mod.name"),
        "description" to project.property("mod.description"),
        "author" to project.property("mod.author"),
        "homepage" to project.property("mod.homepage"),
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE"))
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jspecify:jspecify:1.0.0")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
