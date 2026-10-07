// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
  configurations.classpath {
    resolutionStrategy.force(
      "org.apache.commons:commons-lang3:3.21.0",
      "org.apache.httpcomponents:httpclient:4.5.14",
      "org.apache.httpcomponents:httpmime:4.5.14",
      "org.bitbucket.b_c:jose4j:0.9.7",
      "org.bouncycastle:bcpkix-jdk18on:1.86",
      "org.bouncycastle:bcprov-jdk18on:1.86",
      "org.bouncycastle:bcutil-jdk18on:1.86",
      "org.jdom:jdom2:2.0.6.1"
    )
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}

// Buildscript pins alone do not protect AGP's separate Lint/tool configurations.
// Apply the same patched dependencies to module plugins, tools, and app dependencies.
allprojects {
  val patchedDependencies = arrayOf(
    "org.apache.commons:commons-lang3:3.21.0",
    "org.apache.httpcomponents:httpclient:4.5.14",
    "org.apache.httpcomponents:httpmime:4.5.14",
    "org.bitbucket.b_c:jose4j:0.9.7",
    "org.bouncycastle:bcpkix-jdk18on:1.86",
    "org.bouncycastle:bcprov-jdk18on:1.86",
    "org.bouncycastle:bcutil-jdk18on:1.86",
    "org.jdom:jdom2:2.0.6.1"
  )
  buildscript.configurations.configureEach {
    resolutionStrategy.force(*patchedDependencies)
  }
  configurations.configureEach {
    resolutionStrategy.force(*patchedDependencies)
  }
}
