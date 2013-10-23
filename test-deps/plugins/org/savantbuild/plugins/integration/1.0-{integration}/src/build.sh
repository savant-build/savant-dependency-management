#!/bin/bash
jar -cvf ../integration-1.0-IB20110110162201637.jar plugin.savant
cd ..
md5sum integration-1.0-IB20110110162201637.jar | awk -F' ' '{print $1}' > integration-1.0-IB20110110162201637.jar.md5
