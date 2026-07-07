# 米家智能插座电量控制 APK

这个仓库的目标是生成一个 Android APK：手机直接根据自身电量，通过局域网 miIO/MIOT 协议控制小米智能插座 3 开关。

- 电量达到上限：关闭插座，停止充电
- 电量降到下限：开启插座，恢复充电
- 支持在 App 内填写插座 `IP`、`token`、高低电量阈值
- 支持手动开启、关闭、读取插座状态
- 支持前台服务监听电量变化，启用后开机自动恢复
- 针对 Redmi K40 / HyperOS 3.0 / Android 16 添加后台运行、自启动、应用设置入口

## 方案边界

- APK 走局域网控制，需要插座 `IP` 和 32 位 `token`。
- [Do1e/mijia-api](https://github.com/Do1e/mijia-api) 是很好的米家云 API 参考，适合登录、列设备、云端属性读写；但它不是局域网控制协议实现。
- 本项目直接实现 miIO 局域网 UDP 协议，不依赖米家云端；手机必须和插座在同一局域网。
- token 只应填写在手机 App 内，不要提交到 GitHub。

## 开源许可

本项目采用 [GPL-3.0](LICENSE) 开源许可证。

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

Android 代码里同样默认使用这组参数。

## GitHub Actions 构建 APK

仓库推送到 `main` 后会自动运行 `.github/workflows/android-apk.yml`。

也可以在 GitHub 页面手动触发：

1. 打开仓库的 `Actions`。
2. 选择 `Build Android APK`。
3. 点击 `Run workflow`。
4. 构建完成后下载 artifact：`mijia-apk-debug`。

Debug APK 路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 手机使用

安装 APK 后：

1. 在路由器后台给插座固定 IP。
2. 在 App 内“添加插座”：填写名称、局域网 IP、32 位 token。
3. 点击“添加 / 保存插座”。
4. 用“状态”“开启”“关闭”测试插座能否正常响应。
5. 设置低电量开启阈值和高电量关闭阈值，例如 `40` / `80`。
6. 勾选“启用电量自动控制和开机自启”。
7. 在“后台权限”区域依次打开后台运行、自启动、应用设置，允许通知、后台运行和自启动。

Android 后台会限制长期任务。启用自动控制后，系统会显示前台服务通知；建议在系统电池设置里允许本 App 后台运行。

HyperOS / MIUI 设备还需要额外确认：

- 允许通知，否则前台服务状态可能不可见。
- 允许自启动，否则重启后可能不会恢复自动控制。
- 允许后台运行或忽略电池优化，否则锁屏后可能停止监听电量。

## Python HTTP 原型服务

仓库仍保留一个 Python HTTP 原型服务。它适合在电脑或局域网服务器上运行，由手机自动化工具发 HTTP 请求控制插座。

### 安装

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

### 启动

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

### API

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

### 手机自动化

#### iPhone 快捷指令

1. 打开“快捷指令”。
2. 新建个人自动化：“电池电量”。
3. 条件一：电量升至 `80%` 以上。
4. 操作：“获取 URL 内容”。
5. URL 填：`http://电脑IP:8787/plug/off?key=你的密钥`。
6. 方法选 `POST`。
7. 再建一个条件：电量低于 `40%`，请求 `http://电脑IP:8787/plug/on?key=你的密钥`。

#### Android Tasker / MacroDroid

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
```
