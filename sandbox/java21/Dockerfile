FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        bash \
        coreutils \
        curl \
        git \
        jq \
        maven \
        patch \
        ripgrep \
        unzip \
        zip \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 1000 agent \
    && useradd --uid 1000 --gid 1000 --create-home --shell /bin/bash agent \
    && mkdir -p /workspace /home/agent/.m2 \
    && chown -R agent:agent /workspace /home/agent

USER agent
WORKDIR /workspace

CMD ["/bin/bash", "-lc", "trap : TERM INT; sleep infinity & wait"]
