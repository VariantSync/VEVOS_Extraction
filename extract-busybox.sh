#! /bin/bash
docker build -t busybox-extraction - < Dockerfile.busybox
docker run busybox-extraction
