plugins {
  id("java")
  id("com.diffplug.spotless") version "6.7.2"
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("../buildscripts/spotless.license.java"), "(package|import|public)")
    target("src/**/*.java")
  }
}

repositories {
  mavenCentral()
}

dependencies {
  testImplementation("org.testcontainers:testcontainers:1.16.2")
  testImplementation("org.testcontainers:postgresql:1.15.3")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
  testImplementation("org.junit.jupiter:junit-jupiter-params:5.7.2")
  testImplementation("com.squareup.okhttp3:okhttp:4.9.1")
  testImplementation("org.jooq:joox:1.6.2")
  testImplementation("com.jayway.jsonpath:json-path:2.6.0")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
  testImplementation("org.slf4j:slf4j-simple:1.7.36")
}

tasks {
  test {
    useJUnitPlatform()
  }
}
