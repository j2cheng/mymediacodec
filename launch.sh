#!/bin/sh

OPTION=""
if [ ! -z $1 ]; then
    OPTION="-s $1"
fi

adb $OPTION shell am start com.splashtop.demo/.MainActivity

