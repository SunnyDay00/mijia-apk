# 小米智能插座 3 局域网 HTTP 控制服务

这个项目把小米智能插座 3 的局域网 miIO/MIOT 控制封装成一个普通 HTTP API。手机只需要按电量阈值请求本服务：

- 电量达到上限：`POST /plug/off`，关闭插座，停止充电
- 电量降到下限：`POST /plug/on`，打开插座，恢复充电

## 方案边界

- 本项目走局域网控制，需要插座 `IP` 和 32 位 `token`。
- [Do1e/mijia-api](https://github.com/Do1e/mijia-api) 是很好的米家云 API 参考，适合登录、列设备、云端属性读写；但它不是局域网控制协议实现。
- 本项目直接实现 miIO 局域网 UDP 协议，不依赖米家云端；服务端必须和插座在同一局域网，且电脑/服务器防火墙要允许手机访问服务端端口。

## 小米智能插座 3 默认参数

常见型号：`cuco.plug.v3`

开关属性：

| 属性 | 值 |
| --- | --- |
| `siid` | `2` |
| `piid` | `1` |
| 类型 | `bool` |
| 含义 | `true` 开，`false` 关 |

这些值已经写进 `.env.example`，如果你的设备型号不同，可以在 `.env` 里覆盖。

## 安装

```powershell
cd E:\Code\米家
uv sync --extra dev
Copy-Item .env.example .env
```

编辑 `.env`：

```env
PLUG_IP=192.168.1.100
PLUG_TOKEN=你的32位token
API_KEY=自己设置一个给手机用的密钥
```

## 启动

```powershell
.\scripts\run.ps1
```

默认监听：

```text
http://电脑局域网IP:8787
```

例如电脑 IP 是 `192.168.1.20`：

```text
http://192.168.1.20:8787
```

## API

如果设置了 `API_KEY`，手机请求需要带 `?key=你的密钥`，或请求头 `X-API-Key: 你的密钥`。

```http
GET /health
GET /status?key=你的密钥
POST /plug/on?key=你的密钥
POST /plug/off?key=你的密钥
POST /plug/toggle?key=你的密钥
POST /plug/set?key=你的密钥
Content-Type: application/json

{"on": true}
```

PowerShell 本机测试：

```powershell
Invoke-RestMethod "http://127.0.0.1:8787/health"
Invoke-RestMethod -Method Post "http://127.0.0.1:8787/plug/on?key=你的密钥"
Invoke-RestMethod -Method Post "http://127.0.0.1:8787/plug/off?key=你的密钥"
Invoke-RestMethod "http://127.0.0.1:8787/status?key=你的密钥"
```

## 手机自动化

### iPhone 快捷指令

1. 打开“快捷指令”。
2. 新建个人自动化：“电池电量”。
3. 条件一：电量升至 `80%` 以上。
4. 操作：“获取 URL 内容”。
5. URL 填：`http://电脑IP:8787/plug/off?key=你的密钥`。
6. 方法选 `POST`。
7. 再建一个条件：电量低于 `40%`，请求 `http://电脑IP:8787/plug/on?key=你的密钥`。

### Android Tasker / MacroDroid

用电池电量作为触发条件：

- 高于 `80%`：HTTP POST `http://电脑IP:8787/plug/off?key=你的密钥`
- 低于 `40%`：HTTP POST `http://电脑IP:8787/plug/on?key=你的密钥`

建议上下限至少间隔 20%，避免电量在临界点反复开关。

## 获取 token

你需要拿到插座的局域网 `IP` 和 `token`。常见方式：

- 路由器后台固定插座 IP。
- 用 `miiocli` 或其他 Xiaomi token 提取工具获取 token。
- 也可以先用米家云 API 列设备，确认设备名称、型号和在线状态；真正局域网控制仍以 `IP + token` 为准。

## 验证

```powershell
uv run ruff check .
uv run pytest
```
