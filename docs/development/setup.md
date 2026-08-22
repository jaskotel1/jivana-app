# Development setup

## Requirements

- JDK 17 to run Gradle and Android Gradle Plugin 9.3.1.
- Android SDK Platform 37.0 (`compileSdk 37.0`).
- An Android Studio version compatible with Android Gradle Plugin 9.3.1.

The application currently uses `targetSdk 36` and `minSdk 26`. Java source and
target compatibility remain set to Java 11.

## Open the project

1. Clone the repository.
2. Open the repository root in Android Studio.
3. Use JDK 17 as the Gradle JDK and allow Gradle sync to finish.
4. Select the `devDebug` build variant for regular development.

Use the committed Gradle Wrapper, currently Gradle 9.5.0. A separate Gradle
installation is not required. On Windows, replace `./gradlew` with
`gradlew.bat` in the commands below.

## Build variants

The `environment` flavor dimension provides two product flavors:

- `dev` for development builds.
- `prod` for production-package builds.

They combine with the existing `debug` and `release` build types. `devDebug`
is the default choice for day-to-day development.

## Common commands

```shell
./gradlew qualityCheck
./gradlew testDevDebugUnitTest
./gradlew testProdDebugUnitTest
./gradlew assembleDevDebug
```

See the root [README](../../README.md) for CI and branch workflow information.

## Compose UI tests

Compose UI tests are located in `app/src/androidTest` and use JUnit 4 with
AndroidX Test. They require a connected Android device or running emulator.

Run the DEV debug instrumentation tests with:

```shell
./gradlew connectedDevDebugAndroidTest
```

