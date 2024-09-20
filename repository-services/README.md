# Repository services

In order to test the metadata submission to BioSamples and ENA, two Java Spring web services can be deployed, using docker. There is no compilation step necessary prior to deployment.

## Deployment

Change to the correct directory:

```sh
cd repository-test-services
```

Use docker compose to deploy both services simultaneously:
 
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