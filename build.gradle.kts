import com.jfrog.bintray.gradle.*
import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.LinkMapping
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.concurrent.*

val commitHash by lazy {
	val process: Process = Runtime.getRuntime().exec("git rev-parse --short HEAD")
	process.waitFor()
	val output = process.inputStream.use {
		it.bufferedReader().use {
			it.readText()
		}
	}
	process.destroy()
	output.trim()
}

val isCI = !System.getenv("CI").isNullOrBlank()

val comingVersion = "1.8.6"
val packageName = "org.frice"
val kotlinVersion: String by extra

group = packageName
version = if (isCI) "$comingVersion-$commitHash" else comingVersion

buildscript {
	var dokkaVersion: String by extra
	dokkaVersion = "0.9.17"

	repositories {
		mavenCentral()
		jcenter()
	}

	dependencies {
		classpath("org.jetbrains.dokka:dokka-gradle-plugin:$dokkaVersion")
	}
}

plugins {
	java
	maven
	`maven-publish`
	kotlin("jvm") version "1.2.60"
	id("com.jfrog.bintray") version "1.7.3"
}

apply { plugin("org.jetbrains.dokka") }

java {
	sourceCompatibility = JavaVersion.VERSION_1_8
	targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		jvmTarget = "1.8"
		freeCompilerArgs = listOf("-Xjvm-default=enable")
		suppressWarnings = false
		verbose = isCI
	}
}

tasks.withType<JavaCompile> {
	sourceCompatibility = "1.8"
	targetCompatibility = "1.8"
	options.apply {
		isDeprecation = true
		isWarnings = true
		isDebug = !isCI
		compilerArgs.add("-Xlint:unchecked")
		encoding = "UTF-8"
	}
}

bintray {
	user = "ice1000"
	key = findProperty("key").toString()
	setConfigurations("archives")
	pkg.apply {
		name = project.name
		repo = "FriceEngine"
		githubRepo = "icela/FriceEngine"
		publicDownloadNumbers = true
		vcsUrl = "https://github.com/icela/FriceEngine.git"
		version.apply {
			name = comingVersion
			vcsTag = "v$comingVersion"
			websiteUrl = "https://github.com/icela/FriceEngine/releases/tag/$vcsTag"
		}
	}
}

publishing {
	(publications) {
		"mavenJava"(MavenPublication::class) {
			from(components["java"])
			groupId = packageName
			artifactId = project.name
			version = comingVersion
			artifact(sourcesJar)
			artifact(javadok)
			pom.withXml {
				val root = asNode()
				root.appendNode("description", "JVM game engine based on Swing/JavaFX")
				root.appendNode("name", project.name)
				root.appendNode("url", "https://icela.github.io")
				root.children().last()
			}
		}
	}
}

java.sourceSets {
	"main" {
		resources.srcDirs("res")
		java.srcDirs("src")
		withConvention(KotlinSourceSet::class) {
			kotlin.setSrcDirs(listOf("src"))
		}
	}

	"test" {
		java.srcDirs("test")
		withConvention(KotlinSourceSet::class) {
			kotlin.setSrcDirs(listOf("test"))
		}
	}
}

repositories {
	mavenCentral()
	jcenter()
}

configurations {
	create("library")
}

val NamedDomainObjectCollection<Configuration>.library get() = this["library"]

dependencies {
	val libraries = file("lib").list { it, _ -> "jar" == it.extension }

	compile(kotlin("stdlib-jdk8"))
	"library"(files(*libraries))
	configurations.compileOnly.extendsFrom(configurations.library)
	testCompile("junit", "junit", "4.12")
	testCompile(kotlin("test-junit"))
}

val javadoc = tasks["javadoc"] as Javadoc
val jar = tasks["jar"] as Jar
jar.from(Callable {
	configurations.library.map {
		@Suppress("IMPLICIT_CAST_TO_ANY")
		if (it.isDirectory) it else zipTree(it)
	}
})

val fatJar = task<Jar>("fatJar") {
	classifier = "all"
	description = "Assembles a jar archive containing the main classes and all the dependencies."
	group = "build"
	from(Callable {
		configurations.compile.map {
			@Suppress("IMPLICIT_CAST_TO_ANY")
			if (it.isDirectory) it else zipTree(it)
		}
	})
	with(jar)
}

val sourcesJar = task<Jar>("sourcesJar") {
	classifier = "sources"
	description = "Assembles a jar archive containing the source code of this project."
	group = "build"
	from(java.sourceSets["main"].allSource)
}

val dokka = tasks["dokka"] as DokkaTask
dokka.apply {
	moduleName = "engine"
	outputFormat = "html-as-java"
	outputDirectory = javadoc.destinationDir?.absolutePath.toString()

	includes = listOf("LICENSE.txt", "README.md")
	// samples = ['test/org/frice/Test.kt', 'test/org/frice/JfxTest.kt']
	impliedPlatforms = mutableListOf("JVM")

	jdkVersion = 8

	skipDeprecated = false
	reportUndocumented = false
	noStdlibLink = false

	linkMappings.add(LinkMapping().apply {
		dir = "src"
		url = "https://github.com/icela/FriceEngine/blob/master/src"
		suffix = "#L"
	})

	// externalDocumentationLink { url = new URL("https://icela.github.io/") }
}

val javadok = task<Jar>("javadok") {
	classifier = "javadoc"
	description = "Assembles a jar archive containing the javadoc of this project."
	group = "documentation"
	from(javadoc.destinationDir)
	dependsOn(dokka)
}

task("displayCommitHash") {
	group = "help"
	description = "Display the newest commit hash"
	doFirst {
		println("Commit hash: $commitHash")
	}
}

task("isCI") {
	group = "help"
	description = "Check if it's running in a continuous-integration"
	doFirst {
		println(if (isCI) "Yes, I'm on a CI." else "No, I'm not on CI.")
	}
}

artifacts {
	add("archives", jar)
	add("archives", fatJar)
	add("archives", sourcesJar)
	add("archives", javadok)
}
