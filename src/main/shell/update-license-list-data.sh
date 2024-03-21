
mkdir -p build/spdx
git clone --depth=1 git@github.com:spdx/license-list-data.git build/license-list-data
rm -rf src/main/resources/license-details
rm -rf src/main/resources/license-exceptions
rm src/main/resources/exceptions.json
rm src/main/resources/licenses.json
cp -Rp build/license-list-data/json/details src/main/resources/license-details
cp -Rp build/license-list-data/json/exceptions src/main/resources/license-exceptions
cp -p build/license-list-data/json/exceptions.json src/main/resources/exceptions.json
cp -p build/license-list-data/json/licenses.json src/main/resources/licenses.json


