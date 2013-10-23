#!/bin/bash
jar -cvf ../groovy-1.0.jar plugin.savant
cd ..
md5sum groovy-1.0.jar | awk -F' ' '{print $1}' > groovy-1.0.jar.md5
