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

The local equivalent of the primary quality gate is:

```shell
./gradlew qualityCheck
```

## Branch Workflow

`master` contains stable, production-ready code. Do not work or push directly
to this branch. Promote changes to `master` through a controlled pull request
from `develop` after review and a successful CI run.

`develop` is the integration branch for ongoing development. Create every
short-lived working branch from the latest `develop` and merge it back through
a pull request.

Use one of these branch name formats:

- `feature/<short-description>` for new functionality, for example
  `feature/habit-creation` or `feature/avatar-screen`.
- `bugfix/<short-description>` for fixes, for example
  `bugfix/navigation-crash`.
- `chore/<short-description>` for maintenance, for example
  `chore/update-dependencies`.

A typical change follows this process:

1. Update local `develop` with `git switch develop` and `git pull --ff-only`.
2. Create a working branch, for example
   `git switch -c feature/habit-creation`.
3. Implement and commit the focused change.
4. Run `./gradlew qualityCheck` and the relevant tests locally.
5. Push the working branch and open a pull request to `develop`.
6. Wait for review and a successful Android CI run.
7. Prefer **Squash and merge** when merging the pull request to `develop`, then
   delete the working branch.
8. Promote an approved, stable `develop` to `master` using a separate pull
   request with green CI. Use a regular merge commit or squash consistently.

The expected flow is:

```text
develop -> feature/* | bugfix/* | chore/* -> PR -> develop -> PR -> master
```

Configure branch protection manually in GitHub repository settings:

- For `master`: require a pull request and successful Android CI, block direct
  pushes and force pushes, and prevent branch deletion.
- For `develop`: require a pull request and successful Android CI, and block
  force pushes.
