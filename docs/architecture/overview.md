# Architecture overview

Jivana currently uses a single Android `app` module. Code inside the module is
organized into package-level layers so that responsibilities remain clear as
the application grows.

## Layers

- `core` contains shared elements that are not owned by a specific feature.
  The current Compose theme is located under `core/ui`.
- `data` is the place for data sources, mapping, and implementations of
  repository contracts. Room dependencies are configured, but no database,
  entities, DAOs, or repositories have been implemented yet.
- `domain` is reserved for framework-independent business models, repository
  contracts, and use cases. It must not depend on Android, Compose, Room,
  `data`, or `presentation`.
- `presentation` contains Compose UI and application navigation. New UI
  functionality should be grouped by feature under `presentation/feature`
  instead of being organized only by technical type.
- `di` is the composition boundary for dependency injection. The package is
  present, but dependency injection has not been configured yet.

## Dependency direction

The primary dependency direction is:

```text
presentation -> domain
data         -> domain
```

UI code must access application behavior through domain contracts rather than
directly using Room or another concrete data source. The domain layer remains
independent of UI and infrastructure frameworks.

