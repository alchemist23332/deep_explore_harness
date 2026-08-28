FROM eclipse-temurin:21-jdk-jammy

ARG NODE_VERSION=22.23.2

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        bash \
        coreutils \
        curl \
        git \
        jq \
        maven \
        patch \
        procps \
        ripgrep \
        tmux \
        unzip \
        xz-utils \
        zip \
    && rm -rf /var/lib/apt/lists/* \
    && case "$(uname -m)" in \
        aarch64) node_arch="arm64" ;; \
        x86_64) node_arch="x64" ;; \
        *) echo "Unsupported architecture: $(uname -m)" >&2; exit 1 ;; \
    esac \
    && node_archive="node-v${NODE_VERSION}-linux-${node_arch}.tar.xz" \
    && curl -fsSLO "https://nodejs.org/dist/v${NODE_VERSION}/${node_archive}" \
    && curl -fsSLO "https://nodejs.org/dist/v${NODE_VERSION}/SHASUMS256.txt" \
    && grep " ${node_archive}$" SHASUMS256.txt | sha256sum -c - \
    && tar -xJf "${node_archive}" -C /usr/local --strip-components=1 \
    && rm -f "${node_archive}" SHASUMS256.txt \
    && groupadd --gid 1000 agent \
    && useradd --uid 1000 --gid 1000 --create-home --shell /bin/bash agent \
    && npm install --global pnpm@10 \
    && mkdir -p \
        /workspace \
        /home/agent/.m2 \
        /home/agent/.npm \
        /home/agent/.local/share/pnpm/store \
    && chown -R agent:agent /workspace /home/agent

USER agent
WORKDIR /workspace

CMD ["/bin/bash", "-lc", "trap : TERM INT; sleep infinity & wait"]
