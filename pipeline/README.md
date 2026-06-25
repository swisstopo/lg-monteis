# Pipeline

The **Pipeline** package acts as the entry point, processor and persister of sensor data in the MONTEIS system.

### Prerequisites

* Java 25
* Maven

## Dev UP

To start the Project you need to start the according compose setup first, this can be done using:

### Live view without seeding data

```shell
cd ../docker && make pipeline-empty  
```

### Live view _with_ seeding data

```shell
cd ../docker && make pipeline-seeded  
```

