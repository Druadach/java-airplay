[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$patchRoot = $PSScriptRoot
$sourceJar = Join-Path $patchRoot 'java-airplay-server.jar'
$outputJar = Join-Path $patchRoot 'java-airplay-server-fixed.jar'
$javaBin = Join-Path $patchRoot 'jre\bin'
$javac = Join-Path $javaBin 'javac.exe'
$java = Join-Path $javaBin 'java.exe'
$jar = Join-Path $javaBin 'jar.exe'
$mainSourceRoot = Join-Path $patchRoot 'patch-src\main\java'
$testSourceRoot = Join-Path $patchRoot 'patch-src\test\java'

foreach ($requiredPath in @($sourceJar, $javac, $java, $jar, $mainSourceRoot, $testSourceRoot)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required path does not exist: $requiredPath"
    }
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$patchTempRoot = Join-Path ([IO.Path]::GetTempPath()) ("airplay-patch-" + [guid]::NewGuid().ToString('N'))
$libraryDir = Join-Path $patchTempRoot 'lib'
$mainOutput = Join-Path $patchTempRoot 'classes-main'
$testOutput = Join-Path $patchTempRoot 'classes-test'
$nestedJarDir = Join-Path $patchTempRoot 'nested'
$fatJarStage = Join-Path $patchTempRoot 'fat-stage'
$candidateJar = Join-Path $patchTempRoot 'java-airplay-server-fixed.jar'

try {
    foreach ($directory in @($libraryDir, $mainOutput, $testOutput, $nestedJarDir, $fatJarStage)) {
        [void][IO.Directory]::CreateDirectory($directory)
    }

    $outerJar = [IO.Compression.ZipFile]::OpenRead($sourceJar)
    try {
        foreach ($entry in $outerJar.Entries) {
            if ($entry.FullName -notlike 'BOOT-INF/lib/*.jar') {
                continue
            }

            $target = Join-Path $libraryDir ([IO.Path]::GetFileName($entry.FullName))
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

    $mainSources = @(Get-ChildItem -LiteralPath $mainSourceRoot -Recurse -Filter '*.java' |
            ForEach-Object FullName)
    & $javac --release 17 -encoding UTF-8 -cp "$libraryDir\*" -d $mainOutput $mainSources
    if ($LASTEXITCODE -ne 0) {
        throw "Main source compilation failed with exit code $LASTEXITCODE"
    }

    $testSources = @(Get-ChildItem -LiteralPath $testSourceRoot -Recurse -Filter '*.java' |
            ForEach-Object FullName)
    & $javac --release 17 -encoding UTF-8 -cp "$mainOutput;$libraryDir\*" -d $testOutput $testSources
    if ($LASTEXITCODE -ne 0) {
        throw "Test source compilation failed with exit code $LASTEXITCODE"
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
            com.github.serezhka.airplay.server.internal.handler.session.SessionMediaCoordinatorTest
    if ($LASTEXITCODE -ne 0) {
        throw "Session media takeover tests failed with exit code $LASTEXITCODE"
    }

    & $java -cp "$testOutput;$mainOutput;$libraryDir\*" `
            com.github.serezhka.airplay.player.ffmpeg.FFmpegPlayerAudioRegressionTest
    if ($LASTEXITCODE -ne 0) {
        throw "FFmpeg audio tests failed with exit code $LASTEXITCODE"
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
                'com/github/serezhka/airplay/player/gstreamer/GstPlayer.class'
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
            com.github.serezhka.airplay.server.internal.handler.session.SessionMediaCoordinatorTest
    if ($LASTEXITCODE -ne 0) {
        throw "Packaged session media takeover tests failed with exit code $LASTEXITCODE"
    }

    $serverEntry = 'BOOT-INF/lib/server-1.0.6.jar'
    $gstreamerEntry = 'BOOT-INF/lib/gstreamer-1.0.6.jar'
    $ffmpegEntry = 'BOOT-INF/lib/ffmpeg-1.0.6.jar'
    [void][IO.Directory]::CreateDirectory((Join-Path $fatJarStage 'BOOT-INF\lib'))
    Copy-Item -LiteralPath $patchedServerJar -Destination (Join-Path $fatJarStage $serverEntry)
    Copy-Item -LiteralPath $patchedGstreamerJar -Destination (Join-Path $fatJarStage $gstreamerEntry)
    Copy-Item -LiteralPath $patchedFfmpegJar -Destination (Join-Path $fatJarStage $ffmpegEntry)
    Copy-Item -LiteralPath $sourceJar -Destination $candidateJar

    Push-Location $fatJarStage
    try {
        & $jar --update --file $candidateJar --no-compress $serverEntry $gstreamerEntry $ffmpegEntry
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

    Copy-Item -LiteralPath $candidateJar -Destination $outputJar -Force
    Write-Output "Created $outputJar"
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
