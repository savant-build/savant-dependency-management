#!/bin/bash
jar -cvf ../java-1.0.jar plugin.savant
cd ..
md5sum java-1.0.jar | awk -F' ' '{print $1}' > java-1.0.jar.md5
