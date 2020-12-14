# A Kubernetes Controller with Spring

A really simple controller that compiles as a native image with [GraalVM](https://github.com/oracle/graal).

## Set Up

Prerequisite:  you need a Kubernetes cluster, so `kubectl get all` has to be working. 

You also need a snapshot build of the [Kubernetes Java Client](https://github.com/kubernetes-client/java). They don't publish snapshots so you have to build it locally:

```
git clone https://github.com/kubernetes-client/java /tmp/kubernetes/client
(cd /tmp/kubernetes/client; mvn install -DskipTests)
```

Start a config server in the cluster:

```
kubectl apply -f src/test/k8s/configserver.yaml
```

and expose it via a port forward:

```
kubectl port-forward svc/configserver 8888:80
```

## Install CRDs

```
kubectl apply -f src/main/k8s/base/configclient.yaml
kubectl apply -f src/test/k8s/debug.yaml
```

## Build and Run


Then you can move on to the controller.

```
./mvnw spring-boot:run
```

Verify that it works:

```
kubectl get configmaps
```

output:

```
NAME   DATA   AGE
demo   4      84m
```

If you delete the owning resource, the config map is also deleted:

```
kubectl delete -f src/test/k8s/debug.yaml
kubectl get configmaps
```

output:

```
No resources found in default namespace.
```

## Native Image

Build and extract a native image:

```
./mvnw spring-boot:build-image
id=$(docker create spring-controller:0.0.1-SNAPSHOT)
docker cp $id:/workspace/io.kubernetes.client.examples.SpringControllerExample target/app
docker rm -v $id > /dev/null
```

Run it:

```
./target/app
```