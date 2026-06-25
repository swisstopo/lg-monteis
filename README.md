# lg-monteis

This repository is used for application development of the project monteis

## Documentation

The documentation for the project lives inside this repository.
For further information of the architecture consult the according [README.md](/documentation/architecture/README.md)

## Setup

To set up your local development environment, run:

```shell
./setup.sh
```

This configures Git to use the repository-provided `.githooks` directory for Git hooks.
This ensures consistent commit message enforcement and maintains a clean, traceable commit history across all contributors.

## Dev UP

To start the Project you need to start the according compose setup first, this can be done using:

### Live view without seeding data

```shell
cd docker && make all-empty  
```

### Live view _with_ seeding data

```shell
cd docker && make all-seeded  
```

Depending on your needs you can also just start single services, for such please consult the according `README.md`.
