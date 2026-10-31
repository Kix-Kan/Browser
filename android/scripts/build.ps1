param(
    [string]$SdkPath = $env:ANDROID_HOME,
    [string]$JavaPath = $env:JAVA_HOME,
    [ValidateSet('testDebugUnitTest','lintDebug','assembleDebug','assembleDebugAndroidTest')]
    [string[]]$Tasks = @('testDebugUnitTest','lintDebug','assembleDebug','assembleDebugAndroidTest'),
    [switch]$Offline
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$androidProject = Split-Path -Parent $PSScriptRoot
$projectsDirectory = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $androidProject))
$toolsDirectory = Join-Path $projectsDirectory '.mobile-sim-tools'
if (-not $SdkPath) { $SdkPath = Join-Path $toolsDirectory 'android-sdk' }
if (-not $JavaPath) { $JavaPath = Join-Path $toolsDirectory 'jdk/jdk-17.0.20+8' }
if (-not (Test-Path -LiteralPath (Join-Path $SdkPath 'platform-tools/adb.exe'))) { throw 'Existing Android SDK required; pass -SdkPath. No tools or licenses are installed.' }
if (-not (Test-Path -LiteralPath (Join-Path $JavaPath 'bin/java.exe'))) { throw 'Existing JDK17 required; pass -JavaPath.' }
if (-not (Test-Path -LiteralPath (Join-Path $SdkPath 'platforms/android-37.2/android.jar') -PathType Leaf)) {
    throw 'GeckoView159 requires the installed Android37.2 compile platform (platforms;android-37.2). Review/install it separately with its licenses; this helper never installs or accepts agreements.'
}
$previous = @{}
foreach($name in @('JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT')) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaPath).Path
    $env:ANDROID_HOME = (Resolve-Path -LiteralPath $SdkPath).Path
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    $arguments = @('--project-dir',$androidProject,'--no-daemon','--console=plain') + $Tasks
    if($Offline) { $arguments += '--offline' }
    & (Join-Path $androidProject 'gradlew.bat') @arguments
    if($LASTEXITCODE -ne 0) { throw "Android Browser Gradle failed: $LASTEXITCODE" }
} finally {
    foreach($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
