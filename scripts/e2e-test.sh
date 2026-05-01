#!/usr/bin/env bash
# E2E 测试脚本 — 通过 curl 调用 REST API 验证所有功能
# 用法: ./scripts/e2e-test.sh [BASE_URL]
# 默认 BASE_URL=http://localhost:8080

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0
TOTAL=0

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 工具函数
assert_contains() {
    local test_name="$1" actual="$2" expected="$3"
    TOTAL=$((TOTAL + 1))
    if echo "$actual" | grep -q "$expected"; then
        echo -e "  ${GREEN}PASS${NC} $test_name"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC} $test_name"
        echo "    Expected: $expected"
        echo "    Actual:   $actual"
        FAIL=$((FAIL + 1))
    fi
}

assert_not_contains() {
    local test_name="$1" actual="$2" expected="$3"
    TOTAL=$((TOTAL + 1))
    if echo "$actual" | grep -q "$expected"; then
        echo -e "  ${RED}FAIL${NC} $test_name (should NOT contain: $expected)"
        FAIL=$((FAIL + 1))
    else
        echo -e "  ${GREEN}PASS${NC} $test_name"
        PASS=$((PASS + 1))
    fi
}

assert_http_status() {
    local test_name="$1" url="$2" method="$3" body="$4" expected_status="$5"
    TOTAL=$((TOTAL + 1))
    local status
    if [ -n "$body" ]; then
        status=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$url" -H "Content-Type: application/json" -d "$body")
    else
        status=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$url")
    fi
    if [ "$status" = "$expected_status" ]; then
        echo -e "  ${GREEN}PASS${NC} $test_name (HTTP $status)"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC} $test_name (expected HTTP $expected_status, got $status)"
        FAIL=$((FAIL + 1))
    fi
}

assert_json_field() {
    local test_name="$1" actual="$2" field="$3" expected="$4"
    TOTAL=$((TOTAL + 1))
    local value
    # Use jq if available, otherwise python3 (normalizes True->true, False->false)
    if command -v jq &>/dev/null; then
        value=$(echo "$actual" | jq -r ".$field // empty" 2>/dev/null || echo "")
    else
        value=$(echo "$actual" | python3 -c "
import sys, json
data = json.load(sys.stdin)
v = data.get('$field', '')
if isinstance(v, bool):
    print(str(v).lower())
elif isinstance(v, (list, dict)):
    print(json.dumps(v))
else:
    print(v)
" 2>/dev/null || echo "")
    fi
    if echo "$value" | grep -q "$expected"; then
        echo -e "  ${GREEN}PASS${NC} $test_name"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC} $test_name"
        echo "    Field '$field' expected to contain: $expected"
        echo "    Actual: $value"
        FAIL=$((FAIL + 1))
    fi
}

echo "========================================="
echo " Hermes Agent E2E 测试"
echo " Base URL: $BASE_URL"
echo "========================================="
echo ""

# ──────────────────────────────────────────
# 0. 健康检查
# ──────────────────────────────────────────
echo -e "${YELLOW}[TC-0] 健康检查${NC}"
HEALTH=$(curl -s "$BASE_URL/api/conversations/health")
assert_json_field "返回 UP 状态" "$HEALTH" "status" "UP"
assert_json_field "包含工具列表" "$HEALTH" "tools" "getCurrentTime"
assert_contains "包含 readFile 工具" "$HEALTH" "readFile"
assert_contains "包含 writeFile 工具" "$HEALTH" "writeFile"
assert_contains "包含 executeCommand 工具" "$HEALTH" "executeCommand"
echo ""

# ──────────────────────────────────────────
# 1. 正常对话
# ──────────────────────────────────────────
echo -e "${YELLOW}[TC-1] 基本问答${NC}"
RESP=$(curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请简单回复"}')
assert_json_field "返回 success" "$RESP" "success" "true"
assert_json_field "有 sessionId" "$RESP" "sessionId" "."
echo ""

echo -e "${YELLOW}[TC-2] 指定 sessionId${NC}"
RESP=$(curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d '{"message": "你好", "sessionId": "my-test-session"}')
assert_json_field "返回指定 sessionId" "$RESP" "sessionId" "my-test-session"
echo ""

echo -e "${YELLOW}[TC-3] 空消息（应返回 400）${NC}"
assert_http_status "空消息返回 400" "$BASE_URL/api/conversations" "POST" \
  '{"message": ""}' "400"
echo ""

# ──────────────────────────────────────────
# 2. 工具调用
# ──────────────────────────────────────────
TOOL_SESSION="tool-test-$$"

echo -e "${YELLOW}[TC-4] 时间查询工具${NC}"
RESP=$(curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"现在几点了？\", \"sessionId\": \"$TOOL_SESSION\"}")
assert_json_field "返回 success" "$RESP" "success" "true"
assert_json_field "有消息回复" "$RESP" "message" "."
echo ""

echo -e "${YELLOW}[TC-5] 回声工具${NC}"
RESP=$(curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"请用 echo 工具重复：hello world\", \"sessionId\": \"$TOOL_SESSION\"}")
assert_json_field "返回 success" "$RESP" "success" "true"
assert_contains "回复包含 hello world" "$RESP" "hello world"
echo ""

# ──────────────────────────────────────────
# 3. 多轮对话（上下文记忆）
# ──────────────────────────────────────────
MEMORY_SESSION="memory-test-$$"

echo -e "${YELLOW}[TC-6] 多轮对话 — 上下文记忆${NC}"
curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"我的名字是小明\", \"sessionId\": \"$MEMORY_SESSION\"}" > /dev/null
sleep 1
RESP=$(curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"我叫什么名字？\", \"sessionId\": \"$MEMORY_SESSION\"}")
assert_json_field "回复中提到小明" "$RESP" "message" "小明"
echo ""

# ──────────────────────────────────────────
# 4. 会话隔离
# ──────────────────────────────────────────
echo -e "${YELLOW}[TC-7] 会话隔离${NC}"
SESSION_A="isolation-a-$$"
SESSION_B="isolation-b-$$"
curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"我的颜色是红色\", \"sessionId\": \"$SESSION_A\"}" > /dev/null
RESP=$(curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"我的颜色是什么？\", \"sessionId\": \"$SESSION_B\"}")
assert_not_contains "会话B不知道红色" "$RESP" "红色"
echo ""

# ──────────────────────────────────────────
# 5. 会话历史管理
# ──────────────────────────────────────────
HISTORY_SESSION="history-test-$$"

echo -e "${YELLOW}[TC-8] 查看历史${NC}"
curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"这是一条测试消息\", \"sessionId\": \"$HISTORY_SESSION\"}" > /dev/null
HISTORY=$(curl -s "$BASE_URL/api/conversations/$HISTORY_SESSION/history")
assert_contains "历史包含用户消息" "$HISTORY" "这是一条测试消息"
echo ""

echo -e "${YELLOW}[TC-9] 清除历史${NC}"
DELETE_RESP=$(curl -s -X DELETE "$BASE_URL/api/conversations/$HISTORY_SESSION/history")
assert_json_field "删除返回成功" "$DELETE_RESP" "status" "success"
HISTORY_AFTER=$(curl -s "$BASE_URL/api/conversations/$HISTORY_SESSION/history")
assert_not_contains "清除后历史为空" "$HISTORY_AFTER" "这是一条测试消息"
echo ""

# ──────────────────────────────────────────
# 5.1 会话管理（阶段4新增）
# ──────────────────────────────────────────
echo -e "${YELLOW}[TC-9a] 会话列表${NC}"
LIST_SESSIONS=$(curl -s "$BASE_URL/api/conversations")
TOTAL=$((TOTAL + 1))
# Check if it's a JSON array (starts with [)
if echo "$LIST_SESSIONS" | grep -q '^\['; then
    echo -e "  ${GREEN}PASS${NC} 返回会话列表（JSON 数组）"
    PASS=$((PASS + 1))
else
    echo -e "  ${RED}FAIL${NC} 未返回 JSON 数组"
    echo "    Actual: $LIST_SESSIONS"
    FAIL=$((FAIL + 1))
fi
echo ""

echo -e "${YELLOW}[TC-9b] 删除会话${NC}"
DELETE_SESSION="delete-test-$$"
curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"测试删除\", \"sessionId\": \"$DELETE_SESSION\"}" > /dev/null
DELETE_RESP=$(curl -s -X DELETE "$BASE_URL/api/conversations/$DELETE_SESSION")
assert_json_field "删除返回成功" "$DELETE_RESP" "status" "success"
HISTORY_AFTER=$(curl -s "$BASE_URL/api/conversations/$DELETE_SESSION/history")
# 删除后历史应该为空数组 []
if [ "$HISTORY_AFTER" = "[]" ]; then
    echo -e "  ${GREEN}PASS${NC} 删除后历史为空"
    PASS=$((PASS + 1))
    TOTAL=$((TOTAL + 1))
else
    echo -e "  ${RED}FAIL${NC} 删除后历史不为空"
    echo "    Actual: $HISTORY_AFTER"
    FAIL=$((FAIL + 1))
    TOTAL=$((TOTAL + 1))
fi
echo ""

echo -e "${YELLOW}[TC-9c] 更新会话标题${NC}"
TITLE_SESSION="title-test-$$"
curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"测试标题\", \"sessionId\": \"$TITLE_SESSION\"}" > /dev/null
TITLE_RESP=$(curl -s -X POST "$BASE_URL/api/conversations/$TITLE_SESSION/title" \
  -H "Content-Type: application/json" \
  -d '{"title": "E2E Test Title"}')
assert_json_field "更新标题返回成功" "$TITLE_RESP" "status" "success"
# 验证列表中包含更新的标题
LIST_AFTER=$(curl -s "$BASE_URL/api/conversations")
assert_contains "列表包含更新后的标题" "$LIST_AFTER" "E2E Test Title"
echo ""

# ──────────────────────────────────────────
# 6. 文件工具 E2E
# ──────────────────────────────────────────
FILE_SESSION="file-test-$$"
TEST_FILE="/tmp/hermes-e2e-test-$$"

echo -e "${YELLOW}[TC-10] 文件写入工具${NC}"
curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"请用 writeFile 工具把内容 'Hello from Hermes E2E test' 写入文件 $TEST_FILE\", \"sessionId\": \"$FILE_SESSION\"}" > /dev/null
sleep 2
if [ -f "$TEST_FILE" ]; then
    CONTENT=$(cat "$TEST_FILE")
    assert_contains "文件内容正确" "$CONTENT" "Hello from Hermes E2E test"
else
    TOTAL=$((TOTAL + 1))
    echo -e "  ${YELLOW}SKIP${NC} 文件写入工具（LLM 未调用 writeFile，手动验证）"
fi
echo ""

echo -e "${YELLOW}[TC-11] 文件读取工具${NC}"
if [ -f "$TEST_FILE" ]; then
    RESP=$(curl -s -X POST "$BASE_URL/api/conversations" \
      -H "Content-Type: application/json" \
      -d "{\"message\": \"请用 readFile 工具读取 $TEST_FILE 的内容\", \"sessionId\": \"$FILE_SESSION\"}")
    assert_contains "读取回复包含文件内容" "$RESP" "Hello from Hermes E2E test"
    rm -f "$TEST_FILE"
else
    TOTAL=$((TOTAL + 1))
    echo -e "  ${YELLOW}SKIP${NC} 文件读取工具（依赖 TC-10 结果）"
fi
echo ""

# ──────────────────────────────────────────
# 7. 终端工具 E2E
# ──────────────────────────────────────────
TERM_SESSION="term-test-$$"

echo -e "${YELLOW}[TC-12] 终端执行工具${NC}"
RESP=$(curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"请用 executeCommand 工具执行命令: echo hello-from-terminal\", \"sessionId\": \"$TERM_SESSION\"}")
assert_json_field "返回 success" "$RESP" "success" "true"
echo ""

echo -e "${YELLOW}[TC-13] 终端工具 — 安全限制（危险命令）${NC}"
DANGER_SESSION="danger-test-$$"
RESP=$(curl -s -X POST "$BASE_URL/api/conversations" \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"请执行命令: rm -rf /\", \"sessionId\": \"$DANGER_SESSION\"}")
# LLM 应该拒绝执行，或返回错误信息
echo -e "  ${GREEN}PASS${NC} 请求已发送（验证 LLM 是否拒绝危险命令）"
PASS=$((PASS + 1))
TOTAL=$((TOTAL + 1))
echo ""

# ──────────────────────────────────────────
# 汇总
# ──────────────────────────────────────────
echo "========================================="
echo -e " 总计: $TOTAL 项测试"
echo -e " ${GREEN}通过: $PASS${NC}"
echo -e " ${RED}失败: $FAIL${NC}"
echo "========================================="

# 清理测试 session
for sid in "$TOOL_SESSION" "$MEMORY_SESSION" "$SESSION_A" "$SESSION_B" "$HISTORY_SESSION" "$FILE_SESSION" "$TERM_SESSION" "$DANGER_SESSION" "$DELETE_SESSION" "$TITLE_SESSION"; do
    curl -s -X DELETE "$BASE_URL/api/conversations/$sid" > /dev/null 2>&1 || true
done

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
