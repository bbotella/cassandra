# Using the Gradle Wrapper for Ant Tasks and Future Migration

This document provides instructions on how to use the new Gradle wrapper to execute existing Ant tasks and offers guidance for eventually migrating those tasks to native Gradle.

## Current State: Gradle as an Ant Wrapper

The project now includes a Gradle wrapper (`gradlew`). This wrapper is primarily configured to execute the existing Ant tasks defined in `build.xml`. This is an interim step towards potentially using Gradle more natively in the future.

**You should use `./gradlew` (or `gradlew.bat` on Windows) for all build operations previously done with `ant`.**

### Available Gradle Tasks

Most common Ant targets have been wrapped as Gradle tasks with an "ant" prefix to avoid conflicts with standard Gradle tasks. You can see all available tasks by running:

```bash
./gradlew tasks
```

The Ant wrapper tasks are grouped under "Ant Tasks". Here are some of the key ones:

#### Core Build Tasks
*   **`./gradlew antBuild`**: Compiles the main Cassandra classes (wraps `ant build`).
*   **`./gradlew antJar`**: Assembles Cassandra JAR files (wraps `ant jar`).
*   **`./gradlew antCheck`**: Verifies source code and dependencies (runs RAT, Checkstyle, etc., by wrapping `ant check`).

#### Cleaning Tasks
*   **`./gradlew antClean`**: Removes all locally created artifacts by calling Ant (wraps `ant clean`).
*   **`./gradlew antRealclean`**: Removes the entire build directory and all downloaded artifacts by calling Ant (wraps `ant realclean`).
*   **`./gradlew antCleanall`**: Alias for antRealclean; removes build directory and downloaded artifacts by calling Ant (wraps `ant cleanall`).

#### Artifact Generation Tasks
*   **`./gradlew antArtifacts`**: Creates Cassandra tarball and Maven artifacts (wraps `ant artifacts`).
*   **`./gradlew antBintar`**: Creates the Cassandra binary tarball by calling Ant (wraps `ant bintar`).
*   **`./gradlew antSourcesJar`**: Assembles the sources JAR by calling Ant (wraps `ant sources-jar`).
*   **`./gradlew antJavadocJar`**: Assembles the Javadoc JAR by calling Ant (wraps `ant javadoc-jar`).
*   **`./gradlew antDtestJar`**: Creates the dtest JAR by calling Ant (wraps `ant dtest-jar`).
*   **`./gradlew antSimulatorJars`**: Assembles simulator JARs by calling Ant (wraps `ant simulator-jars`).

#### Documentation Tasks
*   **`./gradlew antJavadoc`**: Creates Javadoc for the project by calling Ant (wraps `ant javadoc`).
*   **`./gradlew antGenDoc`**: Generates main project documentation by calling Ant (wraps `ant gen-doc`).
*   **`./gradlew antGenerateCqlHtml`**: Generates CQL HTML documentation by calling Ant (wraps `ant generate-cql-html`).

#### Test and Coverage Tasks
*   **`./gradlew antTest`**: Runs the main unit tests.
*   **`./gradlew antTestsome -Ptest.name=your.TestClass [-Ptest.methods=testMethod1,testMethod2]`**: Runs specific unit tests.
    *   Use the `-Ptest.name` project property to specify the fully qualified test class name.
    *   Optionally, use `-Ptest.methods` to specify a comma-separated list of methods.
*   **`./gradlew antLongTest`**: Runs long-running tests.
*   **`./gradlew antBurnTest`**: Runs burn tests.
*   **`./gradlew antCqlTest`**: Runs CQL tests.
*   **`./gradlew antStressTest`**: Runs stress tests (ensure stress tool is built, e.g., via `ant stress-build`).
*   **`./gradlew antFqltoolTest`**: Runs fqltool tests (ensure fqltool is built, e.g., via `ant fqltool-build`).
*   **`./gradlew antTestJvmDtest`**: Runs in-JVM distributed tests (dtests).
*   **`./gradlew antTestclasslist`**: Runs tests specified in a class list file by calling Ant. Configure file via Ant properties.
*   **`./gradlew antCodecoverage`**: Generates a code coverage report by calling Ant (runs JaCoCo).
*   **`./gradlew antJacocoReport`**: Generates a JaCoCo report from existing execution data by calling Ant.

#### IDE Integration & Sub-module Builds
*   **`./gradlew antGenerateIdeaFiles`**: Generates IntelliJ IDEA project files.
*   **`./gradlew antGenerateEclipseFiles`**: Generates Eclipse project files.
*   **`./gradlew antSimulatorAsmBuild`**: Builds the simulator-asm module by calling Ant.
*   **`./gradlew antSimulatorBootstrapBuild`**: Builds the simulator-bootstrap module by calling Ant.

#### Publishing Tasks
*   **`./gradlew antMvnInstall`**: Installs project artifacts to the local Maven repository by calling Ant.
*   **`./gradlew antPublish`**: Publishes project artifacts to the configured Maven repository by calling Ant.

#### Running Arbitrary Ant Targets

If you need to run an Ant target that does not have an explicit Gradle wrapper, you can use the `antTarget` task (this task is not prefixed):

```bash
./gradlew antTarget -PantTargetName=yourAntTargetName
```

For example, to run `ant some-other-target`:

```bash
./gradlew antTarget -PantTargetName=some-other-target
```

### Passing Properties to Ant Tasks

When using Gradle tasks that wrap Ant targets (including the generic `antTarget`), Ant properties can often be passed as Gradle project properties (`-Pproperty=value`) or system properties (`-Dproperty=value`), depending on how the Ant script consumes them.

For tasks like `antTestsome`, specific guidance is provided (e.g., `-Ptest.name=...`). For others, you might need to refer to `build.xml` to see how properties are used.

## Future Migration: Ant to Native Gradle

The long-term goal might be to migrate parts of the build logic from Ant to native Gradle. This offers benefits like better dependency management, performance, and a more modern build ecosystem. Here's a very basic overview of how common Ant operations translate to Gradle:

### 1. Dependency Management

Ant typically relies on manually managed JARs or Ivy. Gradle has robust dependency management.

**Ant (example from `build-resolver.xml` or similar for library dependencies):**
```xml
<ivy:retrieve pattern="${build.dir.lib}/jars/[artifact]-[revision](-[classifier]).[ext]" ... />
```

**Gradle (`build.gradle`):**
```gradle
repositories {
    mavenCentral()
    // other repositories
}

dependencies {
    implementation 'org.slf4j:slf4j-api:1.7.30'
    testImplementation 'junit:junit:4.13.2'
    // implementation project(':another-subproject') // For multi-project builds
}
```
This requires defining your dependencies explicitly. Gradle will download them automatically.

### 2. Compiling Java Code

**Ant (`build.xml`):**
```xml
<javac destdir="${build.classes.main}" ...>
    <src path="${build.src.java}"/>
    <classpath refid="cassandra.classpath"/>
</javac>
```

**Gradle (`build.gradle` with Java plugin):**
```gradle
plugins {
    id 'java'
}

// sourceSets define where your source code is, default is src/main/java
// sourceSets {
//     main {
//         java {
//             srcDirs = ['src/java', 'src/gen-java'] // Example if sources are not in default location
//         }
//     }
// }

// Java compile options can be configured if needed
tasks.withType(JavaCompile) {
    options.encoding = 'UTF-8'
    // options.compilerArgs.add('--add-exports=...') // For module access
}
```
Gradle's `java` plugin adds `compileJava`, `classes`, and other tasks.

### 3. Running Tests

**Ant (`build.xml` using `junit` task):**
```xml
<junit fork="on" ...>
    <batchtest todir="${build.test.output.dir}/@{testtag}">
        <fileset dir="@{inputdir}" includes="@{filter}"/>
    </batchtest>
</junit>
```

**Gradle (`build.gradle` with Java plugin):**
```gradle
plugins {
    id 'java' // or java-library
}

// test task is automatically added by the java plugin
test {
    useJUnitPlatform() // Or useJUnit() for JUnit 4
    // systemProperty 'property', 'value'
    // maxHeapSize = '1G'

    // To run specific tests:
    // ./gradlew test --tests "your.TestClass"
    // ./gradlew test --tests "your.TestClass.testMethod"
}
```

### 4. Creating JARs

**Ant (`build.xml`):**
```xml
<jar jarfile="${build.dir}/${final.name}.jar">
    <fileset dir="${build.classes.main}"/>
    <manifest>
        <attribute name="Implementation-Title" value="Cassandra"/>
        ...
    </manifest>
</jar>
```

**Gradle (`build.gradle` with Java plugin):**
```gradle
plugins {
    id 'java'
}

jar {
    manifest {
        attributes(
            'Implementation-Title': 'Cassandra',
            'Implementation-Version': project.version,
            // ... other attributes
        )
    }
}
```

### Migration Strategy (High-Level)

A full migration would be a significant undertaking. A possible approach:
1.  **Identify Independent Modules/Functionality:** Start with smaller, more isolated parts of the build (e.g., a specific tool or a sub-module if the project were structured that way).
2.  **Dependencies First:** Convert library dependency management to Gradle for the chosen module.
3.  **Compile & JAR:** Migrate compilation and JAR creation for that module.
4.  **Tests:** Migrate tests for that module.
5.  **Iterate:** Gradually expand the natively built parts, ensuring the Ant-wrapped tasks and natively built Gradle tasks can coexist and depend on each other if necessary (e.g., Gradle task using JAR produced by an Ant task, or vice-versa).

This is a simplified overview. Real-world migration involves careful planning, understanding existing Ant script intricacies (properties, custom tasks, control flow), and thorough testing at each step.
