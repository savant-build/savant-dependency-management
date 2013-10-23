#!/bin/bash
jar -cvf ../settings-1.0.jar plugin.savant
cd ..
md5sum settings-1.0.jar | awk -F' ' '{print $1}' > settings-1.0.jar.md5
