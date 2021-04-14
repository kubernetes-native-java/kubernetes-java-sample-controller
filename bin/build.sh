#!/usr/bin/env bash

cd $( dirname $0 )/..

mvn io.spring.javaformat:spring-javaformat-maven-plugin:0.0.27:apply
mvn -DskipTests=true clean package spring-boot:build-image
