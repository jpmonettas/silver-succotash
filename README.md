# silver-succotash

A [re-frame](https://github.com/Day8/re-frame) application designed as a UI to administer sshd remote port forwarding.
It uses a patched version of open-ssh to handle the forwarding. This patched version limits the "local" ports available for a user on the remote server by using .ssh/authorized_keys

## Development Mode

### Run application:

```
lein clean
lein figwheel dev
```

Figwheel will automatically push cljs changes to the browser.

Wait a bit, then browse to [http://localhost:3449](http://localhost:3449).

## Production Build

```
lein release
java -jar recom-0.1.0-SNAPSHOT-standalone.jar
```
## sudoers

```
bhdev ALL= NOPASSWD: /bin/cat
bhdev ALL= NOPASSWD: /usr/bin/lsof
bhdev ALL= NOPASSWD: /usr/sbin/userdel
bhdev ALL= NOPASSWD: /usr/bin/tee
bhdev ALL= NOPASSWD: /usr/sbin/adduser
bhdev ALL= NOPASSWD: /bin/mkdir
bhdev ALL= NOPASSWD: /usr/bin/touch
```

## client side
```
ssh -fN -R 2291:localhost:80 -i /home/behome247/private/key bh247_xcuhgzvr@192.168.1.21 -vvv
```
