import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("com.github.node-gradle.node") version "7.1.0"
}

val npmBuild = tasks.register<NpmTask>("npmBuild") {
    args.set(listOf("run", "build"))
    outputs.dir("dist")
    inputs.dir("src")
    dependsOn("npmInstall")
}

