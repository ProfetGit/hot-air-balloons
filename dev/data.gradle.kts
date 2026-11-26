// Data that differs before 26.3, rewritten from the one source in src/main/resources (applied by every build script
// with extra["mcVersion"]).
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

val mcv = project.extra["mcVersion"] as String

fun before(version: String): Boolean {
    val a = mcv.split(".").map { it.toInt() }
    val b = version.split(".").map { it.toInt() }
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d < 0
    }
    return false
}

@Suppress("UNCHECKED_CAST")
fun obj(value: Any?) = value as MutableMap<String, Any?>

if (before("26.3")) tasks.named<ProcessResources>("processResources") {
    inputs.property("dataVersion", mcv)
    inputs.file(rootProject.file("dev/data.gradle.kts"))
    doLast {
        // before 1.21.2 a recipe ingredient is an object, not an id or #tag string
        if (before("1.21.2")) {
            val recipe = destinationDir.resolve("data/hotairballoons/recipe/hot_air_balloon.json")
            val root = obj(JsonSlurper().parse(recipe))
            root["key"] = obj(root["key"]).mapValues { (_, v) ->
                val id = v as String
                if (id.startsWith("#")) mapOf("tag" to id.substring(1)) else mapOf("item" to id)
            }
            recipe.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(root)))
        }

        val advancements = destinationDir.resolve("data/hotairballoons/advancement")

        val unlock = advancements.resolve("recipes/hot_air_balloon.json")
        unlock.writeText(unlock.readText().replace("\"recipes\": \"", "\"recipe\": \""))

        // the player condition is a list of loot conditions; before 26.2 entity predicates have plain keys
        val high = advancements.resolve("adventure/up_up_and_away.json")
        val root = obj(JsonSlurper().parse(high))
        val conditions = obj(obj(obj(root["criteria"])["high_in_a_balloon"])["conditions"])
        val player = obj(conditions["player"])
        player["condition"] = player.remove("type")
        if (before("26.2")) {
            val predicate = obj(player["predicate"])
            val vehicle = obj(predicate["minecraft:vehicle"])
            player["predicate"] = mapOf(
                "vehicle" to mapOf("type" to vehicle["minecraft:entity_type"]),
                "location" to predicate["minecraft:location"],
            )
        }
        conditions["player"] = listOf(player)
        high.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(root)))

        // before 1.20.5 items are {"item": ...}, a recipe's item list is a list; before 1.21 the data folders are plural
        if (before("1.20.5")) {
            val recipe = destinationDir.resolve("data/hotairballoons/recipe/hot_air_balloon.json")
            val shaped = obj(JsonSlurper().parse(recipe))
            shaped["result"] = mapOf("item" to obj(shaped["result"])["id"])
            recipe.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(shaped)))

            val got = obj(JsonSlurper().parse(unlock))
            val inventory = obj(obj(obj(got["criteria"])["has_ingredient"])["conditions"])
            @Suppress("UNCHECKED_CAST")
            val items = inventory["items"] as List<Any?>
            inventory["items"] = items.map { mapOf("items" to listOf(obj(it)["items"])) }
            unlock.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(got)))

            val advancement = obj(JsonSlurper().parse(high))
            val display = obj(advancement["display"])
            display["icon"] = mapOf("item" to obj(display["icon"])["id"])
            high.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(advancement)))

            val mixins = destinationDir.resolve("hotairballoons.mixins.json")
            val config = obj(JsonSlurper().parse(mixins))
            @Suppress("UNCHECKED_CAST")
            val common = config["mixins"] as List<String>
            config["mixins"] = common + listOf("LegacyLeadItemMixin", "LegacyLeashKnotMixin", "LegacyMobLeashMixin")
            mixins.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(config)))
        }
        if (before("1.21")) {
            for (folder in listOf("recipe", "advancement")) {
                val from = destinationDir.resolve("data/hotairballoons/$folder")
                val to = destinationDir.resolve("data/hotairballoons/${folder}s")
                to.deleteRecursively()
                check(from.renameTo(to)) { "cannot rename $from" }
            }
        }
    }
}
