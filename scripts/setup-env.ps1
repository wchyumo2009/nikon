<#
.SYNOPSIS
Prepare the Android build/run environment automatically and build the app.

.DESCRIPTION
Idempotent setup for this project. It checks (and prepares when missing):
  * Temurin JDK 17 (Gradle 8.2 / AGP 8.2 do not run on JDK 25)
  * Android SDK: platform-tools, platforms;android-34, build-tools;34.0.0
  * Android command line tools, SDK licenses
  * Gradle wrapper (reuses an existing cached Gradle 8.2 distribution)
  * local.properties with sdk.dir
Then it builds app-debug.apk and installs/starts it on a connected device.
Pass -WithEmulator to also create and boot AVDs. By default it prepares two
Huawei-class devices (P50 phone and MatePad tablet); -Device selects one, both,
or the original generic 320x640 AVD.

All tools are stored under %LOCALAPPDATA%\NikonMetronome (ASCII path) so the
Java properties file and native SDK tools never have to parse a Unicode path.
#>
[CmdletBinding()]
param(
    [switch]$WithEmulator,
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [ValidateSet('Default', 'P50', 'Pad', 'All')]
    [string]$Device = 'All',
    [string]$SdkRoot,
    [string]$JdkRoot
)

# Native tools write progress to stderr. With 'Stop', PowerShell turns every
# such line into a terminating NativeCommandError, so keep it non-terminating
# and check $LASTEXITCODE / throw explicitly instead.
$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Tools = Join-Path $env:LOCALAPPDATA 'NikonMetronome'
$Downloads = Join-Path $Tools 'downloads'
$ProjectDownloads = Join-Path $ProjectRoot '.tools\downloads'
$Log = Join-Path $Tools 'setup.log'
New-Item -ItemType Directory -Force -Path $Tools, $Downloads | Out-Null

$JdkUrl = 'https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.zip'
$JdkFallbackUrl = 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse'
$CmdlineToolsUrl = 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip'
$GradleUrl = 'https://services.gradle.org/distributions/gradle-8.2-bin.zip'
$GradleVersion = '8.2'

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Write-Log([string]$Message) {
    Add-Content -Path $Log -Value ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $Message)
}

function Get-Download([string]$FileName, [string]$Url, [string]$FallbackUrl) {
    $target = Join-Path $Downloads $FileName
    if (Test-Path $target) {
        Write-Host "    cached: $target"
        return $target
    }
    $projectCopy = Join-Path $ProjectDownloads $FileName
    if (Test-Path $projectCopy) {
        Write-Host "    cached: $projectCopy"
        return $projectCopy
    }

    foreach ($candidate in @($Url, $FallbackUrl)) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        Write-Host "    downloading: $candidate"
        $part = "$target.part"
        & curl.exe -fLsS --retry 3 --connect-timeout 20 --max-time 1800 -o $part $candidate
        if ($LASTEXITCODE -eq 0) {
            Move-Item -Force $part $target
            return $target
        }
        Write-Log "download failed ($LASTEXITCODE): $candidate"
        if (Test-Path $part) { Remove-Item -Force $part }
    }
    throw "Unable to download $FileName"
}

function Expand-Zip([string]$Zip, [string]$Destination) {
    if (Test-Path $Destination) { Remove-Item -Recurse -Force $Destination }
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    & tar.exe -xf $Zip -C $Destination
    if ($LASTEXITCODE -ne 0) {
        Write-Host '    tar failed, falling back to Expand-Archive'
        Expand-Archive -Path $Zip -DestinationPath $Destination -Force
    }
}

function Test-Jdk17([string]$JdkHome) {
    if ([string]::IsNullOrWhiteSpace($JdkHome)) { return $false }
    $java = Join-Path $JdkHome 'bin\java.exe'
    if (-not (Test-Path $java)) { return $false }
    $version = (& $java -version 2>&1 | Out-String)
    return $version -match 'version\s+"17\.'
}

function Ensure-Jdk17 {
    Write-Step 'Checking JDK 17'
    if ($JdkRoot -and (Test-Jdk17 $JdkRoot)) {
        Write-Host "    using supplied JDK: $JdkRoot"
        return $JdkRoot
    }
    if (Test-Jdk17 $env:JAVA_HOME) {
        Write-Host "    using JAVA_HOME: $env:JAVA_HOME"
        return $env:JAVA_HOME
    }
    $local = Join-Path $Tools 'jdk17'
    if (Test-Jdk17 $local) {
        Write-Host "    using prepared JDK: $local"
        return $local
    }

    try {
        $index = Invoke-WebRequest -Uri 'https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/' `
            -UseBasicParsing -TimeoutSec 20
        $match = [regex]::Matches($index.Content,
            'OpenJDK17U-jdk_x64_windows_hotspot_[^"<> ]+\.zip') | Select-Object -Last 1
        if ($match) {
            $JdkUrl = 'https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/' + $match.Value
        }
    }
    catch {
        Write-Log 'Could not read Adoptium mirror index; using the pinned URL'
    }

    $zip = Get-Download 'jdk17.zip' $JdkUrl $JdkFallbackUrl
    Write-Host '    extracting JDK 17 (about 190 MB)...'
    $temp = Join-Path $Tools 'jdk17-extract'
    Expand-Zip $zip $temp
    $found = Get-ChildItem -Path $temp -Directory -Recurse -Filter 'bin' -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'java.exe') } |
        Select-Object -First 1
    if (-not $found) { throw 'JDK archive did not contain bin/java.exe' }
    $jdkHome = Split-Path -Parent $found.FullName
    if (Test-Path $local) { Remove-Item -Recurse -Force $local }
    Move-Item -Force $jdkHome $local
    Remove-Item -Recurse -Force $temp -ErrorAction SilentlyContinue
    if (-not (Test-Jdk17 $local)) { throw 'Prepared JDK is not version 17' }
    Write-Host "    prepared JDK: $local"
    return $local
}

function Convert-ToJavaPropertyValue([string]$Value) {
    $builder = New-Object System.Text.StringBuilder
    foreach ($char in $Value.ToCharArray()) {
        $code = [int][char]$char
        if ($char -eq '\') { [void]$builder.Append('\\') }
        elseif ($char -eq ':' -or $char -eq '=') { [void]$builder.Append('\').Append($char) }
        elseif ($code -gt 126 -or $code -lt 32) { [void]$builder.Append('\u').Append($code.ToString('x4')) }
        else { [void]$builder.Append($char) }
    }
    return $builder.ToString()
}

function Ensure-AndroidSdk([string]$JdkHome) {
    Write-Step 'Checking Android SDK'
    $sdk = if ($SdkRoot) { $SdkRoot } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    New-Item -ItemType Directory -Force -Path $sdk | Out-Null

    $platformJar = Join-Path $sdk 'platforms\android-34\android.jar'
    $aapt2 = Join-Path $sdk 'build-tools\34.0.0\aapt2.exe'
    $adb = Join-Path $sdk 'platform-tools\adb.exe'
    $sdkmanager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'

    if ((Test-Path $platformJar) -and (Test-Path $aapt2) -and (Test-Path $adb)) {
        Write-Host "    SDK ready: $sdk"
        return $sdk
    }

    if (-not (Test-Path $sdkmanager)) {
        $zip = Get-Download 'cmdline-tools.zip' $CmdlineToolsUrl ''
        Write-Host '    extracting Android command line tools...'
        $temp = Join-Path $Tools 'cmdline-extract'
        Expand-Zip $zip $temp
        $src = Join-Path $temp 'cmdline-tools'
        if (-not (Test-Path $src)) { throw 'cmdline-tools archive has unexpected layout' }
        $parent = Join-Path $sdk 'cmdline-tools'
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
        $latest = Join-Path $parent 'latest'
        if (Test-Path $latest) { Remove-Item -Recurse -Force $latest }
        Move-Item -Force $src $latest
        Remove-Item -Recurse -Force $temp -ErrorAction SilentlyContinue
    }

    $licenses = Join-Path $sdk 'licenses'
    New-Item -ItemType Directory -Force -Path $licenses | Out-Null
    Set-Content -Path (Join-Path $licenses 'android-sdk-license') -NoNewline -Encoding ascii `
        -Value "8933bad161af4178b1185d1a37fbf41ea5269c55`n24333f8a63b6825ea9c5514f83c2829b004d1fee"
    Set-Content -Path (Join-Path $licenses 'android-sdk-preview-license') -NoNewline -Encoding ascii `
        -Value '84831b9409646a918e30573bab4c9c91346e58'

    Write-Host '    installing platform-tools / android-34 / build-tools 34.0.0...'
    $sdkLog = Join-Path $Tools 'sdkmanager.log'
    & $sdkmanager "--sdk_root=$sdk" 'platform-tools' 'platforms;android-34' 'build-tools;34.0.0' `
        *>&1 | Out-File -FilePath $sdkLog -Encoding utf8
    if ($LASTEXITCODE -ne 0) {
        Get-Content $sdkLog -Tail 40
        throw "sdkmanager failed (exit $LASTEXITCODE). See $sdkLog"
    }
    if (-not (Test-Path $platformJar) -or -not (Test-Path $aapt2) -or -not (Test-Path $adb)) {
        throw 'SDK packages were not installed completely'
    }
    Write-Host "    SDK ready: $sdk"
    return $sdk
}

function Write-LocalProperties([string]$Sdk) {
    $file = Join-Path $ProjectRoot 'local.properties'
    $lines = @()
    if (Test-Path $file) {
        $lines = @(Get-Content $file | Where-Object { $_ -notmatch '^\s*sdk\.dir\s*=' })
    }
    $lines += 'sdk.dir=' + (Convert-ToJavaPropertyValue $Sdk)
    Set-Content -Path $file -Value $lines -Encoding ascii
    Write-Host "    wrote sdk.dir to local.properties"
}

function Ensure-GradleWrapper([string]$JdkHome) {
    Write-Step 'Checking Gradle wrapper'
    $jar = Join-Path $ProjectRoot 'gradle\wrapper\gradle-wrapper.jar'
    if (Test-Path $jar) {
        Write-Host '    wrapper already present'
        return
    }

    $gradleBat = $null
    $dists = Join-Path $env:USERPROFILE '.gradle\wrapper\dists'
    if (Test-Path $dists) {
        $cached = Get-ChildItem -Path $dists -Recurse -Filter 'gradle.bat' -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match 'gradle-8\.2' } |
            Select-Object -First 1
        if ($cached) { $gradleBat = $cached.FullName }
    }
    if (-not $gradleBat) {
        $zip = Get-Download "gradle-$GradleVersion-bin.zip" $GradleUrl ''
        $temp = Join-Path $Tools "gradle-$GradleVersion"
        Expand-Zip $zip $temp
        $gradleBat = Join-Path $temp "gradle-$GradleVersion\bin\gradle.bat"
    }
    if (-not (Test-Path $gradleBat)) { throw 'Could not locate a Gradle installation' }

    Write-Host '    generating wrapper files...'
    Push-Location $ProjectRoot
    try {
        & $gradleBat wrapper --gradle-version $GradleVersion --distribution-type bin `
            --no-daemon --console=plain *>&1 | Out-File (Join-Path $Tools 'wrapper.log') -Encoding utf8
    }
    finally { Pop-Location }
    if (-not (Test-Path $jar)) {
        Get-Content (Join-Path $Tools 'wrapper.log') -Tail 30
        throw 'Gradle wrapper generation failed'
    }
    Write-Host '    wrapper ready'
}

function Invoke-Build {
    Write-Step 'Building app-debug.apk'
    $gradlew = Join-Path $ProjectRoot 'gradlew.bat'
    if (-not (Test-Path $gradlew)) { throw 'gradlew.bat is missing' }
    $buildLog = Join-Path $Tools 'build.log'
    & $gradlew assembleDebug --no-daemon --console=plain *>&1 |
        Out-File -FilePath $buildLog -Encoding utf8
    if ($LASTEXITCODE -ne 0) {
        Get-Content $buildLog -Tail 60 | ForEach-Object { Write-Host "    $_" }
        throw "Gradle build failed (exit $LASTEXITCODE). See $buildLog"
    }
    Get-Content $buildLog -Tail 12 | ForEach-Object { Write-Host "    $_" }
    $apk = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $apk)) { throw "APK not found at $apk" }
    Write-Host ("    APK: {0} ({1:N1} MB)" -f $apk, ((Get-Item $apk).Length / 1MB))
    return $apk
}

function Get-OnlineDevices([string]$Adb) {
    $list = & $Adb devices 2>&1 | Out-String
    return @($list -split "`r?`n" | Where-Object { $_ -match '\tdevice\s*$' })
}

function Get-OnlineSerials([string]$Adb) {
    return @(Get-OnlineDevices $Adb | ForEach-Object {
        ($_ -split '\s+')[0].Trim()
    } | Where-Object { $_ })
}

# Display metrics of the two Huawei devices we validate against. The emulator
# runs AOSP Android 14: these values reproduce the screen (and therefore the
# layout qualifiers) of the real hardware, not EMUI / HarmonyOS itself.
$DeviceProfiles = @(
    [pscustomobject]@{
        Key = 'P50'; Avd = 'nikon_huawei_p50'; Port = 5556
        Width = 1224; Height = 2700; Density = 460
        Label = 'HUAWEI P50 (1224x2700, 460 dpi, portrait)'
    },
    [pscustomobject]@{
        Key = 'Pad'; Avd = 'nikon_huawei_pad'; Port = 5558
        Width = 2560; Height = 1600; Density = 280
        Label = 'HUAWEI MatePad 11 (2560x1600, 280 dpi, landscape)'
    }
)

function Get-TargetProfiles([string]$Selection) {
    if ($Selection -eq 'All') { return @($DeviceProfiles) }
    return @($DeviceProfiles | Where-Object { $_.Key -eq $Selection })
}

function Get-AvdHome {
    if ($env:ANDROID_AVD_HOME) { return $env:ANDROID_AVD_HOME }
    return Join-Path $env:USERPROFILE '.android\avd'
}

function Ensure-EmulatorComponents([string]$Sdk) {
    $emulatorExe = Join-Path $Sdk 'emulator\emulator.exe'
    $systemImage = Join-Path $Sdk 'system-images\android-34\google_apis\x86_64\system.img'
    if ((Test-Path $emulatorExe) -and (Test-Path $systemImage)) {
        Write-Host '    emulator and system image already installed'
        return $emulatorExe
    }
    $sdkmanager = Join-Path $Sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
    Write-Host '    downloading emulator and system image (about 1.5 GB)...'
    & $sdkmanager "--sdk_root=$Sdk" 'emulator' 'system-images;android-34;google_apis;x86_64' `
        *>&1 | Out-File (Join-Path $Tools 'emulator-sdkmanager.log') -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw 'Failed to install emulator components' }
    if (-not (Test-Path $emulatorExe)) { throw "emulator.exe not found at $emulatorExe" }
    return $emulatorExe
}

function Set-AvdScreen([string]$AvdName, $Spec) {
    $config = Join-Path (Get-AvdHome) ($AvdName + '.avd\config.ini')
    if (-not (Test-Path $config)) { throw "AVD config not found: $config" }
    $lines = @(Get-Content $config | Where-Object {
        $_ -notmatch '^\s*hw\.lcd\.(width|height|density)\s*=' -and
        $_ -notmatch '^\s*hw\.mainKeys\s*='
    })
    $lines += "hw.lcd.width = $($Spec.Width)"
    $lines += "hw.lcd.height = $($Spec.Height)"
    $lines += "hw.lcd.density = $($Spec.Density)"
    # Huawei phones/tablets use on-screen navigation instead of hardware keys.
    $lines += 'hw.mainKeys = no'
    [System.IO.File]::WriteAllLines($config, [string[]]$lines,
        (New-Object System.Text.UTF8Encoding($false)))
    # Emulator caches hardware values here; drop it so config.ini wins.
    $cached = Join-Path (Split-Path -Parent $config) 'hardware-qemu.ini'
    if (Test-Path $cached) { Remove-Item -Force $cached }
    Write-Host ("    screen: {0}x{1} @ {2} dpi" -f $Spec.Width, $Spec.Height, $Spec.Density)
}

function Ensure-Avd([string]$Sdk, $Spec) {
    $ini = Join-Path (Get-AvdHome) ($Spec.Avd + '.ini')
    if (Test-Path $ini) {
        Write-Host "    reusing AVD $($Spec.Avd)"
    }
    else {
        $avdmanager = Join-Path $Sdk 'cmdline-tools\latest\bin\avdmanager.bat'
        Write-Host "    creating AVD $($Spec.Avd)..."
        # "no" answers the optional custom-hardware prompt without blocking.
        # cmd's echo keeps the reply as plain ASCII stdin. PowerShell pipes
        # strings as UTF-16 here, which Java reads as "??no" and rejects.
        $create = 'echo no | "' + $avdmanager + '" create avd --force --name ' +
            $Spec.Avd + ' --abi google_apis/x86_64' +
            ' --package system-images;android-34;google_apis;x86_64'
        & cmd.exe /c $create *>&1 | Out-File (Join-Path $Tools 'avdmanager.log') -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw "Failed to create AVD $($Spec.Avd)" }
    }
    Set-AvdScreen $Spec.Avd $Spec
}

function Apply-DeviceProfile([string]$Adb, [string]$Serial, $Spec) {
    # Enforce the exact metrics through WindowManager too: some emulator builds
    # ignore hw.lcd.* from config.ini or restore a saved snapshot.
    & $Adb -s $Serial shell wm size "$($Spec.Width)x$($Spec.Height)" 2>&1 | Out-Null
    & $Adb -s $Serial shell wm density "$($Spec.Density)" 2>&1 | Out-Null
    & $Adb -s $Serial shell settings put system accelerometer_rotation 0 2>&1 | Out-Null
    & $Adb -s $Serial shell settings put system user_rotation 0 2>&1 | Out-Null
    $size = (& $Adb -s $Serial shell wm size 2>&1 | Out-String)
    $density = (& $Adb -s $Serial shell wm density 2>&1 | Out-String)
    if ($size -notmatch "$($Spec.Width)x$($Spec.Height)" -or
        $density -notmatch "$($Spec.Density)") {
        throw "$Serial does not report the requested screen profile:`n$size$density"
    }
    Write-Host ("    {0}: {1}x{2} @ {3} dpi" -f $Serial, $Spec.Width, $Spec.Height, $Spec.Density)
}

function Start-DeviceEmulator([string]$Adb, [string]$EmulatorExe, $Spec) {
    $serial = "emulator-$($Spec.Port)"
    if ((Get-OnlineSerials $Adb) -contains $serial) {
        $boot = (& $Adb -s $serial shell getprop sys.boot_completed 2>&1 | Out-String).Trim()
        if ($boot -match '^1') {
            $runningAvd = (& $Adb -s $serial emu avd name 2>&1 | Out-String)
            if ($runningAvd -notmatch [regex]::Escape($Spec.Avd)) {
                throw "$serial is already used by another AVD ($runningAvd); free the port first"
            }
            Write-Host "    $serial is already booted"
            Apply-DeviceProfile $Adb $serial $Spec
            return $serial
        }
    }
    else {
        Write-Host "    booting $($Spec.Label)..."
        # Host GPU proved stable on WHPX; swiftshader_indirect crashed qemu here.
        Start-Process -FilePath $EmulatorExe -ArgumentList @(
            '-avd', $Spec.Avd, '-port', "$($Spec.Port)",
            '-no-snapshot-load', '-gpu', 'host', '-no-boot-anim'
        ) -WindowStyle Hidden | Out-Null
    }
    $ready = $false
    for ($i = 0; $i -lt 150; $i++) {
        if ((Get-OnlineSerials $Adb) -contains $serial) {
            $boot = (& $Adb -s $serial shell getprop sys.boot_completed 2>&1 | Out-String).Trim()
            if ($boot -match '^1') { $ready = $true; break }
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw "$serial did not finish booting (hardware acceleration may be unavailable)" }
    Start-Sleep -Seconds 3
    Apply-DeviceProfile $Adb $serial $Spec
    return $serial
}

function Ensure-DeviceEmulators([string]$Sdk, [string]$Adb) {
    Write-Step "Preparing Huawei device emulators (-Device $Device)"
    # Other tools may leave an older adb server on port 5037; make sure this
    # SDK's server owns the port before the emulators connect.
    & $Adb kill-server 2>&1 | Out-Null
    & $Adb start-server 2>&1 | Out-Null
    $emulatorExe = Ensure-EmulatorComponents $Sdk
    $serials = @()
    foreach ($spec in (Get-TargetProfiles $Device)) {
        Write-Step $spec.Label
        Ensure-Avd $Sdk $spec
        $serials += Start-DeviceEmulator $Adb $emulatorExe $spec
    }
    return $serials
}

function Ensure-Emulator([string]$Sdk, [string]$Adb) {
    Write-Step 'No device found: preparing an Android emulator'
    # Other tools may leave an older adb server on port 5037; make sure this
    # SDK's server owns the port before the emulator connects.
    & $Adb kill-server 2>&1 | Out-Null
    & $Adb start-server 2>&1 | Out-Null
    $sdkmanager = Join-Path $Sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
    Write-Host '    downloading emulator and system image (about 1.5 GB)...'
    & $sdkmanager "--sdk_root=$Sdk" 'emulator' 'system-images;android-34;google_apis;x86_64' `
        *>&1 | Out-File (Join-Path $Tools 'emulator-sdkmanager.log') -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw 'Failed to install emulator components' }

    $avdmanager = Join-Path $Sdk 'cmdline-tools\latest\bin\avdmanager.bat'
    # "no" answers the optional custom-hardware prompt without blocking.
    # Same ASCII stdin workaround as Ensure-Avd (see the comment there).
    $create = 'echo no | "' + $avdmanager + '" create avd --force --name ' +
        'nikon_metronome --abi google_apis/x86_64' +
        ' --package system-images;android-34;google_apis;x86_64'
    & cmd.exe /c $create *>&1 | Out-File (Join-Path $Tools 'avdmanager.log') -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw 'Failed to create AVD' }

    $emulatorExe = Join-Path $Sdk 'emulator\emulator.exe'
    Write-Host '    booting AVD (hardware acceleration is required)...'
    # Host GPU proved stable on WHPX; swiftshader_indirect crashed qemu here.
    Start-Process -FilePath $emulatorExe `
        -ArgumentList '-avd nikon_metronome -no-snapshot-load -gpu host -no-boot-anim' `
        -WindowStyle Hidden | Out-Null
    $ready = $false
    for ($i = 0; $i -lt 150; $i++) {
        if ((Get-OnlineDevices $Adb).Count -gt 0) {
            $boot = (& $Adb shell getprop sys.boot_completed 2>&1 | Out-String).Trim()
            if ($boot -match '^1') { $ready = $true; break }
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'Emulator did not finish booting (hardware acceleration may be unavailable)' }
    Start-Sleep -Seconds 3
    Write-Host '    emulator ready'
}

function Install-And-Launch([string]$Sdk, [string]$Apk) {
    Write-Step 'Installing and launching on a device'
    $adb = Join-Path $Sdk 'platform-tools\adb.exe'
    if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

    $targets = @()
    if ($WithEmulator -and $Device -ne 'Default') {
        # Huawei profiles boot/reuse their own AVDs and are installed only on
        # those emulators, even when other devices happen to be connected.
        $targets = @(Ensure-DeviceEmulators $Sdk $adb)
    }
    elseif ($WithEmulator -and (Get-OnlineSerials $adb).Count -eq 0) {
        Ensure-Emulator $Sdk $adb
    }
    if ($targets.Count -eq 0) { $targets = @(Get-OnlineSerials $adb) }

    if ($targets.Count -eq 0) {
        Write-Host "    no device/emulator connected. APK is ready:" -ForegroundColor Yellow
        Write-Host "      $Apk"
        Write-Host "    connect a device and run: `"$adb`" install -r `"$Apk`""
        Write-Host '    or re-run this script with -WithEmulator to prepare Huawei device AVDs'
        return
    }
    if ($SkipInstall) {
        Write-Host '    -SkipInstall: emulators are ready, nothing installed'
        return
    }
    if (-not (Test-Path $Apk)) { throw "APK not found at $Apk (run without -SkipBuild)" }

    foreach ($serial in $targets) {
        Write-Step "Installing and launching on $serial"
        $installed = $false
        for ($attempt = 1; $attempt -le 3 -and -not $installed; $attempt++) {
            & $adb -s $serial install -r $Apk 2>&1 | ForEach-Object { Write-Host "    $_" }
            $installed = ($LASTEXITCODE -eq 0)
            if (-not $installed) { Start-Sleep -Seconds 3 }
        }
        if (-not $installed) { throw "adb install failed on $serial after 3 attempts" }
        & $adb -s $serial shell am start -n com.nikon.metronome/.MainActivity
        if ($LASTEXITCODE -ne 0) { throw "Failed to start MainActivity on $serial" }
        Write-Host "    installed and launched on $serial"
    }
}

try {
    Write-Log 'setup-env start'
    $jdkHome = Ensure-Jdk17
    $env:JAVA_HOME = $jdkHome
    $env:PATH = "$jdkHome\bin;$env:PATH"
    Write-Log "JDK: $jdkHome"

    $sdk = Ensure-AndroidSdk $jdkHome
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
    Write-Log "SDK: $sdk"

    Write-LocalProperties $sdk
    Ensure-GradleWrapper $jdkHome

    $apk = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if ($SkipBuild) {
        Write-Step 'Skipping build (-SkipBuild)'
        Write-Host 'Environment is ready.'
    }
    else {
        $apk = Invoke-Build
    }
    if ($WithEmulator -or (-not $SkipInstall -and (Test-Path $apk))) {
        Install-And-Launch $sdk $apk
    }
    elseif (-not $SkipInstall) {
        Write-Step 'Skipping install (no APK found with -SkipBuild)'
    }
    Write-Step 'Done'
    Write-Log 'setup-env finished'
}
catch {
    Write-Log "ERROR: $($_.Exception.Message)"
    Write-Host "`nERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
