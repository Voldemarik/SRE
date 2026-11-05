#!/bin/bash

URL="http://localhost:30080/"
COUNT=1500
OUT="${1:-evidence/s1_delete.log}"

dir=$(dirname "$OUT")
mkdir -p "$dir"

echo "Starting load test against $URL ($COUNT requests)..."
start_time=$(date +%s)

> "$OUT"

for ((i=1; i<=COUNT; i++)); do
    timestamp=$(date +"%T.%3N")
    response=$(curl -s -m 1 -w " %{http_code} %{exitcode}" "$URL")
    echo "$timestamp$response" | tee -a "$OUT"
    sleep 0.02
done

end_time=$(date +%s)
duration=$((end_time - start_time))
if [ $duration -eq 0 ]; then duration=1; fi

echo ""
echo "=== ИТОГ ==="
total_requests=$(wc -l < "$OUT")
echo "Всего запросов: $total_requests за ${duration}с"

errs_file="evidence/errors.tmp"
grep -vE ' 200 0$' "$OUT" > "$errs_file"
err_count=$(wc -l < "$errs_file")
echo "Ошибок: $err_count"

echo "Типы ошибок:"
awk '{print $NF}' "$errs_file" | sort | uniq -c | while read count code; do
    echo "  - exit/http code $code: $count"
done

echo "По версиям:"
awk -F'"' '{
    for(i=1; i<=NF; i++) {
        if($i == "version") {
            print $(i+2)
        }
    }
}' "$OUT" | sort | uniq -c | awk '{print "  " $2 ": " $1}'

echo "По Pod:"
grep -o '"pod":"[^"]*"' "$OUT" | cut -d'"' -f4 | sort | uniq -c | awk '{print "  " $2 ": " $1}'

rm -f "$errs_file"
echo "Лог сохранен в: $OUT"