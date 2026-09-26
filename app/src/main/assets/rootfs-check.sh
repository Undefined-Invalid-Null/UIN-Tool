#!/bin/bash
# Rootfs Diagnostic Script
# Run inside proot to check environment health

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass=0
fail=0
warn=0

check() {
    local desc="$1"
    shift
    if "$@" >/dev/null 2>&1; then
        echo -e "  ${GREEN}[OK]${NC} $desc"
        ((pass++))
    else
        echo -e "  ${RED}[FAIL]${NC} $desc"
        ((fail++))
    fi
}

check_file() {
    local desc="$1"
    local path="$2"
    if [ -f "$path" ]; then
        echo -e "  ${GREEN}[OK]${NC} $desc"
        ((pass++))
    else
        echo -e "  ${RED}[FAIL]${NC} $desc ($path not found)"
        ((fail++))
    fi
}

check_dir() {
    local desc="$1"
    local path="$2"
    if [ -d "$path" ]; then
        echo -e "  ${GREEN}[OK]${NC} $desc"
        ((pass++))
    else
        echo -e "  ${RED}[FAIL]${NC} $desc ($path not found)"
        ((fail++))
    fi
}

check_exec() {
    local desc="$1"
    local path="$2"
    if [ -x "$path" ]; then
        echo -e "  ${GREEN}[OK]${NC} $desc"
        ((pass++))
    else
        echo -e "  ${RED}[FAIL]${NC} $desc ($path not found or not executable)"
        ((fail++))
    fi
}

echo "========================================="
echo "  UIN Tool Rootfs Diagnostic"
echo "========================================="
echo

echo "[1/6] Shell & Core"
check_exec "bash" "/bin/bash"
check_exec "sh" "/bin/sh"
check_exec "ls" "/bin/ls" || check_exec "ls" "/usr/bin/ls"
check_exec "cat" "/bin/cat" || check_exec "cat" "/usr/bin/cat"
check_exec "grep" "/bin/grep" || check_exec "grep" "/usr/bin/grep"
check_exec "env" "/usr/bin/env"

echo
echo "[2/6] Package Manager"
check_exec "dpkg" "/usr/bin/dpkg"
check_exec "apt" "/usr/bin/apt" || check_exec "apt-get" "/usr/bin/apt-get"
check_file "dpkg status" "/var/lib/dpkg/status"
check_dir "dpkg info" "/var/lib/dpkg/info"
check_dir "dpkg triggers" "/var/lib/dpkg/triggers"

echo
echo "[3/6] APT Configuration"
check_dir "apt conf.d" "/etc/apt/apt.conf.d"
check_file "debian version" "/etc/debian_version"

# Check for sources
if [ -f "/etc/apt/sources.list" ]; then
    check_file "sources.list" "/etc/apt/sources.list"
elif [ -d "/etc/apt/sources.list.d" ]; then
    src_count=$(ls /etc/apt/sources.list.d/*.sources /etc/apt/sources.list.d/*.list 2>/dev/null | wc -l)
    if [ "$src_count" -gt 0 ]; then
        echo -e "  ${GREEN}[OK]${NC} sources in sources.list.d ($src_count files)"
        ((pass++))
    else
        echo -e "  ${RED}[FAIL]${NC} no sources found"
        ((fail++))
    fi
else
    echo -e "  ${RED}[FAIL]${NC} no apt sources found"
    ((fail++))
fi

echo
echo "[4/6] System Files"
check_file "resolv.conf" "/etc/resolv.conf"
check_file "hosts" "/etc/hosts"
check_file "passwd" "/etc/passwd"
check_file "group" "/etc/group"

echo
echo "[5/6] Directories & Permissions"
if [ -d "/tmp" ]; then
    if [ -w "/tmp" ]; then
        echo -e "  ${GREEN}[OK]${NC} /tmp (writable)"
        ((pass++))
    else
        echo -e "  ${RED}[FAIL]${NC} /tmp (not writable)"
        ((fail++))
    fi
else
    echo -e "  ${RED}[FAIL]${NC} /tmp (not found)"
    ((fail++))
fi

check_dir "/var/tmp" "/var/tmp"
check_dir "/root" "/root"
check_dir "/proc" "/proc"
check_dir "/sys" "/sys"
check_dir "/dev" "/dev"

echo
echo "[6/6] Network & Locale"
if [ -f "/etc/resolv.conf" ]; then
    ns_count=$(grep -c "^nameserver" /etc/resolv.conf 2>/dev/null || echo 0)
    if [ "$ns_count" -gt 0 ]; then
        echo -e "  ${GREEN}[OK]${NC} DNS nameservers configured ($ns_count)"
        ((pass++))
    else
        echo -e "  ${YELLOW}[WARN]${NC} /etc/resolv.conf has no nameserver"
        ((warn++))
    fi
else
    echo -e "  ${RED}[FAIL]${NC} no DNS configuration"
    ((fail++))
fi

check "locale" locale -a 2>/dev/null | grep -qi "C.UTF-8\|en_US.UTF-8"

echo
echo "========================================="
echo -e "  Results: ${GREEN}$pass passed${NC}, ${RED}$fail failed${NC}, ${YELLOW}$warn warnings${NC}"
echo "========================================="

if [ $fail -gt 0 ]; then
    echo
    echo "Fix suggestions:"
    [ ! -f "/etc/apt/apt.conf.d/99proot-nosandbox" ] && echo "  apt: mkdir -p /etc/apt/apt.conf.d && echo 'APT::Sandbox::User \"root\";' > /etc/apt/apt.conf.d/99proot-nosandbox"
    [ ! -f "/etc/resolv.conf" ] && echo "  dns: echo 'nameserver 8.8.8.8' > /etc/resolv.conf"
    [ ! -f "/etc/hosts" ] && echo "  hosts: echo '127.0.0.1 localhost' > /etc/hosts"
fi

exit $fail
