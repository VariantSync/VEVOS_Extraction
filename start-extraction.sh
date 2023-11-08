#! /bin/bash
docker run --rm -v "$(pwd)/ground-truth":"/home/user/ground-truth" extraction "$@"
echo "Done."
