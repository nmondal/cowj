# Copy dependencies to lib

Cowj supports loading Jars at runtime using the `lib:` directive in the 
yaml file. How do we generate such a folder?

### Maven

There are simple ways to get it done in `maven` as can be found from :

[java - Make Maven to copy dependencies into target/lib - Stack Overflow](https://stackoverflow.com/questions/97640/make-maven-to-copy-dependencies-into-target-lib)

Simplest way of doing this is as follows:

```shell
mvn install dependency:copy-dependencies 
```

This ensures dependencies directory created in target folder with all dependency jars.



### Gradle

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    /// Add your dependencies here
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.named('test') {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

tasks.register('copyAllDependencies', Copy) {
    from configurations.runtimeClasspath
    into "${rootDir}/path/where/to/copy"
}
build.dependsOn(copyAllDependencies)

tasks.register('deleteLibs') {
    delete "${rootDir}/path/where/to/copy"
    println("libs deleted")
}

build.dependsOn deleteLibs
```

If you are just building a cowj application, create a gradle file with
the above contents. Otherwise, it amend it as necessary. Important parts
are:

```groovy
tasks.register('copyAllDependencies', Copy) {
from configurations.runtimeClasspath
into "${rootDir}/path/where/to/copy"
}
build.dependsOn(copyAllDependencies)
```

This copied your dependencies to the right folder

and

```groovy
tasks.register('deleteLibs') {
    delete "${rootDir}/path/where/to/copy"
    println("libs deleted")
}

build.dependsOn deleteLibs
```

This deleted your copied dependencies so every build is clean

Now, just run `./gradlw build`

Now in the yaml file add the lib directive with the path to the lib folder
or the path of a single jar file if you only want to load one jar:

```yaml
port: 5003

lib: _/path/to/lib

...
```


