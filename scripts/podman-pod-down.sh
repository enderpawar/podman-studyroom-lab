#!/usr/bin/env bash
# podman-pod-up.sh로 만든 Pod를 정리한다.
# pod rm -f 는 Pod 안의 컨테이너(infra 포함)를 함께 지우지만 named volume은 남긴다.
# DB 데이터까지 지우려면: ./scripts/podman-pod-down.sh --volumes
set -euo pipefail

podman pod rm -f --ignore studyroom

if [[ "${1:-}" == "--volumes" ]]; then
  podman volume rm -f mysql-data
fi

podman ps -a --pod
