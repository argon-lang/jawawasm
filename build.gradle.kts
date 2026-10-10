import com.diffplug.gradle.spotless.SpotlessExtension
import net.ltgt.gradle.errorprone.errorprone

plugins {
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotless) apply false
}

val sharedLibs = libs

subprojects {
    apply(plugin = "java")
    apply(plugin = "net.ltgt.errorprone")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            eclipse().configFile(rootProject.file("eclipse-formatter.properties"))
        }
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        "testCompileOnly"(sharedLibs.jspecify)
        "testImplementation"(sharedLibs.junit.jupiter)
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "errorprone"(sharedLibs.nullaway)
        "errorprone"(sharedLibs.errorprone)
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(22)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.errorprone {
            disable("RefactorSwitch", "EffectivelyPrivate")
            option("NullAway:OnlyNullMarked", "true")
            option("NullAway:JSpecifyExperimental", "true")
            option("NullAway:JSpecifyMode", "true")
            error("NullAway")
            error("RequireExplicitNullMarking")
        }

        options.compilerArgs.add("-Xlint:unchecked,deprecation,fallthrough,path,rawtypes")
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    pluginManager.withPlugin("java-library") {
        group = "dev.argon.jawawasm"
        version = "0.3.0-SNAPSHOT"

        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
            withJavadocJar()
        }

        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    pom {
                        url = "https://github.com/argon-lang/jawawasm"
                        licenses {
                            license {
                                name = "GNU Lesser General Public License, Version 3"
                                url = "https://www.gnu.org/licenses/lgpl-3.0.en.html"
                            }
                        }
                        developers {
                            developer {
                                name = "argon-dev"
                                email = "argon@argon.dev"
                                organization = "argon-lang"
                                organizationUrl = "https://argon.dev"
                            }
                        }
                        scm {
                            connection = "scm:git:git@github.com:argon-lang/jawawasm.git"
                            developerConnection = "scm:git:git@github.com:argon-lang/jawawasm.git"
                            url = "https://github.com/argon-lang/jawawasm/tree/master"
                        }
                    }
                }
            }

            repositories {
                maven {
                    url = uri(layout.buildDirectory.dir("repo"))
                }
            }
        }

        extensions.configure<SigningExtension> {
            sign(extensions.getByType<PublishingExtension>().publications["mavenJava"])
        }
    }
}
