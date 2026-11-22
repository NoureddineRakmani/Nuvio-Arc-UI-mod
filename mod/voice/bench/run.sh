#!/system/bin/sh
# PG Voice: Whisper speed on the box, short command-length clip. Run from /data/local/tmp/pgv.
cd /data/local/tmp/pgv
for m in ggml-tiny-q8_0.bin ggml-tiny-q5_1.bin ggml-base-q5_1.bin; do
  for ac in 256 0; do
    s=$(date +%s%N)
    out=$(./whisper-cli -m $m -f short.wav -t 4 -l auto -ac $ac -nt -np 2>/dev/null)
    e=$(date +%s%N)
    echo "$m ac=$ac: $(( (e - s) / 1000000 )) ms  [$out]"
  done
done
