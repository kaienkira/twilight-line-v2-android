# twilight-line-v2-android
twilight-line-v2 android client

## dependency
* tun2socks [https://github.com/xjasonlyu/tun2socks]
* twilight-line-v2-rust [https://github.com/kaienkira/twilight-line-v2-rust]

```
# set $ANDROID_HOME, $ANDROID_NDK_HOME, $GOPATH,
# add $GOPATH/bin to $PATH
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/bind
cd "$tun2socks_dir"
go get golang.org/x/mobile/bind
gomobile bind -o ./tun2socks.aar \
    -target android/arm64,android/amd64 -androidapi 21 "$tun2socks_dir"/engine
```
