import java.util.zip.ZipFile

plugins {
    id("io.papermc.paperweight.userdev")
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

dependencies {
    api(project(":platforms:bukkit:common"))
    paperweight.paperDevBundle(Versions.Bukkit.paperDevBundle)
    implementation("xyz.jpenilla", "reflection-remapper", Versions.Bukkit.reflectionRemapper)
}

tasks.register("inspectClasses") {
    doLast {
        val cp = sourceSets["main"].compileClasspath
        cp.filter { it.extension == "jar" }.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (entry.name.contains("Ambient") || entry.name.contains("Particle") || entry.name.contains("Music") || entry.name.contains("Sound")) {
                        if (entry.name.startsWith("net/minecraft") && entry.name.endsWith(".class")) {
                            println("${entry.name}")
                        }
                    }
                }
            }
        }
    }
}