#!/usr/bin/env sh
set -e
cd "$(dirname "$0")/backend"
if [ -x ./mvnw ]; then
  ./mvnw -B spring-boot:run
else
  mvn -B spring-boot:run
fi
