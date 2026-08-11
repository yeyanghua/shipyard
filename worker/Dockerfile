# ============================================================
# shipyard worker (Go) - multi-stage Dockerfile
# Stage 1: golang:1.22-alpine AS build
# Stage 2: scratch (single binary + ca-certificates for HTTPS to shipyard)
# 预期镜像 ~20MB
# ============================================================

# --- Stage 1: build ---
# 国内 build 镜像 (docker hub 拉不动, 走 mirror.gcr.io / 国内 mirror 优先)
#   改镜像源: FROM mirror.gcr.io/library/golang:1.23-alpine AS build
#   备选:      docker.m.daocloud.io/library/golang:1.23-alpine
#   默认:      docker.io/library/golang:1.23-alpine (海外 PC 用)
ARG GOLANG_BASE_IMAGE=mirror.gcr.io/library/golang:1.23-alpine
FROM ${GOLANG_BASE_IMAGE} AS build

# 装 git (go mod download 可能要)
RUN apk add --no-cache git ca-certificates

# 国内代理 (Mac 本机配过, build 容器里也要, 否则 proxy.golang.org 超时)
ENV GOPROXY=https://goproxy.cn,direct
ENV GOSUMDB=sum.golang.google.cn

WORKDIR /src

# 单独 copy go.mod / go.sum,利用 layer cache
COPY go.mod go.sum* ./
RUN go mod download

# copy 源码
COPY . .

# CGO disabled (spec §5.1 要求 "单二进制 + scratch 镜像")
# ldflags: 压二进制 + 注入版本号
ARG WORKER_VERSION=dev
RUN CGO_ENABLED=0 GOOS=linux go build \
    -ldflags="-s -w -X main.workerVersion=${WORKER_VERSION}" \
    -o /out/worker \
    ./cmd/worker

# --- Stage 2: runtime ---
FROM scratch

# ca-certificates: 调 shipyard HTTPS 用
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/

# 二进制
COPY --from=build /out/worker /worker

# 用 65532 (非 root 用户, shipyard 标准)
# scratch 没有 /etc/passwd,我们用 numeric uid
USER 65532:65532

# 默认 8888 (跟 config.go 默认对齐)
EXPOSE 8888

# 健康检查 (scratch 没有 curl/wget, k8s liveness probe 直接调 HTTP 就行)
# HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
#   CMD ["/worker", "-healthcheck"]   # 这里不实现, 用 k8s probe 代替

ENTRYPOINT ["/worker"]
