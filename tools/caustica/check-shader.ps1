param(
    [string]$Caustica = 'F:/Caustica',
    [string]$Java = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.7-hotspot/bin'
)
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtimeClasses = Join-Path $Caustica 'packages/slang-runtime/build/classes/java/main'
$compiler = Join-Path $Caustica 'packages/slang-runtime/build/generated/slang-runtime/caustica/natives/slang/2026.14.1/windows-x64'
$destination = Join-Path $workspace 'build/shader-check'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
& (Join-Path $Java 'javac.exe') -cp $runtimeClasses -d $destination (Join-Path $PSScriptRoot 'ShaderCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Shader check helper compilation failed' }
& (Join-Path $Java 'java.exe') --enable-native-access=ALL-UNNAMED -cp "$destination;$runtimeClasses" dev.comfyfluffy.caustica.slang.ShaderCheck $compiler (Join-Path $workspace 'src/main/resources/assets/halo/shaders/caustica') (Join-Path $Caustica 'packages/shader-api/src/main/resources/caustica/shaders/api') (Join-Path $PSScriptRoot 'shader-check.slang') (Join-Path $destination 'halo.spv')
if ($LASTEXITCODE -ne 0) { throw 'Halo surface/coverage Slang compilation failed' }
