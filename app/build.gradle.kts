plugins {
    id("com.android.application")
}

android {
    namespace = "com.aura"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aura"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = false
        viewBinding = true
    }

    buildTypes {
        debug {
            ndk {
                abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        disable += setOf(
            "IconLauncherShape",
            "IconDipSize",
            "IconDuplicates",
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion"
        )
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Camera & Vision
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.guava:guava:33.2.1-android")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    implementation("com.google.android.gms:play-services-location:21.4.0")

    // AR & Spatial Navigation
    implementation("com.google.ar:core:1.44.0")
    implementation("androidx.xr.scenecore:scenecore:1.0.0-alpha01")

    // AppFunctions & Security
    implementation("androidx.appfunctions:appfunctions:1.0.0-alpha10")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    testImplementation("junit:junit:4.13.2")
}

tasks.register("generateEclipseConfig") {
    doLast {
        val projDir = projectDir
        val projectFile = file(".project")
        val classpathFile = file(".classpath")

        projectFile.writeText("""<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
	<name>app</name>
	<comment></comment>
	<projects>
	</projects>
	<buildSpec>
		<buildCommand>
			<name>org.eclipse.jdt.core.javabuilder</name>
			<arguments>
			</arguments>
		</buildCommand>
	</buildSpec>
	<natures>
		<nature>org.eclipse.jdt.core.javanature</nature>
	</natures>
</projectDescription>
""".trimIndent())

        val cp = android.applicationVariants.find { it.name == "debug" }?.javaCompileProvider?.get()?.classpath?.files ?: emptySet()
        val testCp = android.unitTestVariants.find { it.name == "debugUnitTest" }?.javaCompileProvider?.get()?.classpath?.files ?: emptySet()
        val allCp = (cp + testCp).filter { it.exists() && it.name.endsWith(".jar") }.distinctBy { it.absolutePath }
        val sdkDir = android.sdkDirectory.absolutePath.replace("\\", "/")
        
        var androidJar = file("$sdkDir/platforms/android-37.0/android.jar")
        if (!androidJar.exists()) {
            androidJar = file("$sdkDir/platforms/android-36/android.jar")
        }
        if (!androidJar.exists()) {
            androidJar = file("$sdkDir/platforms/android-35/android.jar")
        }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<classpath>\n")
        sb.append("\t<classpathentry kind=\"src\" path=\"src/main/java\"/>\n")
        sb.append("\t<classpathentry kind=\"src\" path=\"src/test/java\"/>\n")
        sb.append("\t<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER\"/>\n")

        if (androidJar.exists()) {
            sb.append("\t<classpathentry kind=\"lib\" path=\"${androidJar.absolutePath.replace("\\", "/")}\"/>\n")
        }

        val homeDir = System.getProperty("user.home")
        val rJar = file("$homeDir/.gradle_builds/auraa/app/intermediates/compile_and_runtime_r_class_jar/debug/processDebugResources/R.jar")
        if (rJar.exists()) {
            sb.append("\t<classpathentry kind=\"lib\" path=\"${rJar.absolutePath.replace("\\", "/")}\"/>\n")
        }

        val junitJar = file("$homeDir/.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2/8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12/junit-4.13.2.jar")
        if (junitJar.exists()) {
            sb.append("\t<classpathentry kind=\"lib\" path=\"${junitJar.absolutePath.replace("\\", "/")}\"/>\n")
        }

        val hamcrestJar = file("$homeDir/.gradle/caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3/42a25dc3219429f0e5d060061f71acb49bf010a0/hamcrest-core-1.3.jar")
        if (hamcrestJar.exists()) {
            sb.append("\t<classpathentry kind=\"lib\" path=\"${hamcrestJar.absolutePath.replace("\\", "/")}\"/>\n")
        }

        for (f in allCp) {
            val p = f.absolutePath.replace("\\", "/")
            sb.append("\t<classpathentry kind=\"lib\" path=\"$p\"/>\n")
        }

        sb.append("\t<classpathentry kind=\"output\" path=\"build/classes/java/main\"/>\n")
        sb.append("</classpath>\n")

        classpathFile.writeText(sb.toString())
        println("Generated app/.project and app/.classpath successfully with ${cp.size} libraries.")
    }
}

