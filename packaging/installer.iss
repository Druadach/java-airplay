; AirPlay Receiver installer — bundles JRE21 + GStreamer runtime + patched server jar
#define MyAppName "AirPlay 接收端"
#define MyAppNameEn "AirPlay Receiver"
#define MyAppVersion "1.2.0"
#define MyAppExeName "AirPlayReceiver.exe"

[Setup]
AppId={{7C1A9A52-4F2B-4F87-9B33-53F5D18A7E21}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={cm:NameAndVersion,{#MyAppName},{#MyAppVersion}}
AppPublisher=Druadach/java-airplay (patched build)
DefaultDirName={autopf}\AirPlayReceiver
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=dist
OutputBaseFilename=AirPlayReceiver_Setup_{#MyAppVersion}
SetupIconFile=airplay.ico
Compression=lzma2/max
SolidCompression=yes
LZMADictionarySize=65536
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64compatible
ShowLanguageDialog=yes
UninstallDisplayIcon={app}\{#MyAppExeName}

[Languages]
Name: "chinese"; MessagesFile: "ChineseSimplified.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[CustomMessages]
english.FirewallHint=Installation complete.%n%nFirst launch tip:%nWhen Windows Firewall asks for permission, please click "Allow access", otherwise iPhone/iPad cannot discover this PC.
chinese.FirewallHint=安装完成。%n%n首次启动提示：%n当 Windows 防火墙弹出提示时，请点击“允许访问”，否则手机将搜索不到本电脑。

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: checkedonce

[Dirs]
; allow non-admin runtime to save application.properties next to the exe
Name: "{app}"; Permissions: users-modify

[Files]
Source: "stage\AirPlay接收端\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
begin
  if (CurStep = ssPostInstall) and (not WizardSilent()) then
    MsgBox(ExpandConstant('{cm:FirewallHint}'), mbInformation, MB_OK);
end;
