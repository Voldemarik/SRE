#!/bin/bash

OUT="${1:-evidence/video-ready.log}"
DIR=$(dirname "$OUT")
mkdir -p "$DIR"

rm -f "$OUT"

while true; do
    ready=$(kubectl get deployment hw1-app-deployment -o jsonpath='{.status.readyReplicas}')
    total=$(kubectl get deployment hw1-app-deployment -o jsonpath='{.status.replicas}')

    if [ -z "$ready" ]; then
        ready=0
    fi
    if [ -z "$total" ]; then
        total=0
    fi

    timestamp=$(date +"%T.%3N")
    log_line="$timestamp ready: $ready  pods total: $total"

    echo "$log_line" | tee -a "$OUT"

    sleep 0.7
done