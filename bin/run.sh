#!/usr/bin/env bash


docker run \
  --volume ${HOME}/.kube:/home/cnb/.kube \
  docker.io/library/kubernetes-client-example:0.0.1-SNAPSHOT
