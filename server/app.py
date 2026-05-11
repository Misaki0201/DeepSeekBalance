"""
DeepSeek 余额监控 - 服务器代理

在服务器上运行此服务，Android App 通过它来查询余额和用量。
支持代理 DeepSeek 聊天补全 API 并自动追踪 Token 消耗。

部署方式:
    1. 将 DEEPSEEK_API_KEY 填入 .env 文件
    2. pip install -r requirements.txt
    3. python app.py  (开发模式)
    4. 或 gunicorn -w 2 -b 0.0.0.0:5000 app:app (生产模式)
"""

import os
import json
import time
import threading
from datetime import datetime, date
from functools import wraps

import requests
from dotenv import load_dotenv
from flask import Flask, jsonify, request, Response, stream_with_context

load_dotenv()

app = Flask(__name__)

# 配置
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = "https://api.deepseek.com"
ACCESS_TOKEN = os.getenv("ACCESS_TOKEN", "")
CACHE_TTL = int(os.getenv("CACHE_TTL", "60"))
USAGE_FILE = os.getenv("USAGE_FILE", "usage_data.json")

# 内存缓存
_cache = {"data": None, "timestamp": 0}


# ============================================================
# 用量追踪
# ============================================================

_usage_lock = threading.Lock()
_usage_data = {
    "records": [],      # 按天记录
    "daily_totals": {}  # { "2026-05-11": { "tokens": ..., "cost": ... } }
}


def _load_usage():
    """从文件加载用量数据"""
    global _usage_data
    try:
        if os.path.exists(USAGE_FILE):
            with open(USAGE_FILE, "r") as f:
                _usage_data = json.load(f)
    except Exception:
        _usage_data = {"records": [], "daily_totals": {}}


def _save_usage():
    """将用量数据保存到文件"""
    try:
        with open(USAGE_FILE, "w") as f:
            json.dump(_usage_data, f, indent=2)
    except Exception as e:
        print(f"[WARN] 保存用量数据失败: {e}")


def _track_usage(prompt_tokens: int, completion_tokens: int, total_tokens: int, model: str):
    """记录一次 API 调用的 Token 消耗"""
    today = date.today().isoformat()
    now = datetime.now().isoformat()

    with _usage_lock:
        # 追加记录
        _usage_data.setdefault("records", []).append({
            "date": today,
            "timestamp": now,
            "model": model,
            "prompt_tokens": prompt_tokens,
            "completion_tokens": completion_tokens,
            "total_tokens": total_tokens,
        })

        # 更新日汇总
        _usage_data.setdefault("daily_totals", {})
        if today not in _usage_data["daily_totals"]:
            _usage_data["daily_totals"][today] = {
                "total_tokens": 0,
                "total_prompt": 0,
                "total_completion": 0,
                "total_cost_cents": 0.0,
                "call_count": 0,
            }

        dt = _usage_data["daily_totals"][today]
        dt["total_tokens"] += total_tokens
        dt["total_prompt"] += prompt_tokens
        dt["total_completion"] += completion_tokens
        dt["call_count"] += 1

        # 按 DeepSeek 价格估算费用（分）
        # 输入: ¥2/百万 tokens, 输出: ¥8/百万 tokens
        input_cost_cents = (prompt_tokens / 1_000_000) * 200   # 2元 = 200分
        output_cost_cents = (completion_tokens / 1_000_000) * 800  # 8元 = 800分
        dt["total_cost_cents"] += input_cost_cents + output_cost_cents

        # 只保留最近90天的记录
        cutoff = (date.today().isoformat(),)
        _usage_data["records"] = [
            r for r in _usage_data["records"]
            if r["date"] >= (date.today().replace(year=date.today().year - 1).isoformat())
        ]

    _save_usage()


def _get_usage_stats():
    """获取聚合后的用量统计"""
    today = date.today().isoformat()
    daily = _usage_data.get("daily_totals", {})

    # 今日数据
    today_data = daily.get(today, {})
    today_tokens = today_data.get("total_tokens", 0)
    today_prompt = today_data.get("total_prompt", 0)
    today_completion = today_data.get("total_completion", 0)
    today_cost = today_data.get("total_cost_cents", 0.0) / 100  # 分转元

    # 累计数据
    total_tokens = sum(d.get("total_tokens", 0) for d in daily.values())
    total_cost = sum(d.get("total_cost_cents", 0.0) for d in daily.values()) / 100

    return {
        "todayTokens": today_tokens,
        "todayPrompt": today_prompt,
        "todayCompletion": today_completion,
        "todayCost": round(today_cost, 6),
        "totalTokens": total_tokens,
        "totalCost": round(total_cost, 2),
        "dailyTotals": daily,
    }


# 启动时加载已有数据
_load_usage()


# ============================================================
# 认证装饰器
# ============================================================

def require_token(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if ACCESS_TOKEN:
            token = request.headers.get("Authorization", "").replace("Bearer ", "")
            if token != ACCESS_TOKEN:
                return jsonify({"error": "Unauthorized"}), 401
        return f(*args, **kwargs)
    return decorated


# ============================================================
# API 路由
# ============================================================

@app.route("/")
def index():
    return jsonify({
        "service": "DeepSeek Balance Proxy",
        "version": "2.0.0",
        "endpoints": {
            "GET  /api/health": "健康检查",
            "GET  /api/balance": "获取 DeepSeek 账户余额",
            "GET  /api/usage-stats": "获取用量统计数据",
            "POST /api/chat/completions": "代理聊天补全 API（自动追踪用量）",
        }
    })


@app.route("/api/health")
def health():
    return jsonify({
        "status": "ok",
        "has_api_key": bool(DEEPSEEK_API_KEY),
        "cache_ttl": CACHE_TTL,
        "usage_records": len(_usage_data.get("records", [])),
    })


@app.route("/api/balance")
@require_token
def get_balance():
    if not DEEPSEEK_API_KEY:
        return jsonify({"error": "服务器未配置 DEEPSEEK_API_KEY"}), 500

    now = time.time()
    if _cache["data"] and (now - _cache["timestamp"]) < CACHE_TTL:
        return jsonify(_cache["data"])

    headers = {
        "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
        "Accept": "application/json"
    }

    try:
        response = requests.get(
            f"{DEEPSEEK_BASE_URL}/user/balance",
            headers=headers,
            timeout=15
        )
        response.raise_for_status()
        data = response.json()
        _cache["data"] = data
        _cache["timestamp"] = now
        return jsonify(data)

    except requests.exceptions.HTTPError as e:
        status_code = e.response.status_code if e.response else 500
        error_msg = {
            401: "API Key 无效",
            403: "API Key 无权限",
            429: "请求过于频繁",
        }.get(status_code, f"DeepSeek API 错误: {status_code}")
        return jsonify({"error": error_msg}), status_code
    except requests.exceptions.Timeout:
        return jsonify({"error": "请求 DeepSeek API 超时"}), 504
    except requests.exceptions.RequestException as e:
        return jsonify({"error": f"请求失败: {str(e)}"}), 502


@app.route("/api/usage-stats")
@require_token
def get_usage_stats():
    """获取用量统计（供 Android App 使用）"""
    if not DEEPSEEK_API_KEY:
        return jsonify({"error": "服务器未配置 DEEPSEEK_API_KEY"}), 500
    return jsonify(_get_usage_stats())


@app.route("/api/chat/completions", methods=["POST"])
@require_token
def proxy_chat_completions():
    """
    代理 DeepSeek 聊天补全 API，自动追踪 Token 消耗。
    客户端将 API 请求发到此端点，服务器转发到 DeepSeek 并记录用量。
    """
    if not DEEPSEEK_API_KEY:
        return jsonify({"error": "服务器未配置 DEEPSEEK_API_KEY"}), 500

    # 转发请求到 DeepSeek
    headers = {
        "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
        "Content-Type": "application/json",
    }

    body = request.get_json()
    is_stream = body and body.get("stream", False)

    try:
        resp = requests.post(
            f"{DEEPSEEK_BASE_URL}/v1/chat/completions",
            headers=headers,
            json=body,
            stream=is_stream,
            timeout=120,
        )

        if not resp.ok:
            return jsonify(resp.json()), resp.status_code

        # 处理流式响应
        if is_stream:
            def generate():
                for chunk in resp.iter_lines(decode_unicode=True):
                    if chunk:
                        yield chunk + "\n\n"
            return Response(stream_with_context(generate()), content_type=resp.headers.get("content-type"))

        # 非流式响应 - 提取用量并追踪
        data = resp.json()

        if "usage" in data:
            usage = data["usage"]
            _track_usage(
                prompt_tokens=usage.get("prompt_tokens", 0),
                completion_tokens=usage.get("completion_tokens", 0),
                total_tokens=usage.get("total_tokens", 0),
                model=data.get("model", "unknown"),
            )

        return jsonify(data)

    except requests.exceptions.Timeout:
        return jsonify({"error": "请求 DeepSeek API 超时"}), 504
    except requests.exceptions.RequestException as e:
        return jsonify({"error": f"请求失败: {str(e)}"}), 502


if __name__ == "__main__":
    port = int(os.getenv("PORT", "5000"))
    debug = os.getenv("DEBUG", "false").lower() == "true"

    print(f"DeepSeek Balance Proxy Server v2.0.0")
    print(f"  API Key 已配置: {'是' if DEEPSEEK_API_KEY else '否'}")
    print(f"  Access Token: {'已启用' if ACCESS_TOKEN else '未启用'}")
    print(f"  缓存 TTL: {CACHE_TTL}秒")
    print(f"  已记录用量: {len(_usage_data.get('records', []))} 条")
    print(f"  监听端口: {port}")
    print()

    app.run(host="0.0.0.0", port=port, debug=debug)
