#! /bin/bash
echo "Starting extraction"
if [ "$1" = 'busybox' ]
then
    echo $(git clone https://git.busybox.net/busybox/)
    ./busybox-run.sh
elif [ "$1" = 'linux' ]
then
echo "Cloning Linux, this will take quite some time."
    echo $(git clone https://github.com/torvalds/linux.git)
    ./linux-run.sh
else
    echo "Select a SPL to extract [ linux | busybox ]"
fi
