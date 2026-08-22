#!/bin/sh
# UIN shared backend supervisor (root passed as $1)
ROOT=$1
CTRL=$ROOT/.uin
LOGDIR=$CTRL/logs
LOG=$LOGDIR/supervisor.log
mkdir -p $CTRL/cmd $CTRL/pid $CTRL/stop $CTRL/idle $CTRL/done $LOGDIR

log() { echo "[$(date '+%H:%M:%S')] $*" >> "$LOG"; }
# 递归杀进程树：pgrep 不可用时（Alpine BusyBox）用 /proc 兜底
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
      local ppid=$(awk '{print $4}' "$proc_dir/stat" 2>/dev/null)
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
    n=$(date +%s); h=$(stat -c %Y $CTRL/host_alive 2>/dev/null || echo 0)
    if [ $(( n - h )) -ge $HOST_ALIVE_TIMEOUT ]; then
      log "host_alive timeout (>${HOST_ALIVE_TIMEOUT}s), killing all backends and exiting"
      for f in $CTRL/pid/*; do
        [ -f "$f" ] || continue
        p=$(cat "$f" 2>/dev/null)
        [ -n "$p" ] && kt "$p"
      done
      break
    fi
  fi

  # ── 2. 插件进程检测：kill -0 检查进程是否存活 ──
  for f in $CTRL/pid/*; do
    [ -f "$f" ] || continue
    k=$(basename "$f")
    p=$(cat "$f" 2>/dev/null)
    if [ -n "$p" ] && ! kill -0 "$p" 2>/dev/null; then
      log "plugin $k (pid=$p) dead, cleaning up"
      rm -f "$f"
      rm -f "$CTRL/idle/$k"
    fi
  done

  # ── pickup cmd ──
  CMD_COUNT=0
  for f in $CTRL/cmd/*.cmd; do
    [ -f "$f" ] || continue
    k=$(basename "$f" .cmd)
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
  for f in $CTRL/stop/*; do
    [ -f "$f" ] || continue
    k=$(basename "$f")
    p=$(cat $CTRL/pid/$k 2>/dev/null)
    [ -n "$p" ] && kt $p
    rm -f "$CTRL/pid/$k"
    rm -f "$CTRL/idle/$k"
    rm -f "$CTRL/idle/${k}.start"
    mv "$f" "$CTRL/done/" 2>/dev/null
  done

  # ── 3. 空闲回收：idle/<key> 文件存超时分钟数，用启动时间戳判断 ──
  now=$(date +%s)
  for f in $CTRL/pid/*; do
    [ -f "$f" ] || continue
    k=$(basename "$f")
    it=$(cat $CTRL/idle/$k 2>/dev/null || echo 0)
    [ "$it" = "0" ] && continue
    st=$(cat $CTRL/idle/${k}.start 2>/dev/null || echo 0)
    [ "$st" = "0" ] && continue
    if [ $(( now - st )) -ge $(( it * 60 )) ]; then
      log "idle timeout: $k (ran $(( now - st ))s >= ${it}min), killing"
      p=$(cat "$f" 2>/dev/null)
      [ -n "$p" ] && kt $p
      rm -f "$f"
      rm -f "$CTRL/idle/$k"
      rm -f "$CTRL/idle/${k}.start"
    fi
  done

  # ── cleanup done dir ──
  rm -f $CTRL/done/* 2>/dev/null

  # ── cleanup logs older than 1 day (every 60 loops) ──
  if [ $((LOOP % 60)) -eq 0 ]; then
    find "$LOGDIR" -name "*.log" -mmin +1440 -delete 2>/dev/null
    tail -500 "$LOG" > "$LOG.tmp" 2>/dev/null && mv "$LOG.tmp" "$LOG"
  fi

  T1=$(date +%s%3N 2>/dev/null || date +%s)
  log "LOOP#$LOOP time=$((T1-T0))ms cmd=$CMD_COUNT pid=$(ls $CTRL/pid 2>/dev/null | wc -l)"
  sleep 1
done

rm -f $CTRL/alive
# kill all child backend processes on exit
for f in $CTRL/pid/*; do
  [ -f "$f" ] || continue
  p=$(cat "$f" 2>/dev/null)
  [ -n "$p" ] && kt "$p"
done
rm -f $CTRL/pid/*
log "supervisor exited"
exit 0
