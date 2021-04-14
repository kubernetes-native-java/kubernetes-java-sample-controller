#!/usr/bin/env bash 
export GCLOUD_PROJECT=pgtm-jlong

APP_NAME=kubernetes-client-example
IMAGE_NAME=gcr.io/${GCLOUD_PROJECT}/$APP_NAME
image_id=$(docker images -q $APP_NAME)
echo $image_id
docker tag "${image_id}" $IMAGE_NAME
docker push $IMAGE_NAME
