# Presentation

Akka Stream application implementing reactive writings in a mongodb database.

It fills the mongodb database using randomly generated call detail records. The throughput can be configured but backpressure will prevent from overloading mongodb and making the application crash with OOM.


# Usage

The recommended way to deploy this application is with Kubernetes. It was originally implemented to test the mongoDB kubernetes operator.
Sbt native-packager is used to generate the docker image.

Example of Kubernetes deployment file is provided [here](./deploy/kubernetes/v1.7)

Don't forget to precise the mongodb credentials, specially if you use the mongodb operator for managing your mongodb cluster as it uses secured mode.
