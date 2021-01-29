#!/bin/bash

set -o nounset

export NAMESPACE=${NAMESPACE-fats}
export CLUSTER_NAME=${CLUSTER_NAME-fats}

# fetch FATS scripts
fats_dir=`dirname "${BASH_SOURCE[0]}"`/fats
source $fats_dir/.util.sh

kubectl delete namespace ${NAMESPACE}
