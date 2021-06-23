#! /bin/bash
docker build -t variability-extraction \
  --build-arg USER_ID="$(id -u)" \
  --build-arg GROUP_ID="$(id -g)" .
