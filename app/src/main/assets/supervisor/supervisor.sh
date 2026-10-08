#!/bin/sh
# UIN shared backend supervisor (root passed as $1)
ROOT=$1
CTRL=$ROOT/.uin
LOGDIR=$CTRL/logs
LOG=$LOGDIR/supervisor.log
mkdir -p $CTRL/cmd $CTRL/pid $CTRL/stop $CTRL/idle $CTRL/done $CTRL/rc $LOGDIR

log() { echo "[$(date '+%H:%M:%S')] $*" >> "$LOG"; }
# 递归杀进程树：pgrep 不可用时用 /proc 兜底
find_children() {
  local p="$1"
  if command -v pgrep >/dev/null 2>&1; then
    pgrep -P "$p" 2>/dev/null
  else
    # BusyBox ps 不支持 -ef，遍历 /proc 找 PPID
    local result=""
    for proc_dir in /proc/[0-9]*/; do
      [ -f "$proc_dir/stat" ] || continue
      local pid=$(basename "$proc_dir")
      # C-29：stat 第 2 段是 "(comm)"，comm 里可能含空格/右括号，
      # 直接 awk '{print $4}' 会错位（ppid 拿到别的字段）→ find_children 漏杀子进程。
      # 先剥掉 "pid (comm) " 前缀再取 ppid（剥壳后字段顺序为 state ppid ...）。
      local rest=$(sed 's/^.*) //' "$proc_dir/stat" 2>/dev/null)
      local ppid=$(echo "$rest" | awk '{print $2}')
      [ "$ppid" = "$p" ] && result="$result $pid"
    done
    echo $result
  fi
}
kt() {
  for c in $(find_children "$1"); do kt "$c"; done
  kill -9 "$1" 2>/dev/null
}

HOST_ALIVE_TIMEOUT=%%HOST_ALIVE_TIMEOUT_SEC%%

log "supervisor started (root=$ROOT, pid=$$)"
rm -f $CTRL/host_alive
echo $$ > $CTRL/alive
# 启动时清理上一轮残留的 shutdown 标记（如果有）
rm -f $CTRL/shutdown
LOOP=0
while true; do
  LOOP=$((LOOP+1))
  T0=$(date +%s%3N 2>/dev/null || date +%s)
  echo $$ > $CTRL/alive

  # ── shutdown ──
  if [ -f $CTRL/shutdown ]; then log "shutdown detected"; rm -f $CTRL/shutdown; break; fi

  # ── 1. 宿主存活检测：host_alive 文件时间戳 ──
  if [ ! -f $CTRL/keep_alive ] && [ -f $CTRL/host_alive ]; then
    n=$(date +%s); h=$(stat -c %Y $CTRL/host_alive 2>/dev/null)
    # C-28：stat 不可用时 h 为空/0，原写法 `|| echo 0` 会让 n-0 恒 >= TIMEOUT →
    # 误判宿主死亡、杀光所有后端并自杀。取不到 mtime 就跳过本轮（fail-open）。
    if [ -z "$h" ] || [ "$h" = "0" ]; then
      log "host_alive mtime unavailable (stat failed), skip this check"
    elif [ $(( n - h )) -ge $HOST_ALIVE_TIMEOUT ]; then
      log "host_alive timeout (>${HOST_ALIVE_TIMEOUT}s), killing all backends and exiting"
      for f in "$CTRL"/pid/*; do
        [ -f "$f" ] || continue
        p=$(cat "$f" 2>/dev/null)
        [ -n "$p" ] && kt "$p"
      done
      break
    fi
  fi

  # ── 2. 插件进程检测：kill -0 检查进程是否存活 ──
  # C-30：kill -0 对僵尸（Z 态）仍返回成功 → 原判据看不到死亡、也不 wait 回收 →
  # 僵尸累积且启动失败无回传。这里按 /proc 状态识别僵尸，与 kill -0 失败一并处理：
  # wait 收割退出码写 rc/<key>.rc（宿主可回读失败原因），同时完成 reap。
  for f in "$CTRL"/pid/*; do
    [ -f "$f" ] || continue
    k=$(basename "$f")
    p=$(cat "$f" 2>/dev/null)
    [ -n "$p" ] || continue
    st=$(sed 's/^.*) //' "/proc/$p/stat" 2>/dev/null | awk '{print $1}')
    if [ "$st" = "Z" ] || [ "$st" = "X" ] || ! kill -0 "$p" 2>/dev/null; then
      rc=0
      wait "$p" 2>/dev/null || rc=$?
      echo "$rc $(date +%s)" > "$CTRL/rc/$k.rc" 2>/dev/null
      log "plugin $k (pid=$p) exited rc=$rc, cleaned up"
      rm -f "$f"
      rm -f "$CTRL/idle/$k"
      rm -f "$CTRL/idle/${k}.start"
    fi
  done

  # ── pickup cmd ──
  CMD_COUNT=0
  for f in $CTRL/cmd/*.cmd; do
    [ -f "$f" ] || continue
    k=$(basename "$f" .cmd)
    rm -f "$CTRL/rc/$k.rc" 2>/dev/null
    TC=$(date +%s%3N 2>/dev/null || date +%s)
    CMD=$(cat "$f" 2>/dev/null)
    mv "$f" "$CTRL/done/" 2>/dev/null
    # 先杀掉同 key 的旧后端进程，避免端口泄漏
    if [ -f "$CTRL/pid/$k" ]; then
      old_pid=$(cat "$CTRL/pid/$k" 2>/dev/null)
      if [ -n "$old_pid" ]; then
        log "CMD kill old: key=$k pid=$old_pid"
        kt "$old_pid"
      fi
    fi
    log "CMD pickup: key=$k"
    # trap "" HUP 防止 supervisor 退出时 SIGHUP 杀后端；CMD 内 exec 让 sh 替换为实际命令（PID 即后端 PID）
    sh -c 'trap "" HUP; '"$CMD" >> "$LOGDIR/backend_$k.log" 2>&1 &
    echo $! > $CTRL/pid/$k
    date +%s > $CTRL/idle/${k}.start
    TE=$(date +%s%3N 2>/dev/null || date +%s)
    log "CMD start: key=$k pid=$! time=$((TE-TC))ms"
    CMD_COUNT=$((CMD_COUNT+1))
  done

  # ── stop backends ──
  for f in "$CTRL"/stop/*; do
    [ -f "$f" ] || continue
    k=$(basename "$f")
    p=$(cat "$CTRL/pid/$k" 2>/dev/null)
    if [ -n "$p" ]; then
      kt $p
      wait "$p" 2>/dev/null   # C-30：收割退出码，避免留下僵尸
      echo "stopped $(date +%s)" > "$CTRL/rc/$k.rc" 2>/dev/null
    fi
    rm -f "$CTRL/pid/$k"
    rm -f "$CTRL/idle/$k"
    rm -f "$CTRL/idle/${k}.start"
    mv "$f" "$CTRL/done/" 2>/dev/null
  done

  # ── 3. 空闲回收：idle/<key> 文件存超时分钟数，用启动时间戳判断 ──
  now=$(date +%s)
  for f in "$CTRL"/pid/*; do
    [ -f "$f" ] || continue
    k=$(basename "$f")
    it=$(cat $CTRL/idle/$k 2>/dev/null || echo 0)
    [ "$it" = "0" ] && continue
    st=$(cat $CTRL/idle/${k}.start 2>/dev/null || echo 0)
    [ "$st" = "0" ] && continue
    if [ $(( now - st )) -ge $(( it * 60 )) ]; then
      log "idle timeout: $k (ran $(( now - st ))s >= ${it}min), killing"
      p=$(cat "$f" 2>/dev/null)
      if [ -n "$p" ]; then
        kt $p
        wait "$p" 2>/dev/null   # C-30：收割退出码，避免留下僵尸
        echo "idle_timeout $(date +%s)" > "$CTRL/rc/$k.rc" 2>/dev/null
      fi
      rm -f "$f"
      rm -f "$CTRL/idle/$k"
      rm -f "$CTRL/idle/${k}.start"
    fi
  done

  # ── cleanup done dir ──
  rm -f "$CTRL"/done/* 2>/dev/null

  # ── cleanup logs older than 1 day (every 60 loops) ──
  if [ $((LOOP % 60)) -eq 0 ]; then
    find "$LOGDIR" -name "*.log" -mmin +1440 -delete 2>/dev/null
    tail -500 "$LOG" > "$LOG.tmp" 2>/dev/null && mv "$LOG.tmp" "$LOG"
    # C-31：backend_*.log 原先只按天删、单日无大小上限（一天刷几百 MB 也照留），
    # 这里按 1MB 封顶，超了只保留末尾 512KB
    for f in "$LOGDIR"/*.log; do
      [ -f "$f" ] || continue
      sz=$(wc -c < "$f" 2>/dev/null)
      [ -n "$sz" ] || continue
      if [ "$sz" -gt 1048576 ]; then
        tail -c 524288 "$f" > "$f.tmp" 2>/dev/null && mv "$f.tmp" "$f"
      fi
    done
  fi

  T1=$(date +%s%3N 2>/dev/null || date +%s)
  log "LOOP#$LOOP time=$((T1-T0))ms cmd=$CMD_COUNT pid=$(ls "$CTRL"/pid 2>/dev/null | wc -l)"
  sleep 1
done

rm -f $CTRL/alive
# kill all child backend processes on exit
for f in "$CTRL"/pid/*; do
  [ -f "$f" ] || continue
  p=$(cat "$f" 2>/dev/null)
  if [ -n "$p" ]; then
    kt "$p"
    wait "$p" 2>/dev/null   # C-30：收割退出码
  fi
done
rm -f "$CTRL"/pid/*
log "supervisor exited"
exit 0
