#!/usr/bin/env bash

cd "${0%/*}"

cd ..

export GPG_TTY=$(tty)

rm -rf ~/.m2/repository/net/dloud/

echo "==== starting to deploy platform ===="

mvn deploy -N

mvn clean deploy -DskipTests -pl common-client,common-utils,platform-utils,platform-actuator,platform-parse

echo "==== deploying client platform ===="