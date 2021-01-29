#!/bin/bash

set -o nounset

export NAMESPACE=${NAMESPACE-fats}
export CLUSTER_NAME=${CLUSTER_NAME-fats}

kubectl delete namespace ${NAMESPACE} || echo Done
