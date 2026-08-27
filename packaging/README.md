# 打包说明（投屏软件 EXE）

把本仓库（Druadach-java-airplay 修改版）打包成单个 Windows 安装包 EXE + 便携版 ZIP。

## 产物（dist/）

| 文件 | 说明 |
| --- | --- |
| `AirPlayReceiver_Setup_1.2.0.exe` | 安装版：选目录、开始菜单/桌面快捷方式、可卸载，装完弹防火墙提醒 |
| `AirPlayReceiver_Portable_1.2.0.zip` | 便携版：解压即用，入口 `AirPlayReceiver.exe` |

安装包约 174 MB（源目录 436 MB），便携 ZIP 约 225 MB。

## 组成

- `stage/AirPlay接收端/` — 安装内容 staging 目录：
  - `jre\`、`gstreamer\`（内置运行时）、两个 jar、`application.properties`、bat 脚本
  - `AirPlayReceiver.exe` — 用 csc 编译的原生启动器，替代 `run_airplay_gui.bat`（无黑窗），
    自动设置 PATH / GST_PLUGIN_PATH 并用 `javaw.exe` 拉起 Swing 启动器
- `make_icon.py` — 纯 Python 生成应用图标 `airplay.ico`
- `installer.iss` — Inno Setup 6 脚本（UTF-8 BOM；中文界面用 `ChineseSimplified.isl`）
- 安装到 `%ProgramFiles%\AirPlayReceiver`，并对目录授予 Users-modify 权限，
  保证普通用户运行时也能保存配置

## 重新构建

```powershell
cd packaging
python make_icon.py                                   # 生成图标
..\..\..\..\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe `
  -nologo -target:winexe -optimize+ -out:AirPlayReceiver.exe `
  "-win32icon:airplay.ico" AirPlayReceiver.cs         # 编译启动器
# 更新 stage\ 后:
& "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe" //Qp installer.iss
7z a -tzip -mx=7 dist/AirPlayReceiver_Portable_1.2.0.zip "./stage/AirPlay接收端"
```

Inno Setup 未装时：`winget install --id JRSoftware.InnoSetup -e`

## 版本号

跟随上游 git tag（当前 v1.2.0），改版本号需同步改 `installer.iss` 里的
`MyAppVersion` 与输出文件名。
