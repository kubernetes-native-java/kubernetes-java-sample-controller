# A Kubernetes Controller with Spring

A really simple controller (copied from the Java Client samples), modified so that it compiles as a native image with [GraalVM](https://github.com/oracle/graal).

Prerequisite: you need a snapshot build of the [Kubernetes Java Client](https://github.com/kubernetes-client/java). They don't publish snapshots so you have to build it locally:

```
git clone https://github.com/kubernetes-client/java /tmp/kubernetes/client
(cd /tmp/kubernetes/client; mvn install -DskipTests)
```

Then you can move on to the controller. Build and extract a native image:

```
mvn spring-boot:build-image
id=$(docker create spring-controller:0.0.1-SNAPSHOT)
docker cp $id:/workspace/io.kubernetes.client.examples.SpringControllerExample target/app
docker rm -v $id > /dev/null
```

Run it (you need a Kubernetes cluster, so `kubectl get all` has to be working):

```
./target/app
```