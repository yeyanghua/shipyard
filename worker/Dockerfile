# ============================================================
# shipyard worker (Go) - multi-stage Dockerfile
# Stage 1: golang:1.22-alpine AS build
# Stage 2: scratch (single binary + ca-certificates for HTTPS to shipyard)
# 预期镜像 ~20MB
# ============================================================

# --- Stage 1: build ---
FROM golang:1.22-alpine AS build

# 装 git (go mod download 可能要)
RUN apk add --no-cache git ca-certificates

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
