#!/usr/bin/env bash
mvn -DskipTests=true -U -Pnative clean package && ./target/spring-controller
