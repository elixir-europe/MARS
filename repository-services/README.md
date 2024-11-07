# Repository services

In order to test the metadata submission to BioSamples and ENA, two Java Spring web services can be deployed, using docker. There is no compilation step necessary prior to deployment.

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/) installed on your system.

## Deployment - Running the Docker Containers

1. **Navigate** to the `repository-services` directory in your cloned repository.

```sh
cd repository-test-services
```

2. **Start the Docker containers** to deploy both services simultaneously:
 
 ```sh
 docker compose up
 ```

Or daemonized:

 ```sh
 docker compose up -d
 ```

 ## Usage

 After deployment, two web services are available to accept requests, consume the provided ISA JSON, and pass the data to the target repository.

 The API end points of the respective web services are documented in the Swagger UI in these locations:

 - ENA Web Service: ISA http://localhost:8042/isaena/
 - BioSamples Web Service: http://localhost:8032/isabiosamples/

 If the web services are not deployed locally, adjust the base URL accordingly.