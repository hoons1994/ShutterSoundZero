param(
    [Parameter(Mandatory = $true)][string]$ZigPath,
    [Parameter(Mandatory = $true)][string]$AnnotationJar,
    [string]$JdkPath = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$nativeSource = Join-Path $projectRoot 'app/src/main/cpp'
$hostTests = Join-Path $projectRoot 'app/src/nativeTest'
$outputPath = Join-Path $projectRoot 'build/native-spake2-host'
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$previousZigCache = $env:ZIG_GLOBAL_CACHE_DIR
$env:ZIG_GLOBAL_CACHE_DIR = Join-Path $outputPath 'zig-cache'
function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Native regression command failed: $Program ($LASTEXITCODE)" }
}
# The host compatibility header supplies byte-order macros only. Production C/JNI
# and Java are compiled directly, without changing their error/cleanup paths.
try {
foreach ($source in @('sha512', 'spake2')) {
    Invoke-Checked $ZigPath @('cc', '-target', 'x86_64-windows-gnu', '-D__linux__=1',
        '-I', "$hostTests/include", '-I', "$nativeSource/spake2-c/include", '-c',
        "$nativeSource/spake2-c/$source.c", '-o', "$outputPath/$source.o")
}
Invoke-Checked $ZigPath @('c++', '-target', 'x86_64-windows-gnu', '-shared',
    '-fno-exceptions', '-fno-rtti', '-nostdlib++',
    '-I', "$JdkPath/include", '-I', "$JdkPath/include/win32", '-I', "$nativeSource/spake2-c/include",
    "$nativeSource/spake2_jni.cpp", "$outputPath/sha512.o", "$outputPath/spake2.o", '-o', "$outputPath/spake2.dll")
Invoke-Checked "$JdkPath/bin/javac.exe" @('-cp', $AnnotationJar, '-d', $outputPath,
    "$projectRoot/app/src/main/java/io/github/muntashirakon/crypto/spake2/Spake2Context.java",
    "$projectRoot/app/src/main/java/io/github/muntashirakon/crypto/spake2/Spake2Role.java",
    "$hostTests/java/Spake2NativeRegression.java")
Invoke-Checked "$JdkPath/bin/java.exe" @("-Djava.library.path=$outputPath", '-cp', $outputPath, 'Spake2NativeRegression')
} finally {
    $env:ZIG_GLOBAL_CACHE_DIR = $previousZigCache
}
