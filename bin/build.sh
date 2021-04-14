#!/usr/bin/env bash

HERE="$(dirname $0)"
echo "current diretory is $HERE "
cd "${HERE}"/..
pwd
mvn io.spring.javaformat:spring-javaformat-maven-plugin:0.0.27:apply
mvn -U -DskipTests=true clean package spring-boot:build-image
