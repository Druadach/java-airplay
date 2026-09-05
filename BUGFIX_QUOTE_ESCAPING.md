# 修复启动器路径引号转义问题

## 问题描述

安装版启动后报错：
```
Illegal char <"> at index 32: C:\Program Files\AirPlayReceiver"
```

该错误在安装路径包含空格时出现（如 `C:\Program Files\`）。

## 根本原因

`packaging/AirPlayReceiver.cs` 第 58 行的参数拼接存在问题：

```csharp
// 错误的写法
Arguments = "-Dfile.encoding=UTF-8 -jar \"" + jar + "\" --base-dir=\"" + appDir + "\""
```

这会生成类似以下的命令行：
```
javaw.exe -jar "path\to\jar" --base-dir="C:\Program Files\AirPlayReceiver"
```

问题在于 `--base-dir="path"` 中等号后直接跟引号，导致 Java 参数解析器将引号本身当作参数值的一部分，而不是路径分隔符。

## 解决方案

在等号和引号之间添加空格：

```csharp
// 正确的写法
Arguments = "-Dfile.encoding=UTF-8 -jar \"" + jar + "\" --base-dir \"" + appDir + "\""
```

生成的命令行：
```
javaw.exe -jar "path\to\jar" --base-dir "C:\Program Files\AirPlayReceiver"
```

这样 Java 会正确解析带空格的路径。

## 额外更改

同时更新了文档中的设备术语：
- 将"手机"统一改为"设备"
- 原因：用户也可能将平板（iPad）投屏到电脑
- 影响文件：README.zh-CN.md、packaging/installer.iss

## 验证方法

重新编译并打包后，在包含空格的路径（如 `C:\Program Files\AirPlayReceiver`）安装测试：

1. 运行安装程序
2. 使用默认安装路径（Program Files）
3. 启动 AirPlayReceiver.exe
4. 应该正常启动，不再报错

## 相关 Issue

用户反馈截图显示该问题在安装版和便携版中均出现，原因相同。修复后需重新打包发布。
