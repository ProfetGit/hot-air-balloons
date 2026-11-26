plugins {
    id("net.fabricmc.fabric-loom-remap")
}

val mc = stonecutter.current.version
val javaVer = if (mc.startsWith("1.20")) 17 else 21
val modId = property("mod.id") as String

version = "${property("mod.version")}+$mc-fabric"
group = property("mod.group") as String
base.archivesName = modId

repositories {
    maven("https://maven.terraformersmc.com/") {
        name = "TerraformersMC"
        content { includeGroup("com.terraformersmc") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    mappings(loom.officialMojangMappings())
    annotationProcessor("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    modCompileOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}") { isTransitive = false }

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// before 1.21.2 VehicleEntity.getDropItem is package-private (NeoForge widens it itself; Forge uses the AT in dev/);
// 1.20.1 has no VehicleEntity (the mod's stand-in)
val widener = rootProject.file("dev/vehicle.accesswidener").takeIf { stonecutter.eval(mc, ">=1.20.5") && stonecutter.eval(mc, "<1.21.2") }

loom {
    mixin.useLegacyMixinAp = true
    widener?.let { accessWidenerPath = it }
    runs.named("server") {
        server()
        runDir = "run-server"
    }
    runs.named("client") {
        client()
        runDir = "run"
        programArgs("--username", "Aeronaut")
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(javaVer)
    targetCompatibility = JavaVersion.toVersion(javaVer)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVer
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "mc" to mc,
        "name" to project.property("mod.name"),
        "description" to project.property("mod.description"),
        "author" to project.property("mod.author"),
        "homepage" to project.property("mod.homepage"),
        "fabric_loader" to project.property("deps.fabric_loader"),
        "mc_range" to ((findProperty("deps.mc_range") as String?) ?: "~$mc"),
        "java" to javaVer.toString(),
    )
    inputs.properties(props)
    exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
    filesMatching("fabric.mod.json") { expand(props) }
    widener?.let { file ->
        from(file) { rename { "hotairballoons.accesswidener" } }
        filesMatching("fabric.mod.json") {
            filter { line -> if (line.trim() == "\"mixins\": [") "  \"accessWidener\": \"hotairballoons.accesswidener\",\n$line" else line }
        }
    }
    if (javaVer < 21) filesMatching("*.mixins.json") { filter { it.replace("JAVA_21", "JAVA_$javaVer") } }
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE"))
}

extra["mcVersion"] = mc
apply(from = rootProject.file("dev/data.gradle.kts"))
apply(from = rootProject.file("../../tools/Backport/renames.gradle.kts"))
