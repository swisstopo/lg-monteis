# Coding standards

Rules a reviewer judges by hand. Formatting and import rules are enforced by tools (Spotless for Java, Prettier and ESLint for `webapp/`), so they are not repeated here. Domain terms follow [`CONTEXT.md`](CONTEXT.md).

## Library first

Before writing plumbing around a dependency, check what its public API already offers at the version we pin. Use the library's mechanism over a hand-rolled one: state sync, URL or query handling, retries, caching, mapping, validation.

Example: the measurement table restores its filters from the URL through the ag-grid Grid State API (`initialState`, `getState`, `setState`, `stateUpdated`, part of `ag-grid-community`), not through code that copies filter models in and out by hand.

## Scope of a change

Every changed line serves the ticket the PR implements. A fix that the ticket doesn't ask for (CI workflow, an unrelated feature, a refactoring elsewhere) goes into its own commit, or into its own ticket when it changes behaviour.

## API errors

Every API error is an `ErrorDto` carrying a `messageKey`. No second error format next to it (e.g. RFC 7807 for some endpoints).

## Authorization

The Spring filter chain checks authorities only. Which rows a user may see or change is decided by the database's row-level security policies (Flyway migrations under `db/meta/schema`), not by per-entry checks in Java.
