# jivana-app

## Code quality

Run all code quality checks:

```shell
./gradlew qualityCheck
```

Individual checks and formatting:

```shell
./gradlew ktlintCheck
./gradlew ktlintFormat
./gradlew detekt
```

## Continuous integration

GitHub Actions runs Android CI for every push and for pull requests targeting
`master` or `develop`. The workflow runs code quality checks, debug unit tests,
and builds both the `devDebug` and `prodDebug` variants.

The local equivalent of the main quality gate is:

```shell
./gradlew qualityCheck
```
