# Contracts

This module turns API specs into Java types. It has no runtime behaviour of its own. All its dependencies are `provided`, and both `core` and `pipeline` depend on the jar it produces.

## What is generated

|                    Spec                    |         Execution         |              Generated into              |
|--------------------------------------------|---------------------------|------------------------------------------|
| `src/main/resources/openapi.yml`           | `generate-monteis-models` | `ch.swisstopo.monteis.contracts`         |
| `src/main/resources/fulcrum/rest-api.json` | `generate-fulcrum-models` | `ch.swisstopo.monteis.contracts.fulcrum` |

Both executions sit on the `openapi-generator-maven-plugin` in `pom.xml`.

## Why here and not in Core

`SensorConfig` comes from `openapi.yml` and is the Kafka payload between the applications. `core` publishes it, `pipeline` consumes it. A type shared by two modules needs a third module, so this one exists either way.

The Fulcrum client is only used by `core` today. It is still generated here, so that there is one plugin and one place to look for generated types, and so that no spec has to be copied between modules during the build.

## Fulcrum Api Spec

The Fulcrum spec is a third-party document, so it has its own pin, refresh script and CI check. How it gets here and what happens when Fulcrum changes it: [README_FUlCRUM_API_SPEC.md](README_FUlCRUM_API_SPEC.md).

