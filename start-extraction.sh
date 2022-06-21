#! /bin/bash
docker run --rm -v "$(pwd)/extraction-results":"/home/user/extraction-results/output" extraction "$@"
echo "Done."
