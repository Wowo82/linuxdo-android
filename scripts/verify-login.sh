#!/usr/bin/env bash
# 本地登录链路验证（不含任何凭据；账号只从终端读取）。
#
# 用法：
#   ./scripts/verify-login.sh [站点根地址]
#
# 默认 https://linux.do。只做只读验证：
#   1. POST /session（密码；若账号开启 2FA，会再提交一次验证码）
#   2. GET  /session/current.json
#   3. GET  /session/csrf.json
#   4. GET  /latest.json?page=0&per_page=5
#
# 不会发帖、点赞、改资料。输出已脱敏：不打印账号、密码、cookie、token、帖子标题。
# 临时 cookie / 响应文件在退出时删除。
set -euo pipefail
umask 077

BASE="${1:-https://linux.do}"
BASE="${BASE%/}"
if [[ "$BASE" != https://* ]]; then
    if [[ "${VERIFY_LOGIN_ALLOW_HTTP:-0}" != "1" ]]; then
        echo "拒绝非 HTTPS 地址（避免凭据明文传输）：$BASE" >&2
        exit 1
    fi
fi

for cmd in curl python3 awk; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "缺少依赖：$cmd" >&2; exit 1; }
done

TMP="$(mktemp -d)"
COOKIE="$TMP/cookies.txt"
BODY="$TMP/body.json"
cleanup() {
    rm -rf "$TMP"
    unset LOGIN PASSWORD CODE || true
}
trap cleanup EXIT

read -rp "用户名或邮箱: " LOGIN
read -rsp "密码（输入不回显）: " PASSWORD
echo

# 打印脱敏摘要；最后一行 result= 供脚本分支使用
parse_login_response() {
    python3 - "$BODY" "$1" <<'PY'
import json, sys

path, code = sys.argv[1], sys.argv[2]
raw = ""
try:
    with open(path, encoding="utf-8", errors="replace") as f:
        raw = f.read()
except OSError:
    pass

try:
    data = json.loads(raw) if raw else None
except Exception:
    data = None

if not isinstance(data, dict):
    print("result=unknown")
    print(f"  HTTP {code}，响应不是 JSON（{len(raw)} bytes）")
    raise SystemExit

flags = {k: data.get(k) is True for k in ("totp_enabled", "backup_enabled", "security_key_enabled")}
reason = data.get("reason")
known_reasons = {
    "invalid_credentials", "invalid_second_factor", "invalid_second_factor_method",
    "not_enabled_second_factor_method", "invalid_security_key",
    "challenge_not_found_or_expired", "expired", "rate_limited",
}
if data.get("second_factor_challenge_nonce"):
    result = "nonce"
elif any(flags.values()) or reason in {"invalid_second_factor", "invalid_second_factor_method"}:
    result = "second_factor"
elif data.get("error") or data.get("success") is False:
    result = "failure"
else:
    result = "success"

print(f"result={result}")
print(f"  HTTP {code}")
print(f"  error: {'present' if data.get('error') else 'none'}")
if reason:
    print(f"  reason: {reason if reason in known_reasons else 'other'}")
for key, value in flags.items():
    if key in data:
        print(f"  {key}: {'true' if value else 'false'}")
print(f"  nonce_challenge: {'yes' if data.get('second_factor_challenge_nonce') else 'no'}")
PY
}

post_login() { # $1=second_factor_method $2=second_factor_token（可为空）
    local method="$1" token="${2:-}" body code out
    body="$(printf '%s\n%s\n%s\n%s\n' "$LOGIN" "$PASSWORD" "$method" "$token" | python3 -c '
import sys, urllib.parse
parts = sys.stdin.read().split("\n")
login, password, method, token = (parts + [""] * 4)[:4]
fields = {"login": login, "password": password, "second_factor_method": method}
if token:
    fields["second_factor_token"] = token
print(urllib.parse.urlencode(fields))
')"
    code="$(printf '%s' "$body" | curl -sS -m 30 \
        -o "$BODY" -w '%{http_code}' \
        -c "$COOKIE" -b "$COOKIE" \
        -H 'Content-Type: application/x-www-form-urlencoded' \
        -H 'Accept: application/json' \
        -H 'X-Requested-With: XMLHttpRequest' \
        -H 'Discourse-Logged-In: true' \
        -H "Origin: $BASE" -H "Referer: $BASE/" \
        --data-binary @- "$BASE/session" 2>/dev/null || echo "000")"
    out="$(parse_login_response "$code")"
    printf '%s\n' "$out"
}

result_of() {
    printf '%s\n' "$1" | awk -F= '/^result=/{print $2; exit}'
}

flag_of() {
    printf '%s\n' "$1" | awk -F': ' -v k="$2" '$1 ~ k"$" {print $2; exit}'
}

OUT="$(post_login 1)"
printf '%s\n' "$OUT"
RESULT="$(result_of "$OUT")"

if [[ "$RESULT" == "second_factor" ]]; then
    TOTP="$(flag_of "$OUT" "totp_enabled")"
    BACKUP="$(flag_of "$OUT" "backup_enabled")"

    if [[ "$TOTP" != "true" && "$BACKUP" != "true" ]]; then
        echo "该账号只启用了安全密钥/Passkey：原生密码流程无法完成，请在 App 里用「使用其他方式登录」。" >&2
        exit 3
    fi

    METHOD=1
    if [[ "$TOTP" != "true" && "$BACKUP" == "true" ]]; then
        METHOD=2
    elif [[ "$TOTP" == "true" && "$BACKUP" == "true" ]]; then
        read -rp "验证方式（1=验证器代码，2=备用码）[1]: " CHOICE
        [[ "${CHOICE:-1}" == "2" ]] && METHOD=2
    fi

    read -rsp "两步验证码（输入不回显）: " CODE
    echo

    OUT2="$(post_login "$METHOD" "$CODE")"
    printf '%s\n' "$OUT2"
    RESULT2="$(result_of "$OUT2")"
    if [[ "$RESULT2" != "success" ]]; then
        echo "两步验证未通过。" >&2
        exit 2
    fi
elif [[ "$RESULT" == "nonce" ]]; then
    echo "该服务器使用新版 nonce 两步验证挑战（原生流程未覆盖），请在 App 里用「使用其他方式登录」。" >&2
    exit 3
elif [[ "$RESULT" != "success" ]]; then
    echo "登录失败。" >&2
    echo "提示：若站点关闭了本地登录或要求 SSO，请在 App 里用「使用其他方式登录」。" >&2
    exit 2
fi

if awk -F'\t' '$6=="_t"{found=1} END{exit found?0:1}' "$COOKIE"; then
    echo "cookie: _t present"
else
    echo "cookie: _t MISSING" >&2
    exit 4
fi

get_json() { # $1=path，返回 HTTP 码；响应写入 $BODY
    curl -sS -m 30 -o "$BODY" -w '%{http_code}' \
        -b "$COOKIE" -c "$COOKIE" \
        -H 'Accept: application/json' \
        -H 'X-Requested-With: XMLHttpRequest' \
        -H 'Discourse-Logged-In: true' \
        "$BASE$1" 2>/dev/null || echo "000"
}

CODE="$(get_json '/session/current.json')"
python3 - "$BODY" "$CODE" <<'PY'
import json, sys
path, code = sys.argv[1], sys.argv[2]
user = False
try:
    data = json.load(open(path, encoding="utf-8", errors="replace"))
    user = isinstance(data, dict) and isinstance(data.get("current_user"), dict)
except Exception:
    pass
print(f"/session/current.json: HTTP {code}, current_user={'present' if user else 'absent'}")
PY

CODE="$(get_json '/session/csrf.json')"
python3 - "$BODY" "$CODE" <<'PY'
import json, sys
path, code = sys.argv[1], sys.argv[2]
token = False
try:
    data = json.load(open(path, encoding="utf-8", errors="replace"))
    token = isinstance(data, dict) and bool(data.get("csrf"))
except Exception:
    pass
print(f"/session/csrf.json: HTTP {code}, csrf={'present' if token else 'absent'}")
PY

CODE="$(get_json '/latest.json?page=0&per_page=5')"
python3 - "$BODY" "$CODE" <<'PY'
import json, sys
path, code = sys.argv[1], sys.argv[2]
count = 0
try:
    data = json.load(open(path, encoding="utf-8", errors="replace"))
    count = len(((data.get("topic_list") or {}).get("topics")) or [])
except Exception:
    pass
print(f"/latest.json: HTTP {code}, topics={count}")
PY

echo "完成：只读验证，未发帖 / 未点赞 / 未改资料；临时 cookie 已删除。"
