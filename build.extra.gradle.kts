import org.gradle.kotlin.dsl.withGroovyBuilder

// Common character input and spans contain no JVM dependency; expose them to browser parsers too.
// Applied scripts do not share the generated build's Kotlin-plugin classloader.
extensions.getByName("kotlin").withGroovyBuilder {
    "js" {
        "browser"()
        "nodejs"()
    }
}
