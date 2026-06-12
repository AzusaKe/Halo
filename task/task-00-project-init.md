# Task 00: Project Structure Initialization

## Agent Type: `general-purpose`

## Goal
Create all build configuration files and mod entrypoints so `./gradlew build` compiles and launches Minecraft 1.20.1 with Fabric.

## Dependencies
None — this is the first task.

## Input
- Existing directory scaffold under `src/main/java/com/example/halo/` and `src/main/resources/`
- Gradle wrapper already cached at `gradle/wrapper/gradle-wrapper.jar` (version 8.8)
- Existing default texture at `assets/halo/textures/halo/ring_default.png` (64x64 RGBA PNG)

## Task

### 1. Create `settings.gradle`
```
pluginManagement {
    repositories {
        maven { url 'https://maven.fabricmc.net/' }
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = 'halo'
```

### 2. Create `build.gradle`
Use Fabric Loom 1.5+, target Java 17, Minecraft 1.20.1, Yarn mappings `1.20.1+build.10:v2`, fabric-loader >= 0.15.0, fabric-api 0.92.0+1.20.1.

### 3. Create `gradle-wrapper.properties`
Gradle 8.8, distribution type `all`.

### 4. Create `gradle.properties`
Standard Fabric properties: `archives_base_name=halo`, `mod_version=0.1.0`, `maven_group=com.example`.

### 5. Create `src/main/resources/fabric.mod.json`
- mod id: `halo`
- entrypoints: `main` → `com.example.halo.HaloMod`, `client` → `com.example.halo.HaloModClient`
- depends: fabricloader >= 0.15.0, fabric-api *, minecraft ~1.20.1, java >= 17
- mixins: `halo.mixins.json`

### 6. Create `src/main/resources/halo.mixins.json`
Package `com.example.halo.mixin`, compatibilityLevel JAVA_17, client mixins array (empty for now).

### 7. Create entrypoint stubs
- `HaloMod.java`: implements `ModInitializer`, logs "Halo mod initialized"
- `HaloModClient.java`: implements `ClientModInitializer`, logs "Halo client initialized"

### 8. Verify
Run `./gradlew build` — must compile successfully and produce a JAR.

## Output Artifacts
- `settings.gradle`
- `build.gradle`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle.properties`
- `src/main/resources/fabric.mod.json`
- `src/main/resources/halo.mixins.json`
- `src/main/java/com/example/halo/HaloMod.java`
- `src/main/java/com/example/halo/HaloModClient.java`
