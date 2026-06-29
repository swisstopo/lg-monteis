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

Before you run any command you should create a `.env` file in the docker directory.

To start the Project you need to start the according compose setup first, this can be done using:

### Live view without seeding data

```shell
cd docker && make all-empty  
```

### Live view _with_ seeding data

```shell
cd docker && make all-seeded  
```

These commands will start MSK Connect. If the startup fails due to a timeout, you can increase the number of retries of the health check locally in the [Compose file](docker/compose.yml).

Depending on your needs you can also just start single services, for such please consult the according `README.md`.

### Backend setup

- Proper **Core API** setup in this [README.md](/core/README.md)

- Proper **Pipeline** setup in this [README.md](/pipeline/README.md)

### Frontend setup

The proper Frontend setup is documented int this [README.md](/webapp/README.md)
