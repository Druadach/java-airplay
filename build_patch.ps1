[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$patchRoot = $PSScriptRoot
$sourceJar = Join-Path $patchRoot 'java-airplay-server.jar'
$outputJar = Join-Path $patchRoot 'java-airplay-server-fixed.jar'
$launcherOutputJar = Join-Path $patchRoot 'java-airplay-launcher.jar'
$javaBin = Join-Path $patchRoot 'jre\bin'
$javac = Join-Path $javaBin 'javac.exe'
$java = Join-Path $javaBin 'java.exe'
$javaw = Join-Path $javaBin 'javaw.exe'
$jar = Join-Path $javaBin 'jar.exe'
$mainSourceRoot = Join-Path $patchRoot 'patch-src\main\java'
$testSourceRoot = Join-Path $patchRoot 'patch-src\test\java'
$launcherSourceRoot = Join-Path $patchRoot 'patch-src\launcher\src'
$launcherTestRoot = Join-Path $patchRoot 'patch-src\launcher\test'
$versionFile = Join-Path $patchRoot 'VERSION'
$trayIconSource = Join-Path $patchRoot 'packaging\tray_icon.png'
$updaterSource = Join-Path $patchRoot 'packaging\AirPlayUpdater.cs'
$updaterProtocol = Join-Path $patchRoot 'patch-src\launcher\resources\airplay-update-protocol.txt'
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'

foreach ($requiredPath in @($sourceJar, $javac, $java, $javaw, $jar, $mainSourceRoot, $testSourceRoot,
        $launcherSourceRoot, $launcherTestRoot, $versionFile, $updaterSource, $updaterProtocol, $csc)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required path does not exist: $requiredPath"
    }
}

if (-not (Test-Path -LiteralPath $trayIconSource)) {
    throw "Launcher icon does not exist: $trayIconSource. Run packaging/make_icon.py first."
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$patchTempRoot = Join-Path ([IO.Path]::GetTempPath()) ("airplay-patch-" + [guid]::NewGuid().ToString('N'))
$libraryDir = Join-Path $patchTempRoot 'lib'
$mainOutput = Join-Path $patchTempRoot 'classes-main'
$testOutput = Join-Path $patchTempRoot 'classes-test'
$launcherOutput = Join-Path $patchTempRoot 'classes-launcher'
$launcherTestOutput = Join-Path $patchTempRoot 'classes-launcher-test'
$nestedJarDir = Join-Path $patchTempRoot 'nested'
$fatJarStage = Join-Path $patchTempRoot 'fat-stage'
$appClassesDir = Join-Path $patchTempRoot 'app-classes'
$candidateJar = Join-Path $patchTempRoot 'java-airplay-server-fixed.jar'
$candidateLauncherJar = Join-Path $patchTempRoot 'java-airplay-launcher.jar'
$launcherValidationDir = Join-Path $patchTempRoot 'launcher-validation'
$nativeLauncherTestExe = Join-Path $patchTempRoot 'launcher-argument-test.exe'
$nativeUpdaterTestExe = Join-Path $patchTempRoot 'updater-transaction-test.exe'

try {
    foreach ($directory in @($libraryDir, $mainOutput, $testOutput, $launcherOutput,
            $launcherTestOutput, $nestedJarDir, $fatJarStage, $appClassesDir)) {
        [void][IO.Directory]::CreateDirectory($directory)
    }

    $outerJar = [IO.Compression.ZipFile]::OpenRead($sourceJar)
    try {
        foreach ($entry in $outerJar.Entries) {
            if ($entry.FullName -like 'BOOT-INF/lib/*.jar') {
                $target = Join-Path $libraryDir ([IO.Path]::GetFileName($entry.FullName))
                $sourceStream = $entry.Open()
                $targetStream = [IO.File]::Create($target)
                try {
                    $sourceStream.CopyTo($targetStream)
                } finally {
                    $targetStream.Dispose()
                    $sourceStream.Dispose()
                }
                continue
            }

            $appClassesPrefix = 'BOOT-INF/classes/'
            if (-not $entry.FullName.StartsWith($appClassesPrefix) -or $entry.FullName.EndsWith('/')) {
                continue
            }

            $relativePath = $entry.FullName.Substring($appClassesPrefix.Length).Replace(
                    '/', [IO.Path]::DirectorySeparatorChar)
            $target = Join-Path $appClassesDir $relativePath
            [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target))
            $sourceStream = $entry.Open()
            $targetStream = [IO.File]::Create($target)
            try {
                $sourceStream.CopyTo($targetStream)
            } finally {
                $targetStream.Dispose()
                $sourceStream.Dispose()
            }
        }
    } finally {
        $outerJar.Dispose()
    }

    $launcherIcon = Join-Path $appClassesDir 'menu\tray_icon.png'
    Copy-Item -LiteralPath $trayIconSource -Destination $launcherIcon -Force

    $launcherSources = @(Get-ChildItem -LiteralPath $launcherSourceRoot -Recurse -Filter '*.java' |
            ForEach-Object FullName)
    $updaterOutput = Join-Path $launcherOutput 'updater'
    [void][IO.Directory]::CreateDirectory($updaterOutput)
    & $csc -nologo -codepage:65001 -utf8output -target:winexe `
            -main:AirPlayLauncher.Update.UpdaterProgram '-r:System.Windows.Forms.dll' `
            "-out:$(Join-Path $updaterOutput 'AirPlayUpdater.exe')" $updaterSource
    if ($LASTEXITCODE -ne 0) {
        throw "Native updater compilation failed with exit code $LASTEXITCODE"
    }
    Copy-Item -LiteralPath $updaterProtocol -Destination (Join-Path $launcherOutput 'airplay-update-protocol.txt')
    Copy-Item -LiteralPath $versionFile -Destination (Join-Path $launcherOutput 'airplay-version.txt')
    & $javac --release 17 -encoding UTF-8 -d $launcherOutput $launcherSources
    if ($LASTEXITCODE -ne 0) {
        throw "Launcher compilation failed with exit code $LASTEXITCODE"
    }

    $launcherTests = @(Get-ChildItem -LiteralPath $launcherTestRoot -Recurse -Filter '*.java' |
            ForEach-Object FullName)
    & $javac --release 17 -encoding UTF-8 -cp $launcherOutput -d $launcherTestOutput $launcherTests
    if ($LASTEXITCODE -ne 0) {
        throw "Launcher test compilation failed with exit code $LASTEXITCODE"
    }

    & $java '-Djava.awt.headless=true' -cp "$launcherTestOutput;$launcherOutput" `
            com.github.serezhka.airplay.launcher.LauncherCoreTest
    if ($LASTEXITCODE -ne 0) {
        throw "Launcher tests failed with exit code $LASTEXITCODE"
    }

    & $java '--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED' `
            '--add-opens=java.desktop/sun.awt=ALL-UNNAMED' `
            -cp "$launcherTestOutput;$launcherOutput" `
            com.github.serezhka.airplay.launcher.NativeMenuFontTest
    if ($LASTEXITCODE -ne 0) {
        throw "Native menu font configuration tests failed with exit code $LASTEXITCODE"
    }

    & $csc -nologo -target:exe -main:AirPlayLauncher.LauncherArgumentTest `
            '-r:System.Windows.Forms.dll' "-out:$nativeLauncherTestExe" `
            (Join-Path $patchRoot 'packaging\AirPlayReceiver.cs') `
            (Join-Path $patchRoot 'packaging\LauncherArgumentTest.cs')
    if ($LASTEXITCODE -ne 0) {
        throw "Native launcher argument test compilation failed with exit code $LASTEXITCODE"
    }
    & $nativeLauncherTestExe
    if ($LASTEXITCODE -ne 0) {
        throw "Native launcher argument tests failed with exit code $LASTEXITCODE"
    }

    & $csc -nologo -codepage:65001 -utf8output -target:exe `
            -main:AirPlayLauncher.Update.UpdaterTransactionTest '-r:System.Windows.Forms.dll' `
            "-out:$nativeUpdaterTestExe" $updaterSource (Join-Path $patchRoot 'packaging\UpdaterTransactionTest.cs')
    if ($LASTEXITCODE -ne 0) {
        throw "Native updater test compilation failed with exit code $LASTEXITCODE"
    }
    & $nativeUpdaterTestExe
    if ($LASTEXITCODE -ne 0) {
        throw "Native updater transaction tests failed with exit code $LASTEXITCODE"
    }

    $mainSources = @(Get-ChildItem -LiteralPath $mainSourceRoot -Recurse -Filter '*.java' |
            ForEach-Object FullName)
    & $javac --release 17 -parameters -encoding UTF-8 `
            -cp "$appClassesDir;$libraryDir\*" -d $mainOutput $mainSources
    if ($LASTEXITCODE -ne 0) {
        throw "Main source compilation failed with exit code $LASTEXITCODE"
    }

    $testSources = @(Get-ChildItem -LiteralPath $testSourceRoot -Recurse -Filter '*.java' |
            ForEach-Object FullName)
    & $javac --release 17 -encoding UTF-8 -cp "$mainOutput;$appClassesDir;$libraryDir\*" -d $testOutput $testSources
    if ($LASTEXITCODE -ne 0) {
        throw "Test source compilation failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$mainOutput;$libraryDir\*" `
            com.github.serezhka.airplay.server.internal.decoder.AudioDecoderRegressionTest
    if ($LASTEXITCODE -ne 0) {
        throw "Audio RTP decoder tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$mainOutput;$libraryDir\*" `
            com.github.serezhka.airplay.server.internal.handler.audio.AudioHandlerRegressionTest
    if ($LASTEXITCODE -ne 0) {
        throw "Regression tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$mainOutput;$libraryDir\*" `
            com.github.serezhka.airplay.server.internal.handler.control.ControlHandlerReleaseTest
    if ($LASTEXITCODE -ne 0) {
        throw "Reference-count tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$mainOutput;$libraryDir\*" `
            com.github.serezhka.airplay.server.internal.handler.control.AirPlayVolumeTest
    if ($LASTEXITCODE -ne 0) {
        throw "AirPlay volume tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$mainOutput;$libraryDir\*" `
            com.github.serezhka.airplay.server.internal.handler.control.RTSPHandlerVolumeTest
    if ($LASTEXITCODE -ne 0) {
        throw "RTSP volume tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$mainOutput;$libraryDir\*" `
            com.github.serezhka.airplay.server.internal.handler.session.SessionMediaCoordinatorTest
    if ($LASTEXITCODE -ne 0) {
        throw "Session media takeover tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$mainOutput;$libraryDir\*" `
            com.github.serezhka.airplay.server.internal.handler.session.SessionMediaCoordinatorVolumeTest
    if ($LASTEXITCODE -ne 0) {
        throw "Session volume coordination tests failed with exit code $LASTEXITCODE"
    }

    & $java '-Djava.awt.headless=true' -cp "$testOutput;$mainOutput;$libraryDir\*" `
            com.github.serezhka.airplay.player.ffmpeg.FFmpegPlayerVideoRegressionTest
    if ($LASTEXITCODE -ne 0) {
        throw "FFmpeg video tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$mainOutput;$libraryDir\*" `
            com.github.serezhka.airplay.player.ffmpeg.FFmpegPlayerAudioRegressionTest
    if ($LASTEXITCODE -ne 0) {
        throw "FFmpeg audio tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$mainOutput;$appClassesDir;$libraryDir\*" `
            com.github.serezhka.airplay.player.gstreamer.GstFullscreenConfigurationTest
    if ($LASTEXITCODE -ne 0) {
        throw "GStreamer fullscreen tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$mainOutput;$appClassesDir;$libraryDir\*" `
            com.github.serezhka.airplay.app.menu.SystemTrayMenuQuitTest
    if ($LASTEXITCODE -ne 0) {
        throw "System tray quit tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$mainOutput;$appClassesDir;$libraryDir\*" `
            com.github.serezhka.airplay.app.control.LocalControlServerTest
    if ($LASTEXITCODE -ne 0) {
        throw "Local launcher control tests failed with exit code $LASTEXITCODE"
    }

    foreach ($probe in @('normal-process-probe', 'blocked-process-probe',
            'production-timeout-process-probe')) {
        & $java -cp "$testOutput;$mainOutput;$appClassesDir;$libraryDir\*" `
                com.github.serezhka.airplay.app.menu.SystemTrayMenuQuitTest $probe
        if ($LASTEXITCODE -ne 0) {
            throw "System tray quit process probe '$probe' failed with exit code $LASTEXITCODE"
        }
    }

    $patchedServerJar = Join-Path $nestedJarDir 'server-1.0.6.jar'
    $patchedGstreamerJar = Join-Path $nestedJarDir 'gstreamer-1.0.6.jar'
    $patchedFfmpegJar = Join-Path $nestedJarDir 'ffmpeg-1.0.6.jar'
    Copy-Item -LiteralPath (Join-Path $libraryDir 'server-1.0.6.jar') -Destination $patchedServerJar
    Copy-Item -LiteralPath (Join-Path $libraryDir 'gstreamer-1.0.6.jar') -Destination $patchedGstreamerJar
    Copy-Item -LiteralPath (Join-Path $libraryDir 'ffmpeg-1.0.6.jar') -Destination $patchedFfmpegJar

    Push-Location $mainOutput
    try {
        & $jar --update --file $patchedServerJar `
                'com/github/serezhka/airplay/server/internal/decoder/AudioDecoder.class' `
                'com/github/serezhka/airplay/server/internal/handler/audio/AudioHandler.class' `
                'com/github/serezhka/airplay/server/internal/handler/control/ControlHandler.class' `
                'com/github/serezhka/airplay/server/internal/handler/control/RTSPHandler.class' `
                'com/github/serezhka/airplay/server/internal/handler/session/SessionManager.class' `
                'com/github/serezhka/airplay/server/internal/handler/session/SessionManager$Activation.class' `
                'com/github/serezhka/airplay/server/internal/handler/session/SessionManager$ControlSession.class' `
                'com/github/serezhka/airplay/server/internal/handler/session/SessionManager$MediaLease.class' `
                'com/github/serezhka/airplay/server/internal/handler/session/SessionAirPlayConsumer.class' `
                'com/github/serezhka/airplay/server/internal/handler/session/SessionAirPlayConsumer$StreamKind.class' `
                'com/github/serezhka/airplay/server/internal/handler/session/SessionMediaCoordinator.class'
        if ($LASTEXITCODE -ne 0) {
            throw "Could not patch server-1.0.6.jar (exit code $LASTEXITCODE)"
        }

        & $jar --update --file $patchedGstreamerJar `
                'com/github/serezhka/airplay/player/gstreamer/FullscreenController.class' `
                'com/github/serezhka/airplay/player/gstreamer/FullscreenKeyBindings.class' `
                'com/github/serezhka/airplay/player/gstreamer/GstPlayer.class' `
                'com/github/serezhka/airplay/player/gstreamer/GstPlayerFullscreen.class' `
                'com/github/serezhka/airplay/player/gstreamer/GstVideoPipeline.class'
        if ($LASTEXITCODE -ne 0) {
            throw "Could not patch gstreamer-1.0.6.jar (exit code $LASTEXITCODE)"
        }

        & $jar --update --file $patchedFfmpegJar `
                'com/github/serezhka/airplay/player/ffmpeg/FFmpegPlayer.class'
        if ($LASTEXITCODE -ne 0) {
            throw "Could not patch ffmpeg-1.0.6.jar (exit code $LASTEXITCODE)"
        }
    } finally {
        Pop-Location
    }

    & $java -cp "$testOutput;$patchedServerJar;$libraryDir\*" `
            com.github.serezhka.airplay.server.internal.decoder.AudioDecoderRegressionTest
    if ($LASTEXITCODE -ne 0) {
        throw "Packaged audio RTP decoder tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$mainOutput;$patchedServerJar;$libraryDir\*" `
            com.github.serezhka.airplay.server.internal.handler.session.SessionMediaCoordinatorTest
    if ($LASTEXITCODE -ne 0) {
        throw "Packaged session media takeover tests failed with exit code $LASTEXITCODE"
    }

    $serverEntry = 'BOOT-INF/lib/server-1.0.6.jar'
    $gstreamerEntry = 'BOOT-INF/lib/gstreamer-1.0.6.jar'
    $ffmpegEntry = 'BOOT-INF/lib/ffmpeg-1.0.6.jar'
    $volumeControllerEntry = 'BOOT-INF/classes/com/github/serezhka/airplay/server/VolumeController.class'
    $airPlayVolumeEntry = 'BOOT-INF/classes/com/github/serezhka/airplay/server/internal/handler/control/AirPlayVolume.class'
    $playerConfigEntry = 'BOOT-INF/classes/com/github/serezhka/airplay/app/config/PlayerConfig.class'
    $systemTrayMenuEntry = 'BOOT-INF/classes/com/github/serezhka/airplay/app/menu/SystemTrayMenu.class'
    $trayIconEntry = 'BOOT-INF/classes/menu/tray_icon.png'
    $localControlServerEntry = 'BOOT-INF/classes/com/github/serezhka/airplay/app/control/LocalControlServer.class'
    $localControlResultEntry = 'BOOT-INF/classes/com/github/serezhka/airplay/app/control/LocalControlServer$CommandResult.class'
    $localControlRejectedEntry = 'BOOT-INF/classes/com/github/serezhka/airplay/app/control/LocalControlServer$RequestRejectedException.class'
    $applicationShutdownEntry = 'BOOT-INF/classes/com/github/serezhka/airplay/app/lifecycle/ApplicationShutdown.class'
    [void][IO.Directory]::CreateDirectory((Join-Path $fatJarStage 'BOOT-INF\lib'))
    [void][IO.Directory]::CreateDirectory(
            (Join-Path $fatJarStage 'BOOT-INF\classes\com\github\serezhka\airplay\app\config'))
    [void][IO.Directory]::CreateDirectory(
            (Join-Path $fatJarStage 'BOOT-INF\classes\com\github\serezhka\airplay\app\menu'))
    [void][IO.Directory]::CreateDirectory(
            (Join-Path $fatJarStage 'BOOT-INF\classes\com\github\serezhka\airplay\app\control'))
    [void][IO.Directory]::CreateDirectory(
            (Join-Path $fatJarStage 'BOOT-INF\classes\com\github\serezhka\airplay\app\lifecycle'))
    [void][IO.Directory]::CreateDirectory(
            (Join-Path $fatJarStage 'BOOT-INF\classes\com\github\serezhka\airplay\server\internal\handler\control'))
    [void][IO.Directory]::CreateDirectory(
            (Join-Path $fatJarStage 'BOOT-INF\classes\com\github\serezhka\airplay\server'))
    [void][IO.Directory]::CreateDirectory((Join-Path $fatJarStage 'BOOT-INF\classes\menu'))
    Copy-Item -LiteralPath $patchedServerJar -Destination (Join-Path $fatJarStage $serverEntry)
    Copy-Item -LiteralPath $patchedGstreamerJar -Destination (Join-Path $fatJarStage $gstreamerEntry)
    Copy-Item -LiteralPath $patchedFfmpegJar -Destination (Join-Path $fatJarStage $ffmpegEntry)
    Copy-Item -LiteralPath (Join-Path $mainOutput 'com\github\serezhka\airplay\app\config\PlayerConfig.class') `
            -Destination (Join-Path $fatJarStage $playerConfigEntry)
    Copy-Item -LiteralPath (Join-Path $mainOutput 'com\github\serezhka\airplay\server\VolumeController.class') `
            -Destination (Join-Path $fatJarStage $volumeControllerEntry)
    Copy-Item -LiteralPath (Join-Path $mainOutput 'com\github\serezhka\airplay\server\internal\handler\control\AirPlayVolume.class') `
            -Destination (Join-Path $fatJarStage $airPlayVolumeEntry)
    Copy-Item -LiteralPath (Join-Path $mainOutput 'com\github\serezhka\airplay\app\menu\SystemTrayMenu.class') `
            -Destination (Join-Path $fatJarStage $systemTrayMenuEntry)
    Copy-Item -LiteralPath (Join-Path $mainOutput 'com\github\serezhka\airplay\app\control\LocalControlServer.class') `
            -Destination (Join-Path $fatJarStage $localControlServerEntry)
    Copy-Item -LiteralPath (Join-Path $mainOutput 'com\github\serezhka\airplay\app\control\LocalControlServer$CommandResult.class') `
            -Destination (Join-Path $fatJarStage $localControlResultEntry)
    Copy-Item -LiteralPath (Join-Path $mainOutput 'com\github\serezhka\airplay\app\control\LocalControlServer$RequestRejectedException.class') `
            -Destination (Join-Path $fatJarStage $localControlRejectedEntry)
    Copy-Item -LiteralPath (Join-Path $mainOutput 'com\github\serezhka\airplay\app\lifecycle\ApplicationShutdown.class') `
            -Destination (Join-Path $fatJarStage $applicationShutdownEntry)
    Copy-Item -LiteralPath $launcherIcon -Destination (Join-Path $fatJarStage $trayIconEntry)

    $stagedAppClasses = Join-Path $fatJarStage 'BOOT-INF\classes'
    & $java '-Djava.awt.headless=true' `
            -cp "$testOutput;$stagedAppClasses;$patchedFfmpegJar;$patchedGstreamerJar;$libraryDir\*" `
            com.github.serezhka.airplay.player.ffmpeg.FFmpegPlayerVideoRegressionTest
    if ($LASTEXITCODE -ne 0) {
        throw "Packaged FFmpeg video tests failed with exit code $LASTEXITCODE"
    }

    & $java '-Djava.awt.headless=true' `
            -cp "$testOutput;$stagedAppClasses;$patchedFfmpegJar;$patchedGstreamerJar;$libraryDir\*" `
            com.github.serezhka.airplay.player.ffmpeg.FFmpegPlayerAudioRegressionTest
    if ($LASTEXITCODE -ne 0) {
        throw "Packaged FFmpeg audio tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$stagedAppClasses;$patchedGstreamerJar;$appClassesDir;$libraryDir\*" `
            com.github.serezhka.airplay.player.gstreamer.GstFullscreenConfigurationTest
    if ($LASTEXITCODE -ne 0) {
        throw "Packaged GStreamer fullscreen tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$stagedAppClasses;$patchedGstreamerJar;$appClassesDir;$libraryDir\*" `
            com.github.serezhka.airplay.app.menu.SystemTrayMenuQuitTest
    if ($LASTEXITCODE -ne 0) {
        throw "Packaged system tray quit tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$stagedAppClasses;$patchedGstreamerJar;$appClassesDir;$libraryDir\*" `
            com.github.serezhka.airplay.app.control.LocalControlServerTest
    if ($LASTEXITCODE -ne 0) {
        throw "Packaged local launcher control tests failed with exit code $LASTEXITCODE"
    }

    foreach ($probe in @('normal-process-probe', 'blocked-process-probe',
            'production-timeout-process-probe')) {
        & $java -cp "$testOutput;$stagedAppClasses;$patchedGstreamerJar;$appClassesDir;$libraryDir\*" `
                com.github.serezhka.airplay.app.menu.SystemTrayMenuQuitTest $probe
        if ($LASTEXITCODE -ne 0) {
            throw "Packaged system tray quit process probe '$probe' failed with exit code $LASTEXITCODE"
        }
    }

    Copy-Item -LiteralPath $sourceJar -Destination $candidateJar

    Push-Location $fatJarStage
    try {
        & $jar --update --file $candidateJar --no-compress `
                $serverEntry $gstreamerEntry $ffmpegEntry $volumeControllerEntry $airPlayVolumeEntry `
                $playerConfigEntry $systemTrayMenuEntry $trayIconEntry `
                $localControlServerEntry $localControlResultEntry $localControlRejectedEntry `
                $applicationShutdownEntry
        if ($LASTEXITCODE -ne 0) {
            throw "Could not patch executable JAR (exit code $LASTEXITCODE)"
        }
    } finally {
        Pop-Location
    }

    & $jar --list --file $candidateJar | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Patched JAR validation failed with exit code $LASTEXITCODE"
    }

    if (-not (Test-Path -LiteralPath $launcherIcon)) {
        throw "Launcher icon does not exist: $launcherIcon"
    }
    & $jar --create --file $candidateLauncherJar `
            --main-class com.github.serezhka.airplay.launcher.AirPlayLauncher `
            -C $launcherOutput . `
            -C $appClassesDir 'menu/tray_icon.png'
    if ($LASTEXITCODE -ne 0) {
        throw "Launcher JAR packaging failed with exit code $LASTEXITCODE"
    }

    & $jar --list --file $candidateLauncherJar | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Launcher JAR validation failed with exit code $LASTEXITCODE"
    }

    & $java '-Djava.awt.headless=true' -cp "$launcherTestOutput;$candidateLauncherJar" `
            com.github.serezhka.airplay.launcher.LauncherCoreTest
    if ($LASTEXITCODE -ne 0) {
        throw "Packaged launcher tests failed with exit code $LASTEXITCODE"
    }

    $validationJavaBin = Join-Path $launcherValidationDir 'jre\bin'
    [void][IO.Directory]::CreateDirectory($validationJavaBin)
    Copy-Item -LiteralPath $candidateJar `
            -Destination (Join-Path $launcherValidationDir 'java-airplay-server-fixed.jar')
    Copy-Item -LiteralPath $javaw -Destination (Join-Path $validationJavaBin 'javaw.exe')
    & $java '-Djava.awt.headless=true' -jar $candidateLauncherJar --validate `
            "--base-dir=$launcherValidationDir"
    if ($LASTEXITCODE -ne 0) {
        throw "Launcher installation validation failed with exit code $LASTEXITCODE"
    }

    Copy-Item -LiteralPath $candidateJar -Destination $outputJar -Force
    Copy-Item -LiteralPath $candidateLauncherJar -Destination $launcherOutputJar -Force
    Write-Output "Created $outputJar"
    Write-Output "Created $launcherOutputJar"
} finally {
    if (Test-Path -LiteralPath $patchTempRoot) {
        $resolvedTemp = [IO.Path]::GetFullPath($patchTempRoot)
        $systemTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        if ($resolvedTemp.StartsWith($systemTemp, [StringComparison]::OrdinalIgnoreCase) `
                -and [IO.Path]::GetFileName($resolvedTemp).StartsWith('airplay-patch-')) {
            Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
        }
    }
}
