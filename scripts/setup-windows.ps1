# MovieShelf first-time Gradle wrapper bootstrap for Windows.
# Downloads the official Gradle distribution and uses its wrapper task to generate
# conventional gradlew / gradlew.bat / gradle-wrapper.jar files in the project root.
$ErrorActionPreference = "Stop"
$version = "8.11.1"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$wrapperJar = Join-Path $root "gradle\wrapper\gradle-wrapper.jar"
if (Test-Path $wrapperJar) {
    Write-Host "Gradle wrapper already exists." -ForegroundColor Green
    exit 0
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $bundledJdk = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path (Join-Path $bundledJdk "bin\java.exe")) {
        $env:JAVA_HOME = $bundledJdk
        $env:PATH = (Join-Path $bundledJdk "bin") + ";" + $env:PATH
    } else {
        throw "Java not found. Install Android Studio and JDK 17 or newer first."
    }
}

$tools = Join-Path $env:LOCALAPPDATA "MovieShelf\gradle-tools"
New-Item -ItemType Directory -Force -Path $tools | Out-Null
$gradleBinary = Join-Path $tools "gradle-$version\bin\gradle.bat"
if (-not (Test-Path $gradleBinary)) {
    $zipFile = Join-Path $tools "gradle-$version-bin.zip"
    if (-not (Test-Path $zipFile)) {
        Write-Host "Downloading Gradle $version from services.gradle.org (first run only)."
        Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$version-bin.zip" -OutFile $zipFile
    }
    Write-Host "Extracting Gradle..."
    Expand-Archive -Path $zipFile -DestinationPath $tools -Force
}

$bootstrap = Join-Path $tools "wrapper-bootstrap"
New-Item -ItemType Directory -Force -Path $bootstrap | Out-Null
Set-Content -Path (Join-Path $bootstrap "settings.gradle.kts") -Value 'rootProject.name = "MovieShelfWrapperBootstrap"'
Set-Content -Path (Join-Path $bootstrap "build.gradle.kts") -Value ''
Write-Host "Generating the standard Gradle wrapper..."
& $gradleBinary -p $bootstrap wrapper --gradle-version $version --distribution-type bin
if ($LASTEXITCODE -ne 0) { throw "Gradle wrapper generation failed" }
New-Item -ItemType Directory -Force -Path (Join-Path $root "gradle\wrapper") | Out-Null
Copy-Item -Force (Join-Path $bootstrap "gradlew") (Join-Path $root "gradlew")
Copy-Item -Force (Join-Path $bootstrap "gradlew.bat") (Join-Path $root "gradlew.bat")
Copy-Item -Force (Join-Path $bootstrap "gradle\wrapper\gradle-wrapper.jar") $wrapperJar
Copy-Item -Force (Join-Path $bootstrap "gradle\wrapper\gradle-wrapper.properties") (Join-Path $root "gradle\wrapper\gradle-wrapper.properties")
Write-Host "Ready! In the project folder run: .\gradlew.bat :composeApp:run" -ForegroundColor Green
