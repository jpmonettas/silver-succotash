#!/bin/bash
# usage: create_user.sh <user_name> <key> <port>
U=$1
# create a random user id if none is supplied
if [ -z "$1" ]; then
  U=bh247_$(cat /dev/urandom | tr -dc 'a-z' | fold -w 8 | head -n 1)
  # check if user exists (in a cluster we should implement a central repo to ask)
  EXISTS=$(getent passwd $U  > /dev/null)
  if [ $? -eq 0 ]; then
    echo "sorry, the user exists"
    exit 1
  fi
fi

echo "the user is $U"
PUBKEY=$2
# check if key was specified
if [ -z "$2" ]; then
# create auth_keys and save on private/ $U and $U.pub (add public to DB?)
$(ssh-keygen -t rsa -f /home/bhdev/private_keys/$U -q -N '')
PUBKEY=$(cat /home/bhdev/private_keys/$U.pub | cut -d " " -f 2)
#grab the public
#cat private/U.pub | cut -d " " -f 2
  echo "missing key"
fi
# check if port was specified
PORT=$3
if [ -z "$3" ]; then
# grab an empty port
  PORT=$(cat last_port)
  ((PORT+=1))
  $(echo $PORT > last_port)
  echo "missing port"
fi
#create user
sudo adduser --disabled-password --gecos "" $U
#create authorized_keys
sudo -H -u $U bash -c 'cd ~;mkdir .ssh;chmod 700 .ssh;touch .ssh/authorized_keys;chmod 600 .ssh/authorized_keys'
# add key/port (from args if passed or generate it)
ENTRY="no-pty,no-X11-forwarding,permitopen=\"localhost:${PORT}\",command=\"/bin/echo do-not-send-commands\" ssh-rsa ${PUBKEY}"
destdir="/home/${U}/.ssh/authorized_keys"
echo "$ENTRY"|sudo tee "$destdir"

#	ssh -fN -R 2223:localhost:80 -i /home/behome247/private/key ckxadfbozybvfgmp@192.168.1.106


