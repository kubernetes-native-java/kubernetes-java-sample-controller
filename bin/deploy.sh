#!/usr/bin/env bash

GCLOUD_PROJECT=${GCLOUD_PROJECT:-pgtm-jlong}
APP_NAME=kubernetes-controller
IMAGE_NAME=gcr.io/${GCLOUD_PROJECT}/$APP_NAME
IMAGE_ID=$(docker images -q $APP_NAME)
echo ${IMAGE_ID}
docker tag "${IMAGE_ID}" $IMAGE_NAME
docker push $IMAGE_NAME

echo "Deployed the Docker image..."
pwd
cd $(dirname $0)/..
pwd
kubectl apply -f ./k8s/controller.yaml
