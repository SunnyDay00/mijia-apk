# 米家智能插座电量控制 APK

这是一个 Android APK 项目，用手机直接在局域网内控制小米智能插座 3，并根据手机电量自动开关插座。

## 来源说明

本仓库不是 GitHub 机制上的 fork，仓库由本地创建并推送到：

```text
https://github.com/SunnyDay00/mijia-apk
```

项目最初参考了 [Do1e/mijia-api](https://github.com/Do1e/mijia-api) 对米家设备、MIOT 属性和米家云端接口的整理。当前 APK 没有继续保留电脑端 HTTP 原型服务，也不依赖电脑常驻服务；核心控制逻辑已经改为原生 Android 代码直接实现 miIO 局域网协议。

## 已实现功能

- 添加插座：填写插座名称、局域网 IP、32 位 miIO token。
- 手动控制：开启插座、关闭插座、读取插座开关状态。
- 电量读取：首页显示手机当前电量。
- 自动控制：设置低电量开启阈值、高电量关闭阈值。
- 常驻通知：单独开关前台服务，让 App 长时间保持电量监听。
- 开机恢复：启用常驻通知或自动控制后，系统重启后尝试恢复服务。
- HyperOS 适配：提供后台运行、自启动、应用设置入口。
- GitHub Actions 构建：APK 通过远端 CI 构建，不依赖本机 Java/Gradle。

## 界面截图

主界面包含当前电量、服务状态、插座控制、阈值设置和自动控制开关：

![主界面与插座控制](docs/images/app-main-control.png)

后台权限和状态日志用于确认 HyperOS / MIUI 长时间运行所需权限：

![后台权限与状态日志](docs/images/app-permissions-log.png)

## 工作方式

手机和插座必须在同一局域网。App 使用 Android 电池广播读取手机电量，然后通过 miIO 局域网 UDP 协议控制插座。

默认适配的小米智能插座 3 参数：

| 项目 | 值 |
| --- | --- |
| 常见型号 | `cuco.plug.v3` |
| miIO 端口 | `54321/udp` |
| 开关属性 `siid` | `2` |
| 开关属性 `piid` | `1` |
| 开启 | `true` |
| 关闭 | `false` |

## Token 说明

当前 APK 需要用户手动填写插座 token。只提供插座 IP 无法从已绑定设备中直接反推出 token，因为局域网握手不会返回真实 token。

后续可以做“小米账号登录并从米家云端读取设备 token”的功能，但这会引入账号登录、云端接口加密和凭据保存等安全边界。当前版本先保持本地局域网控制，token 只保存在手机 App 本地配置中，不应提交到 GitHub。

### 获取 token 的自部署工具

当前可使用自部署的 Xiaomi Cloud Tokens Extractor Web Tool 获取设备 token：

```text
https://xiaomi-token-web.ketenglaoshu.workers.dev
```

原始公开演示地址：

```text
https://xiaomi-token-web.asd.workers.dev/
```

本项目优先使用自部署地址。原始公开演示地址只作为来源参考，不建议直接输入小米账号密码。

当前自部署版本基于 `rankjie/xiaomi-tokens-web` fork 后部署到自己的 Cloudflare Worker，并做过以下调整：

- 补充小米双因素认证流程中的 `authStart`、`identity/list`、`result/check`、`Auth2/end`、`STS` 跳转处理。
- 补充图形验证码流程，登录时如果小米返回 `captchaUrl`，页面会显示图形验证码输入框。
- 修复图形验证码图片请求返回的挑战 cookie 保留问题，避免明明输入正确仍被小米判定验证码错误。
- 增加不包含账号密码、验证码、token 明文的诊断日志，便于排查登录阶段卡点。

使用注意事项：

- 只使用这个自部署地址，不要把小米账号密码输入到第三方公开 demo。
- 建议使用无痕窗口打开，用完后清理该站点的浏览器数据。
- 登录后如果出现图形验证码，先在工具页面输入图形验证码继续。
- 如果进入小米双因素认证页面，只在小米页面选择短信或邮箱并发送验证码。
- 收到 6 位验证码后，不要在小米官网页面提交；回到工具页面输入验证码并点击验证。
- 复制设备 token 后，只填写到 APK 本地配置中，不要写入源码、README、截图或提交记录。
- token 获取完成后，如长期不用该工具，建议在 Cloudflare 中停用或删除对应 Worker。

## 手机使用流程

1. 在路由器后台给插座固定局域网 IP。
2. 安装 APK。
3. 在 App 内填写插座名称、局域网 IP、32 位 token。
4. 点击“添加 / 保存插座”。
5. 先点击“状态”确认能读到插座开关状态。
6. 再测试“开启”和“关闭”。
7. 设置低电量开启阈值和高电量关闭阈值，例如 `40` / `80`。
8. 打开“常驻通知，保持电量监听服务运行”。
9. 打开“启用阈值自动开关插座”。
10. 在“后台权限”中允许通知、自启动和后台运行。

## HyperOS / MIUI 注意事项

在 Redmi K40 / HyperOS 3.0 / Android 16 上做过基础验证。为了长时间运行，建议手动确认：

- 允许通知，否则前台服务状态可能不可见。
- 允许自启动，否则重启后可能不会恢复服务。
- 允许后台运行或忽略电池优化，否则锁屏后可能被系统停止。

## 项目结构

```text
app/src/main/java/com/sunnyday/mijiaapk/
  AppSettings.java             本地配置读写
  BootReceiver.java            开机恢复服务
  MainActivity.java            主界面、表单、手动控制、权限入口
  MiioLanClient.java           miIO 局域网 UDP/AES 控制实现
  PlugAutomationService.java   前台服务、电量监听、阈值自动控制

.github/workflows/android-apk.yml
  GitHub Actions APK 构建流程
```

## 构建 APK

推送到 `main` 后自动构建：

```text
.github/workflows/android-apk.yml
```

也可以手动触发：

```powershell
gh workflow run android-apk.yml --repo SunnyDay00/mijia-apk --ref main
```

查看构建：

```powershell
gh run list --repo SunnyDay00/mijia-apk --workflow android-apk.yml --limit 5
gh run watch <run-id> --repo SunnyDay00/mijia-apk --exit-status
```

下载 APK：

```powershell
$run = "<run-id>"
$dir = "dist\mijia-apk-debug-$run"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
gh run download $run --repo SunnyDay00/mijia-apk --name mijia-apk-debug --dir $dir
Get-FileHash -Algorithm SHA256 "$dir\app-debug.apk"
```

安装到已连接 ADB 设备：

```powershell
adb install -r "$dir\app-debug.apk"
```

## 开源许可

本项目采用 [GPL-3.0](LICENSE) 开源许可证。
