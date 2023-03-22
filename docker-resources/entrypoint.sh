#!/bin/sh
if [ "$(id -u)" = "0" ]; then
  # running on a developer laptop as root
  ls -l
  ./fix-perms.sh -r -u user -g user /home/user
  exec gosu user "$@"
else
  # running in production as a user
  ls -l
  exec "$@"
fi