# 打包说明（投屏软件 EXE）

把本仓库（Druadach-java-airplay 修改版）打包成单个 Windows 安装包 EXE + 便携版 ZIP。

## 产物（dist/）

| 文件 | 说明 |
| --- | --- |
| `AirPlayReceiver_Setup_1.2.1_Test_20260906.exe` | 本地测试安装版：选目录、快捷方式、可卸载 |
| `AirPlayReceiver_Portable_1.2.1_Test_20260906.zip` | 本地测试便携版：解压即用，入口 `AirPlayReceiver.exe` |

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
& "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe" /Qp `
  /FAirPlayReceiver_Setup_1.2.1_Test_20260906 installer.iss
7z a -tzip -mx=7 dist/AirPlayReceiver_Portable_1.2.1_Test_20260906.zip "./stage/AirPlay接收器"
```

Inno Setup 未装时：`winget install --id JRSoftware.InnoSetup -e`

## 版本号

当前本地测试版本为 v1.2.1；改版本号需同步改 `installer.iss` 里的
`MyAppVersion` 与输出文件名。
