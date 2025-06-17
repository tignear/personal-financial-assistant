FROM mcr.microsoft.com/devcontainers/base:ubuntu

RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends git curl wget unzip zip \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

ENV SDKMAN_DIR="/usr/local/sdkman"
RUN curl 'https://get.sdkman.io?ci=true' | bash \
    && ls -la "${SDKMAN_DIR}/bin/sdkman-init.sh"

RUN /bin/bash -c "source ${SDKMAN_DIR}/bin/sdkman-init.sh \
    && sdk install java 24.0.1-graal \
    && sdk install java 24.0.1-tem \
    && sdk default java 24.0.1-tem \
    && sdk install sbt"