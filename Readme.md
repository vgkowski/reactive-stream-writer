# Presentation

Akka Stream application implementing reactive writings in multiple backends (including MongoDB and Elasticsearch).

It fills the backends using randomly generated call detail records. The throughput can be configured but backpressure will prevent from overloading backends and making the application crash with OOM.


# Usage

The recommended way to deploy this application is with Kubernetes. It was originally implemented to test the mongoDB kubernetes operator.
Sbt native-packager is used to generate the docker image.

Example of Kubernetes deployment file is provided [here](./deploy/kubernetes/v1.7)

Don't forget to precise backends credentials, specially if you use the mongodb operator for managing your mongodb cluster as it uses secured mode.
