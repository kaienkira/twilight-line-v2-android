#!/bin/bash

set -o pipefail

script_name=`basename "$0"`
script_abs_name=`readlink -f "$0"`
script_path=`dirname "$script_abs_name"`

# check args
if [ $# -ne 1 ]
then
    echo "usage: $script_name <tl_src_dir>"
    exit 1
fi

# dirs
tl_src_dir=`readlink -f "$1"`
proj_home_dir=`readlink -f "$script_path/.."`
config_output_dir=$proj_home_dir/app/src/main/assets/config
bin_output_dir=$proj_home_dir/app/src/main/jniLibs

# check exists
if [ ! -d "$tl_src_dir" ]
then
    echo "dir $tl_src_dir not found"
    exit 1
fi
if [ ! -d "$proj_home_dir" ]
then
    echo "dir $proj_home_dir not found"
    exit 1
fi
if [ ! -d "$config_output_dir" ]
then
    echo "dir $config_output_dir not found"
    exit 1
fi
if [ ! -d "$bin_output_dir" ]
then
    echo "dir $bin_output_dir not found"
    exit 1
fi

# copy files
# -- config
find "$config_output_dir"/tl-client/ -name '*.json' -delete
if [ $? -ne 0 ]; then exit 1; fi
cp "$tl_src_dir"/etc/tlclient-*.json \
   "$config_output_dir"/tl-client/
if [ $? -ne 0 ]; then exit 1; fi
# -- binary
cp "$tl_src_dir"/target/aarch64-linux-android/release/twilight-line-rust-client \
   "$bin_output_dir"/arm64-v8a/libtlclient.so
if [ $? -ne 0 ]; then exit 1; fi
cp "$tl_src_dir"/target/x86_64-linux-android/release/twilight-line-rust-client \
   "$bin_output_dir"/x86_64/libtlclient.so
if [ $? -ne 0 ]; then exit 1; fi

exit 0
