# Stimmzug - QA helpers for exploratory testing via adb (Windows PowerShell 5.1+).
#
# Usage (from the repository root):
#   . .\tools\qa\adb.ps1                 # dot-source to load the functions
#   Get-QaDevices
#   Set-QaDevice emulator-5554           # optional when only one device is connected
#   Install-QaApk; Start-QaApp; Wait-QaApp
#   Get-QaScreenshot start               # -> build\qa\screens\<timestamp>-start.png
#   Invoke-QaTap -Text 'Start'           # or -ResourceId <Compose test tag>
#   Set-QaDarkMode yes; Wait-QaApp       # configuration changes recreate the activity
#   Get-QaLogcat -Lines 200; Get-QaCrashLog
#   Get-QaDeviceInfo                     # model, Android version, audio capabilities (docs/devices.md)
#   Start-QaLab selbsttest; Get-QaLabSessions -Latest   # audio lab (debug build), see docs/field-test/audio-spike.md
#
# Binary output (screenshots, UI dumps) is written on the device and pulled, because PowerShell 5.1
# corrupts binary data piped from native commands.

$script:QaAdb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' } else { 'adb' }
$script:QaPackage = 'io.github.blanaboo.stimmzug.debug'
$script:QaActivity = 'io.github.blanaboo.stimmzug.MainActivity'
$script:QaSerial = $null
$script:QaOutDir = Join-Path (Get-Location) 'build\qa'

function Invoke-QaAdb {
    <# Runs adb against the selected device and returns its text output. #>
    $deviceArgs = @()
    if ($script:QaSerial) { $deviceArgs = @('-s', $script:QaSerial) }
    $output = & $script:QaAdb @deviceArgs @args 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "adb $($args -join ' ') failed ($LASTEXITCODE): $($output -join "`n")" }
    $output
}

function Get-QaDevices {
    & $script:QaAdb devices -l | Select-Object -Skip 1 | Where-Object { $_ -match '\S' }
}

function Set-QaDevice {
    param([Parameter(Mandatory)] [string] $Serial)
    $script:QaSerial = $Serial
}

function Set-QaPackage {
    <# Switch between the debug build (default) and the release build ('io.github.blanaboo.stimmzug'). #>
    param([Parameter(Mandatory)] [string] $Package)
    $script:QaPackage = $Package
}

function Get-QaOutDir([string] $Sub) {
    $dir = Join-Path $script:QaOutDir $Sub
    New-Item -ItemType Directory -Force $dir | Out-Null
    $dir
}

function Install-QaApk {
    param([string] $Path = 'app\build\outputs\apk\debug\app-debug.apk')
    Invoke-QaAdb install -r -t $Path
}

function Start-QaApp {
    <# Starts the app. -Extras @{ audio_input = 'scenario' } become string intent extras (--es). #>
    param([hashtable] $Extras = @{})
    $extraArgs = foreach ($key in $Extras.Keys) { '--es'; $key; "$($Extras[$key])" }
    Invoke-QaAdb shell am start -W -n "$script:QaPackage/$script:QaActivity" @extraArgs
}

function Stop-QaApp { Invoke-QaAdb shell am force-stop $script:QaPackage | Out-Null }

function Wait-QaApp {
    <#
    Waits until the app window has focus again, then lets it settle. Use it after Start-QaApp and after
    configuration changes (Set-QaDarkMode/-FontScale/-Rotation recreate the activity): a screenshot taken during
    the transition is black.
    #>
    param([int] $TimeoutSec = 20, [int] $SettleMs = 2000)
    $pattern = "mCurrentFocus=.*$([regex]::Escape($script:QaPackage))/"
    $watch = [Diagnostics.Stopwatch]::StartNew()
    while (-not ((Invoke-QaAdb shell dumpsys window) -match $pattern)) {
        if ($watch.Elapsed.TotalSeconds -gt $TimeoutSec) { throw "app window not focused after $TimeoutSec s" }
        Start-Sleep -Milliseconds 500
    }
    Start-Sleep -Milliseconds $SettleMs
}

function Reset-QaApp { Invoke-QaAdb shell pm clear $script:QaPackage }

function Set-QaPermission {
    param(
        [ValidateSet('grant', 'revoke')] [string] $Action = 'grant',
        [string] $Permission = 'android.permission.RECORD_AUDIO'
    )
    # pm grant/revoke silently ignore permissions the app does not request, so check that first.
    $requested = Invoke-QaAdb shell dumpsys package $script:QaPackage | Select-String -SimpleMatch "${Permission}:"
    if (-not $requested) { throw "$script:QaPackage does not request $Permission" }
    Invoke-QaAdb shell pm $Action $script:QaPackage $Permission
}

function Get-QaScreenshot {
    <# Saves a PNG screenshot under build\qa\screens and returns its path. #>
    param([string] $Name = 'screen')
    $file = Join-Path (Get-QaOutDir 'screens') ("{0:yyyyMMdd-HHmmss}-{1}.png" -f (Get-Date), $Name)
    Invoke-QaAdb shell screencap -p /sdcard/qa-screen.png | Out-Null
    Invoke-QaAdb pull /sdcard/qa-screen.png $file | Out-Null
    Invoke-QaAdb shell rm /sdcard/qa-screen.png | Out-Null
    $file
}

function Get-QaUiDump {
    <#
    Returns the current UI hierarchy as [xml]. Compose test tags appear as resource-id because MainActivity sets
    testTagsAsResourceId. uiautomator exits with 0 even when it fails (e.g. "null root node" while the UI is busy)
    and then writes no file, so the old file is removed first and failures are retried.
    #>
    param([int] $Attempts = 3)
    $file = Join-Path (Get-QaOutDir 'ui') ("{0:yyyyMMdd-HHmmss-fff}.xml" -f (Get-Date))
    $remote = '/sdcard/qa-ui.xml'
    for ($i = 1; $i -le $Attempts; $i++) {
        Invoke-QaAdb shell rm -f $remote | Out-Null
        $out = (Invoke-QaAdb shell uiautomator dump $remote) -join ' '
        if ($out -notmatch 'ERROR') {
            Invoke-QaAdb pull $remote $file | Out-Null
            Invoke-QaAdb shell rm -f $remote | Out-Null
            return [xml](Get-Content $file -Raw -Encoding UTF8)
        }
        Start-Sleep -Seconds 1
    }
    throw "uiautomator dump failed $Attempts times: $out"
}

function Find-QaNode {
    <# Finds UI nodes by (partial) text, content description or resource-id/test tag. #>
    param([string] $Text, [string] $Description, [string] $ResourceId)
    $nodes = (Get-QaUiDump).SelectNodes('//node')
    $nodes | Where-Object {
        ($Text -and $_.text -like "*$Text*") -or
        ($Description -and $_.'content-desc' -like "*$Description*") -or
        ($ResourceId -and $_.'resource-id' -like "*$ResourceId")
    }
}

function Invoke-QaTap {
    <# Taps a node found by text/description/resource-id, or explicit coordinates. #>
    param([string] $Text, [string] $Description, [string] $ResourceId, [int] $X = -1, [int] $Y = -1)
    if ($X -lt 0) {
        $node = Find-QaNode -Text $Text -Description $Description -ResourceId $ResourceId | Select-Object -First 1
        if (-not $node) { throw "No node found (Text='$Text' Description='$Description' ResourceId='$ResourceId')" }
        $b = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
        $X = [int](($b[0] + $b[2]) / 2); $Y = [int](($b[1] + $b[3]) / 2)
    }
    Invoke-QaAdb shell input tap $X $Y | Out-Null
    "tap $X,$Y"
}

function Send-QaKey { param([Parameter(Mandatory)] [string] $Key) Invoke-QaAdb shell input keyevent $Key | Out-Null }

function Get-QaLogcat {
    <# Logcat lines of the running app process (or everything if it is not running). #>
    param([int] $Lines = 300)
    # pidof exits with 1 when the app is not running, which Invoke-QaAdb turns into an exception.
    $appPid = ''
    try { $appPid = (Invoke-QaAdb shell pidof $script:QaPackage) -join '' } catch { }
    if ($appPid -match '^\d+') { Invoke-QaAdb logcat -d -t $Lines --pid=$($Matches[0]) }
    else { Invoke-QaAdb logcat -d -t $Lines }
}

function Get-QaCrashLog { Invoke-QaAdb logcat -d -b crash }

function Clear-QaLogcat { Invoke-QaAdb logcat -c | Out-Null }

function Set-QaFontScale { param([double] $Scale = 1.0) Invoke-QaAdb shell settings put system font_scale $Scale }

function Set-QaDarkMode { param([ValidateSet('yes', 'no', 'auto')] [string] $Mode = 'yes') Invoke-QaAdb shell cmd uimode night $Mode }

function Set-QaRotation {
    <# 0 = portrait, 1 = landscape, 2 = reverse portrait, 3 = reverse landscape. #>
    param([ValidateRange(0, 3)] [int] $Rotation = 0)
    Invoke-QaAdb shell settings put system accelerometer_rotation 0 | Out-Null
    Invoke-QaAdb shell settings put system user_rotation $Rotation | Out-Null
}

function Get-QaDeviceInfo {
    <#
    Collects the facts recorded per device in docs/devices.md. UnprocessedSource is the framework resource that
    AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) reads, with vendor overlays applied.
    #>
    $prop = { param($Name) ((Invoke-QaAdb shell getprop $Name) -join '').Trim() }
    $unprocessed = try {
        ((Invoke-QaAdb shell cmd overlay lookup android android:bool/config_supportAudioSourceUnprocessed) -join '').Trim()
    } catch { 'unknown (cmd overlay lookup needs Android 11+)' }
    $features = Invoke-QaAdb shell pm list features
    $hasFeature = { param($Name) [bool]($features -contains "feature:$Name") }

    # Attached input devices are listed with two spaces of indentation, mix port entries deeper.
    $policy = Invoke-QaAdb shell dumpsys media.audio_policy
    $inputs = foreach ($line in $policy) {
        if ($line -match '^  \d+\. Port ID: \d+; "([^"]+)"; \{(AUDIO_DEVICE_IN_\w+)') { "$($Matches[1]) ($($Matches[2]))" }
    }
    # Each output thread prints its HAL frame count before its stream flags.
    $fastFrames = $null
    $halFrames = $null
    foreach ($line in (Invoke-QaAdb shell dumpsys media.audio_flinger)) {
        if ($line -match 'HAL frame count: (\d+)') { $halFrames = [int]$Matches[1] }
        if ($line -match 'AudioStreamOut: .*\(AUDIO_OUTPUT_FLAG_FAST\)' -and -not $fastFrames) { $fastFrames = $halFrames }
    }

    [pscustomobject][ordered]@{
        Manufacturer      = & $prop ro.product.manufacturer
        Model             = & $prop ro.product.model
        Device            = & $prop ro.product.device
        Android           = & $prop ro.build.version.release
        Sdk               = & $prop ro.build.version.sdk
        FirstApiLevel     = & $prop ro.product.first_api_level
        Build             = & $prop ro.build.display.id
        SecurityPatch     = & $prop ro.build.version.security_patch
        Soc               = '{0} {1}' -f (& $prop ro.soc.manufacturer), (& $prop ro.soc.model)
        Abi               = & $prop ro.product.cpu.abi
        UnprocessedSource = $unprocessed
        LowLatencyAudio   = & $hasFeature android.hardware.audio.low_latency
        ProAudio          = & $hasFeature android.hardware.audio.pro
        AAudioMmapPolicy  = & $prop aaudio.mmap_policy
        MmapInput         = [bool]($policy -match 'AUDIO_INPUT_FLAG_MMAP_NOIRQ')
        FastOutputFrames  = $fastFrames
        InputDevices      = @($inputs | Select-Object -Unique)
    }
}

function Start-QaLab {
    <#
    Starts the audio lab of the debug build (#33) with a protocol: 'selbsttest' runs on its own (drone through the
    speaker, about 3 minutes), 'tuba' is the guided measurement. The phone must be unlocked.
    #>
    param([ValidateSet('selbsttest', 'tuba')] [string] $Protocol = 'selbsttest')
    Set-QaPermission grant | Out-Null
    Invoke-QaAdb shell am start -W -n "$script:QaPackage/io.github.blanaboo.stimmzug.lab.AudioLabActivity" --es protocol $Protocol
}

function Get-QaLabSessions {
    <#
    Pulls the audio lab's sessions into testdata\local\lab (git-ignored: recordings of real playing stay on this
    machine) and returns the local folders. -Latest pulls only the newest one. Evaluate a session with
    ./gradlew :tools:replay:run --args="testdata/local/lab/<session>"
    #>
    param([switch] $Latest, [string] $Destination = 'testdata\local\lab')
    $remote = "/sdcard/Android/data/$script:QaPackage/files/lab"
    $sessions = @(Invoke-QaAdb shell ls $remote | Where-Object { $_ -match '^\d{8}-\d{6}-\w+$' } | Sort-Object)
    if ($Latest) { $sessions = @($sessions | Select-Object -Last 1) }
    New-Item -ItemType Directory -Force $Destination | Out-Null
    foreach ($session in $sessions) {
        Invoke-QaAdb pull "$remote/$session" $Destination | Out-Null
        Get-Item (Join-Path $Destination $session)
    }
}

function Get-QaMemory {Invoke-QaAdb shell dumpsys meminfo $script:QaPackage | Select-String 'TOTAL|Java Heap|Native Heap' }

function Start-QaMonkey {
    <# Random UI stress test; returns the monkey summary. #>
    param([int] $Events = 2000, [int] $Seed = 42)
    Invoke-QaAdb shell monkey -p $script:QaPackage -s $Seed --throttle 50 --pct-syskeys 0 -v $Events | Select-Object -Last 5
}
