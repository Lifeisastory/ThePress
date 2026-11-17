#Requires -Version 5.1
<#
.SYNOPSIS
创建包含 namespace 包目录的 Kotlin Android Library 模块
.EXAMPLE
powershell -File ./tools/new-android-library.ps1 -ModulePath :core:reader_ui -Namespace com.aster.core.reader_ui -Compose
.EXAMPLE
powershell -File ./tools/new-android-library.ps1 -ModulePath :core:data -Namespace com.aster.core.data
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^:[a-z][a-z0-9_]*(?::[a-z][a-z0-9_]*)*$')]
    [string]$ModulePath,

    [Parameter(Mandatory)]
    [ValidatePattern('^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$')]
    [string]$Namespace,

    [switch]$Compose
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$settingsPath = Join-Path $projectRoot 'settings.gradle.kts'
$moduleDirectory = Join-Path $projectRoot ($ModulePath.Substring(1).Replace(':', '/'))

if (-not (Test-Path -LiteralPath $settingsPath -PathType Leaf)) {
    throw "找不到项目配置：$settingsPath"
}
if (Test-Path -LiteralPath $moduleDirectory) {
    throw "模块目录已存在，不会覆盖：$moduleDirectory"
}

$settingsContent = [System.IO.File]::ReadAllText($settingsPath)
$quotedModulePath = '["'']' + [regex]::Escape($ModulePath) + '["'']'
if ([regex]::IsMatch($settingsContent, $quotedModulePath)) {
    throw "settings.gradle.kts 已包含模块路径：$ModulePath"
}

$composePlugin = ''
$composeFeatures = ''
$composeDependencies = ''
if ($Compose) {
    $composePlugin = "`n    alias(libs.plugins.kotlin.compose)"
    $composeFeatures = @'


    buildFeatures {
        compose = true
    }
'@
    $composeDependencies = @'

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
'@
}

$buildScript = @"
plugins {
    id("thepress.android.library")$composePlugin
}

android {
    namespace = "$Namespace"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }$composeFeatures
}

dependencies {$composeDependencies
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
"@

# 根据 namespace 创建包目录，并用占位文件保留空目录供 Git 跟踪
$packagePath = $Namespace.Replace('.', '/')
foreach ($sourceSet in @('main', 'test', 'androidTest')) {
    $sourceDirectory = Join-Path $moduleDirectory "src/$sourceSet/kotlin"
    $packageDirectory = Join-Path $sourceDirectory $packagePath
    New-Item -ItemType Directory -Path $packageDirectory -Force | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $packageDirectory '.gitkeep'), '')
}

[System.IO.File]::WriteAllText((Join-Path $moduleDirectory 'build.gradle.kts'), $buildScript + "`n")
[System.IO.File]::WriteAllText((Join-Path $moduleDirectory '.gitignore'), "/build`n")
[System.IO.File]::WriteAllText((Join-Path $moduleDirectory 'consumer-rules.pro'), '')
[System.IO.File]::WriteAllText(
    (Join-Path $moduleDirectory 'src/main/AndroidManifest.xml'),
    "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<manifest />`n"
)

# 所有模块文件生成成功后再注册，沿用 settings 文件原有的换行风格
$newLine = if ($settingsContent.Contains("`r`n")) { "`r`n" } else { "`n" }
$includeLine = 'include("' + $ModulePath + '")'
[System.IO.File]::WriteAllText($settingsPath, $settingsContent.TrimEnd() + $newLine + $includeLine + $newLine)

Write-Output "已创建 ${ModulePath}：$moduleDirectory"
Write-Output '请在 Android Studio 中执行 Sync Project with Gradle Files。'
