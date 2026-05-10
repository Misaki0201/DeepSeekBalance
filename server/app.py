"""
DeepSeek 余额监控 - 服务器代理

简单的 Flask 服务器，代理 DeepSeek 余额查询 API。
在服务器上运行此服务，Android App 通过它来查询余额，
这样 API Key 只需要存储在服务器上，手机端只需要知道服务器地址即可。

部署方式:
    1. 将 DEEPSEEK_API_KEY 填入 .env 文件
    2. pip install -r requirements.txt
    3. python app.py  (开发模式)
    4. 或 gunicorn -w 2 -b 0.0.0.0:5000 app:app (生产模式)
"""

import os
import time
from functools import wraps

import requests
from dotenv import load_dotenv
from flask import Flask, jsonify, request

load_dotenv()

app = Flask(__name__)

# 配置
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BALANCE_URL = "https://api.deepseek.com/user/balance"
ACCESS_TOKEN = os.getenv("ACCESS_TOKEN", "")  # 可选：App 访问本服务的令牌
CACHE_TTL = int(os.getenv("CACHE_TTL", "60"))  # 缓存时间（秒），默认 1 分钟

# 简单内存缓存
_cache = {"data": None, "timestamp": 0}


def require_token(f):
    """简单的 Token 验证装饰器"""
    @wraps(f)
    def decorated(*args, **kwargs):
        if ACCESS_TOKEN:
            token = request.headers.get("Authorization", "").replace("Bearer ", "")
            if token != ACCESS_TOKEN:
                return jsonify({"error": "Unauthorized"}), 401
        return f(*args, **kwargs)
    return decorated


@app.route("/")
def index():
    return jsonify({
        "service": "DeepSeek Balance Proxy",
        "version": "1.0.0",
        "endpoints": {
            "GET /api/balance": "获取 DeepSeek 账户余额",
            "GET /api/health": "健康检查"
        }
    })


@app.route("/api/health")
def health():
    """健康检查"""
    return jsonify({
        "status": "ok",
        "has_api_key": bool(DEEPSEEK_API_KEY),
        "cache_ttl": CACHE_TTL
    })


@app.route("/api/balance")
@require_token
def get_balance():
    """
    获取 DeepSeek 余额

    使用缓存避免频繁请求 DeepSeek API。
    缓存时间由 CACHE_TTL 环境变量控制（默认 60 秒）。
    """
    # 检查 API Key 是否已配置
    if not DEEPSEEK_API_KEY:
        return jsonify({"error": "服务器未配置 DEEPSEEK_API_KEY"}), 500

    # 检查缓存
    now = time.time()
    if _cache["data"] and (now - _cache["timestamp"]) < CACHE_TTL:
        return jsonify(_cache["data"])

    # 请求 DeepSeek API
    headers = {
        "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
        "Accept": "application/json"
    }

    try:
        response = requests.get(
            DEEPSEEK_BALANCE_URL,
            headers=headers,
            timeout=15
        )
        response.raise_for_status()
        data = response.json()

        # 更新缓存
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


if __name__ == "__main__":
    port = int(os.getenv("PORT", "5000"))
    debug = os.getenv("DEBUG", "false").lower() == "true"

    print(f"DeepSeek Balance Proxy Server")
    print(f"  API Key 已配置: {'是' if DEEPSEEK_API_KEY else '否'}")
    print(f"  Access Token: {'已启用' if ACCESS_TOKEN else '未启用（不推荐）'}")
    print(f"  缓存 TTL: {CACHE_TTL}秒")
    print(f"  监听端口: {port}")
    print()

    app.run(host="0.0.0.0", port=port, debug=debug)
