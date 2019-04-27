#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

#
# Dockerfile for guacamole-client
#

# Use args for Tomcat image label to allow image builder to choose alternatives
# such as `--build-arg TOMCAT_JRE=jre8-alpine`
#
ARG TOMCAT_VERSION=8.5
ARG TOMCAT_JRE=jre8

# Use official maven image for the build
FROM maven:3-jdk-8 AS builder

# Use args to build radius auth extension such as
# `--build-arg BUILD_PROFILE=lgpl-extensions`
ARG BUILD_PROFILE

# Build environment variables
ENV \
    BUILD_DIR=/tmp/guacamole-docker-BUILD

# Add configuration scripts
COPY guacamole-docker/bin/ /opt/guacamole/bin/

# Copy source to container for sake of build
COPY . "$BUILD_DIR"

# Update to org.apache.directory.api.api-all 2.0.0.AM3-SNAPSHOT for more debug info
WORKDIR /root
RUN apt-get update && apt-get install git -y
RUN git clone https://github.com/apache/directory-ldap-api.git
WORKDIR /root/directory-ldap-api
RUN mvn clean install -DskipTests -Drat.skip=true
#RUN pwd

# Run the build itself
RUN /opt/guacamole/bin/build-guacamole.sh "$BUILD_DIR" /opt/guacamole "$BUILD_PROFILE"

# For the runtime image, we start with the official Tomcat distribution
FROM tomcat:${TOMCAT_VERSION}-${TOMCAT_JRE}

# This is where the build artifacts go in the runtime image
WORKDIR /opt/guacamole

# Copy artifacts from builder image into this image
COPY --from=builder /opt/guacamole/ .

COPY server.xml /usr/local/tomcat/conf/server.xml
# Copy in debugging information
COPY setenv.sh /usr/local/tomcat/bin/

# Start Guacamole under Tomcat, listening on 0.0.0.0:8080
EXPOSE 8080
CMD ["/opt/guacamole/bin/start.sh" ]

