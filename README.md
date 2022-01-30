# A Kubernetes Controller with Spring

> This sample is [99% the amazing work]](https://github.com/scratches/spring-controller) of the good and the 
great [Dr. David Syer](https://twitter.com/david_syer). I have only updated it to use the [Kubernetes Native Java](https://github.com/kubernetes-native-java) 
 [Spring Native hints for the official Kubernetes Java client](https://github.com/kubernetes-native-java/kubernetes-java-spring-native) 
and to use Java 17 (because, why not?).

A really simple controller that compiles as a native image with [GraalVM](https://github.com/oracle/graal). 

## Set Up

Prerequisite:  you need a Kubernetes cluster, so `kubectl get all` has to be working. 

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
kubectl apply -f src/main/k8s/crds/configclient.yaml
```

Create a resource instance:

```
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