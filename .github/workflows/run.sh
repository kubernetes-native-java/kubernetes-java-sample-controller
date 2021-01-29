#!/bin/bash -x

set -o errexit
set -o nounset
set -o pipefail

export CLUSTER=${CLUSTER-kind}
export CLUSTER_NAME=${CLUSTER_NAME-fats}
export REGISTRY=${REGISTRY-docker-daemon}
export NAMESPACE=${NAMESPACE-fats}

basedir=$(realpath `dirname "${BASH_SOURCE[0]}"`/../..)
cd ${basedir}

./mvnw test -D test=ClusterIT
