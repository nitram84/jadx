plugins {
	id("jadx-library")
}

dependencies {
	api(project(":jadx-core"))

	testImplementation("org.apache.commons:commons-lang3:3.14.0")
	testImplementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
	testImplementation("org.ow2.asm:asm:9.7")
}
