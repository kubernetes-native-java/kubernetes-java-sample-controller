#!/usr/bin/env bash

HERE="$(dirname $0)"
echo "current directory is $HERE "
cd "${HERE}"/..
pwd


export PROJECT_ID=${GCLOUD_PROJECT}
export ROOT_DIR=$(cd $(dirname $0) && pwd)
export APP_NAME=kubernetes-controller
export GCR_IMAGE_NAME=gcr.io/${PROJECT_ID}/${APP_NAME}
docker rmi $(docker images -a -q )
mvn -f "${ROOT_DIR}"/../pom.xml -DskipTests=true clean package spring-boot:build-image
image_id=$(docker images -q $APP_NAME)
docker tag "${image_id}" $IMAGE_NAME
docker push $IMAGE_NAME
echo "pushing ${image_id} to $IMAGE_NAME "
echo "tagging ${GCR_IMAGE_NAME}"