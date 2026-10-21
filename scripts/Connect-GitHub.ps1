param([string]$RepositoryUrl='https://github.com/feedham/Codex.git')
$ErrorActionPreference='Stop'
if($RepositoryUrl -notmatch '^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\.git)?/?$'){throw 'Use an HTTPS GitHub repository URL.'}
$projectRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
function Run-Git([string[]]$Arguments){ & git @Arguments; if($LASTEXITCODE -ne 0){throw ('Git failed: '+($Arguments -join ' '))} }
if(-not(Test-Path -LiteralPath (Join-Path $projectRoot '.git'))){Run-Git -Arguments @('init','-b','main')}
Run-Git -Arguments @('config','user.name','Fahmeed Shaukat')
Run-Git -Arguments @('config','user.email','37490614+feedham@users.noreply.github.com')
$remotes=@(& git remote)
if($remotes -contains 'origin'){$existing=& git remote get-url origin;if($existing.TrimEnd('/') -ne $RepositoryUrl.TrimEnd('/')){throw 'Origin already points to a different repository. Review it before changing the connection.'}}
else{Run-Git -Arguments @('remote','add','origin',$RepositoryUrl)}
Run-Git -Arguments @('fetch','origin','main','--tags')
$currentBranch=& git symbolic-ref --short HEAD
& git show-ref --verify --quiet ('refs/heads/'+$currentBranch)
if($LASTEXITCODE -eq 1){Run-Git -Arguments @('reset','--mixed','origin/main')}
elseif($LASTEXITCODE -eq 0){Run-Git -Arguments @('merge','--ff-only','origin/main')}
else{throw 'Could not inspect the local branch.'}
Run-Git -Arguments @('add','--','.gitignore','CHANGELOG.md','VERSIONING.md','NAME-GENDER-MODEL.md','README.md','app','backend','desktop','gradle','scripts','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','AdvocateIcon.png','BackupIcon.png','CreditIcon.png','InsightIcon.png','SrAdvocateIcon.png')
& git diff --cached --quiet
if($LASTEXITCODE -eq 1){Run-Git -Arguments @('commit','-m','Update Bar Member Manager source and version descriptions')}
elseif($LASTEXITCODE -ne 0){throw 'Could not inspect staged changes.'}
Run-Git -Arguments @('push','-u','origin','HEAD')
Write-Host 'Local source is connected to feedham/Codex. Keep CHANGELOG.md updated; use a new tag and release description for each new version. Private datasets and application binaries remain local.'
