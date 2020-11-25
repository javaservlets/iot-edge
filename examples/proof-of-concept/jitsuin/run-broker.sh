#!/usr/bin/env bash

#
# Copyright 2020 ForgeRock AS
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

AM_URL="https://iot.iam.example.com/am"
if [ -n "$1" ]; then
  AM_URL=$1
fi

rm -rf mqtt-broker/tmp && mkdir mqtt-broker/tmp
cp mqtt-broker/enterprise-security-extension-template.xml mqtt-broker/tmp/enterprise-security-extension.xml
sed -i '' "s,&{am.url},$AM_URL,g" "$(pwd)/mqtt-broker/tmp/enterprise-security-extension.xml"

AM_URL=$AM_URL docker-compose build hivemq
AM_URL=$AM_URL docker-compose run hivemq
