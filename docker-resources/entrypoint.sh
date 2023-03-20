#!/bin/sh
if [ "$(id -u)" = "0" ]; then
  # running on a developer laptop as root
  ls -l
  fix-perms -r -u user -g user /home/user
  exec gosu user sh "$@"
else
  # running in production as a user
  ls -l
  exec sh "$@"
fi