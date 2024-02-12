#!/bin/bash
USER_ID=$(id -u ${SUDO_USER:-$(whoami)})
GROUP_ID=$(id -g ${SUDO_USER:-$(whoami)})

docker build --build-arg USER_ID=$USER_ID --build-arg GROUP_ID=$GROUP_ID -t extraction .
