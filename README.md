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


To compile clojurescript to javascript:

```
lein clean
lein cljsbuild once min
```
