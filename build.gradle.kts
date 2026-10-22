plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("software.amazon.awssdk:bom:2.28.21"))
    implementation("software.amazon.awssdk:ec2")
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:cloudwatch")
    implementation("software.amazon.awssdk:sts")
    implementation("software.amazon.awssdk:iam")

    implementation("com.github.openstack4j.core:openstack4j:3.12")
    implementation("org.apache.logging.log4j:log4j-core:2.26.1")
    implementation("org.apache.logging.log4j:log4j-api:2.26.1")
    implementation("org.glassfish.jersey.bundles:jaxrs-ri:2.45")

    implementation(fileTree("lib") { include("**/*.jar") })
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/aufgabe1", "src/aufgabe2", "src/aufgabe3", "src/aufgabe4", "src/aufgabe5", "src/aufgabe6"))
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

val mainClasses = mapOf(
    // Aufgabe 1
    "runA1RegistryClient" to "mw.client.MWRegistryClient",
    "runA1WebServiceClient" to "mw.client.MWWebServiceClient",
    "runA1PathServer" to "mw.path.MWPathServer",
    // Aufgabe 2
    "runA2CloudController" to "mw.hybridcloud.MWCloudController",
    "runA2TweetProvider" to "mw.hybridcloud.MWTweetProvider",
    // Aufgabe 3
    "runA3NameNode" to "mw.namenode.MWNameNode",
    "runA3DFSClient" to "mw.dfsclient.MWDFSClient",
    // Aufgabe 4
    "runA4MapReduce" to "mw.mapreduce.MWMapReduce",
    // Aufgabe 6
    "runA6ZooKeeperServer" to "mw.zookeeper.MWZooKeeperServer",
    "runA6ZooKeeperImplTest" to "mw.zookeeper.MWZooKeeperImplTest",
    "runA6ZooKeeperTest" to "mw.zookeeper.MWZooKeeperTest",
)

mainClasses.forEach { (taskName, cls) ->
    tasks.register<JavaExec>(taskName) {
        group = "assignments"
        description = "Runs $cls"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass = cls
        standardInput = System.`in`
    }
}
