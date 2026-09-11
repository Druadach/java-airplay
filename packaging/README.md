# 打包说明（投屏软件 EXE）

把本仓库（Druadach-java-airplay 修改版）打包成单个 Windows 安装包 EXE + 便携版 ZIP。

## 产物（dist/）

| 文件 | 说明 |
| --- | --- |
| `AirPlayReceiver_Setup_1.2.4.exe` | 安装版：选目录、快捷方式、可卸载 |
| `AirPlayReceiver_Portable_1.2.4.zip` | 便携版：解压即用，入口 `AirPlayReceiver.exe` |

安装包约 174 MB（源目录 436 MB），便携 ZIP 约 225 MB。

## 组成

- `stage/AirPlay接收器/` — 安装内容 staging 目录：
  - `jre\`、`gstreamer\`（内置运行时）、两个 jar、`application.properties`、bat 脚本
  - `AirPlayReceiver.exe` — 用 csc 编译的原生启动器，替代 `run_airplay_gui.bat`（无黑窗），
    自动设置 PATH / GST_PLUGIN_PATH 并用 `javaw.exe` 拉起 Swing 启动器
- `make_icon.py` — 纯 Python 生成蓝底（material blue 渐变）、白色投屏图形的 `airplay.ico` 和 `tray_icon.png`；PNG 由 `build_patch.ps1` 嵌入两个 JAR，供窗口及托盘使用。
- `installer.iss` — Inno Setup 6 脚本（中文界面用 `ChineseSimplified.isl`）
- `jre/conf/fonts/fontconfig.properties` 必须同步到 stage，修复 UTF-8 Windows 下原生菜单中文方框。
- `LauncherArgumentTest.cs` 验证 EXE 的参数转义和自启动参数传递。
- `AirPlayUpdater.cs` 是独立的 .NET Framework 更新助手，构建时嵌入启动器 JAR；不依赖将被替换的 JRE。
- `UpdaterTransactionTest.cs` 使用临时目录测试成功更新、文件占用、失败回滚、配置保留、路径边界和 junction 拒绝，不启动投屏。
- 安装到 `%ProgramFiles%\AirPlayReceiver`，并对目录授予 Users-modify 权限，
  保证普通用户运行时也能保存配置

## 重新构建

```powershell
cd packaging
& ..\build_patch.ps1                                  # 完整 Java 构建与测试
& "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe" `
  -nologo -target:winexe -optimize+ -out:AirPlayReceiver.exe `
  "-win32icon:airplay.ico" AirPlayReceiver.cs         # 编译启动器
# 更新 stage\ 后:
$version = (Get-Content -LiteralPath ..\VERSION -Raw).Trim()
& "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe" /Qp `
  installer.iss
7z a -tzip -mx=7 "dist/AirPlayReceiver_Portable_$version.zip" "./stage/AirPlay接收器"
```

Inno Setup 未装时：`winget install --id JRSoftware.InnoSetup -e`

## 版本号

版本号统一由仓库根目录的 `VERSION` 指定（当前为 `1.2.4`，不加 `v` 前缀）。
`build_patch.ps1` 将它复制为启动器内的 `airplay-version.txt`，“关于”和 GitHub 更新检查读取这个资源；`installer.iss` 也直接读取同一个 `VERSION`，安装包文件名随之更新。

发布前先修改 `VERSION`，重新构建并将新的启动器 JAR 同步到 `stage`，再生成安装包和便携包。GitHub Release 使用对应的 `vX.Y.Z` 标签并标记为正式版。
不要只修改安装器版本号或仅重命名旧产物，否则安装包中的程序仍会报告旧版本。
启动器只在用户点击“检查更新”时访问公开的 GitHub Releases API，不依赖 GitHub Token。发现新版后可选择下载并自动更新；安装必须再次确认重启，不会替换正在使用的 JRE。

## 自动更新包约定

- 正式版标签使用 `vX.Y.Z`，便携资产命名为 `AirPlayReceiver_Portable_X.Y.Z.zip`，并上传到本仓库对应的 GitHub Release。自动更新使用便携包，安装版与便携版共用这一流程。
- GitHub API 中资产的 `state` 必须为 `uploaded`，`size` 必须有效，`digest` 必须包含 `sha256:<64 位十六进制值>`；没有校验值时只提供手动下载入口。
- ZIP 保持一个顶层目录（如 `AirPlay接收器/`）。必须包含 `jre`、`gstreamer`、两个 JAR、`AirPlayReceiver.exe` 和两个启动 BAT。不要直接打包开发目录或 `.airplay-update`。
- `build_patch.ps1` 会编译并嵌入 `updater/AirPlayUpdater.exe`、版本资源和 `airplay-update-protocol.txt`（当前协议为 `1`）。发布时必须使用重新构建的 JAR，不要仅修改 ZIP 文件名；旧包缺少此协议时，下载后会拒绝自动安装。
- 更新只替换明确列出的运行文件，不覆盖 `application.properties`、日志、开发源码或其他用户文件。安装器覆盖安装时也仅在配置不存在时写入默认配置。
- 更新助手先确认原启动器 PID 和启动时间，等待原程序退出，再备份、替换、验证新版 JRE/JAR 并等待新界面初始化成功。接收服务在双方确认后才可启动。失败时尝试还原旧运行文件；备份及诊断记录留在 `.airplay-update/<任务 ID>/`。
- 自动更新不修改 Windows 的卸载注册信息或快捷方式；“已安装的应用”中的安装版本可能仍是原安装器版本，程序内以 JAR 版本为准。需要刷新 Windows 安装记录时，使用新版安装器覆盖安装。

发布前在干净的测试副本上检查完整流程。不要用旧的公开发行包覆盖尚未发布的开发改动。程序只接受比本机更新的正式版，因此同版本检查不会出现自动更新按钮。
