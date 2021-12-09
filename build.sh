#!/usr/bin/env bash
mvn -Pnative -DskipTests=true clean package && ./target/kubernetes-controller