param(
    [ValidateSet('Install', 'Run', 'Show', 'Restart', 'Pause', 'Uninstall', 'Check')]
    [string]$Action = 'Show',
    [string]$Address
)
$ErrorActionPreference = 'Stop'
$runtime = Join-Path $env:LOCALAPPDATA 'CodexBar\ControlCenter'
$companionRoot = Join-Path $runtime 'companion'
$centerRoot = Join-Path $companionRoot 'windows-control-center'
$scriptPath = Join-Path $centerRoot 'scripts\windows-control-center.ps1'
$settingsPath = Join-Path $runtime 'settings.json'
$controlPath = Join-Path $runtime 'control.json'
$pausePath = Join-Path $runtime 'paused'
$taskName = 'CodexBar Windows Control Center'
$powershell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$startupLink = Join-Path ([Environment]::GetFolderPath('Startup')) 'CodexBar Windows.lnk'
$menuDirectory = Join-Path ([Environment]::GetFolderPath('Programs')) 'CodexBar Windows'
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$mutexName = "Local\CodexBarWindowsControlCenter-$sid"
$providers = @('claude', 'antigravity', 'gemini', 'codex')
$ports = @{ claude = 43823; antigravity = 43824; gemini = 43821; codex = 43822 }
$names = @{ claude = 'Claude'; antigravity = 'Antigravity'; gemini = 'Gemini CLI'; codex = 'Codex telemetry' }
$colors = @{ claude = '#D78965'; antigravity = '#9C92F5'; gemini = '#6DA9F8'; codex = '#66C9AE' }

function New-Shortcut($path, $mode) {
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($path)
    $shortcut.TargetPath = $powershell
    $shortcut.Arguments = '-NoProfile -NonInteractive -STA -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $scriptPath + '" -Action ' + $mode
    $shortcut.WorkingDirectory = $runtime
    $shortcut.WindowStyle = 7
    $shortcut.Description = 'CodexBar Windows Control Center'
    $shortcut.Save()
}
function Start-Resident {
    Start-Process -FilePath $powershell -ArgumentList @('-NoProfile', '-NonInteractive', '-STA', '-WindowStyle', 'Hidden', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $scriptPath + '"'), '-Action', 'Run') -WindowStyle Hidden | Out-Null
}
function Send-Control($command) {
    $temporary = "$controlPath.$PID.tmp"
    @{ command = $command; createdAt = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() } | ConvertTo-Json | Set-Content -LiteralPath $temporary -Encoding UTF8
    Move-Item -LiteralPath $temporary -Destination $controlPath -Force
}
function Wait-ForExit($name) {
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $existing = $null
        if (![Threading.Mutex]::TryOpenExisting($name, [ref]$existing)) { return }
        $existing.Dispose()
        Start-Sleep -Milliseconds 500
    }
    throw 'The previous CodexBar resident did not exit.'
}
function Wait-ForStart($name) {
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $existing = $null
        if ([Threading.Mutex]::TryOpenExisting($name, [ref]$existing)) { $existing.Dispose(); return }
        Start-Sleep -Milliseconds 500
    }
    throw 'The CodexBar Windows resident did not start.'
}

if ($Action -eq 'Check') {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    Add-Type -Path (Join-Path $PSScriptRoot '..\..\claude\scripts\WindowsChildJob.cs')
    $job = New-Object CodexBarChildJob
    $job.Dispose()
    Write-Output 'PASS WinForms and Windows child-job initialization'
    exit 0
}

if ($Action -eq 'Install') {
    $sourceCompanion = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
    if (!$Address -and (Test-Path -LiteralPath $settingsPath)) { $Address = (Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json).address }
    if (!$Address) {
        foreach ($oldName in @('ClaudeCompanion', 'AntigravityCompanion')) {
            $oldSettings = Join-Path $env:LOCALAPPDATA "CodexBar\$oldName\windows-resident.json"
            if (Test-Path -LiteralPath $oldSettings) { $Address = (Get-Content -LiteralPath $oldSettings -Raw | ConvertFrom-Json).address; break }
        }
    }
    $parsedAddress = $null
    if (![Net.IPAddress]::TryParse($Address, [ref]$parsedAddress) -or $parsedAddress.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) {
        throw 'Install requires a private IPv4 address (for example the PC Tailscale address).'
    }
    $node = (Get-Command node.exe).Source
    $npm = (Get-Command npm.cmd).Source
    $claude = (Get-Command claude.exe -ErrorAction Stop).Source
    $gemini = (Get-Command gemini.cmd -ErrorAction SilentlyContinue).Source
    if (!$gemini) { $gemini = 'gemini.cmd' }
    New-Item -ItemType Directory -Path $runtime, $companionRoot -Force | Out-Null
    if (Test-Path -LiteralPath $settingsPath) {
        $existingTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
        if ($existingTask -and ($existingTask.Actions.Arguments -like ('*' + $scriptPath + '*'))) {
            Disable-ScheduledTask -TaskName $taskName | Out-Null
        }
        Send-Control 'exit'
        Wait-ForExit $mutexName
    }
    foreach ($name in @($providers + 'windows-control-center')) {
        $source = Join-Path $sourceCompanion $name
        $target = Join-Path $companionRoot $name
        New-Item -ItemType Directory -Path $target, (Join-Path $target 'src') -Force | Out-Null
        Copy-Item -Path (Join-Path $source 'src\*') -Destination (Join-Path $target 'src') -Recurse -Force
        Copy-Item -LiteralPath (Join-Path $source 'package.json') -Destination $target -Force
        Copy-Item -LiteralPath (Join-Path $source 'package-lock.json') -Destination $target -Force
        if ($name -eq 'windows-control-center') {
            New-Item -ItemType Directory -Path (Join-Path $target 'scripts') -Force | Out-Null
            Copy-Item -Path (Join-Path $source 'scripts\*') -Destination (Join-Path $target 'scripts') -Recurse -Force
        }
        if ($name -eq 'claude') {
            New-Item -ItemType Directory -Path (Join-Path $target 'scripts') -Force | Out-Null
            Copy-Item -LiteralPath (Join-Path $source 'scripts\WindowsChildJob.cs') -Destination (Join-Path $target 'scripts') -Force
        }
        Push-Location $target
        try {
            & $npm ci --omit=dev --no-audit --no-fund
            if ($LASTEXITCODE -ne 0) { throw "Dependency install failed for $name" }
        } finally { Pop-Location }
    }
    & $powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $scriptPath -Action Check
    if ($LASTEXITCODE -ne 0) { throw 'Windows Control Center check failed.' }
    @{ node = $node; claude = $claude; gemini = $gemini; address = $Address } | ConvertTo-Json | Set-Content -LiteralPath $settingsPath -Encoding UTF8

    # Replace only the two known CodexBar residents after the new package is ready.
    $replaced = @()
    try {
        foreach ($oldName in @('ClaudeCompanion', 'AntigravityCompanion')) {
            $oldScript = Join-Path $env:LOCALAPPDATA "CodexBar\$oldName\scripts\windows-resident.ps1"
            if (Test-Path -LiteralPath $oldScript) {
                & $powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $oldScript -Action Uninstall | Out-Null
                if ($LASTEXITCODE -ne 0) { throw "Could not stop $oldName" }
                $replaced += $oldScript
                $oldMutex = if ($oldName -eq 'ClaudeCompanion') { "Local\CodexBarClaudeResident-$sid" } else { "Local\CodexBarAntigravityResident-$sid" }
                Wait-ForExit $oldMutex
            }
        }
        if (Test-Path -LiteralPath $controlPath) { Remove-Item -LiteralPath $controlPath }
        $arguments = '-NoProfile -NonInteractive -STA -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $scriptPath + '" -Action Run'
        $existingTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
        if ($existingTask -and $existingTask.Actions.Arguments -notcontains $arguments) { throw 'An unrelated scheduled task uses the Control Center name.' }
        $startupMode = 'Task Scheduler'
        try {
            $taskAction = New-ScheduledTaskAction -Execute $powershell -Argument $arguments -WorkingDirectory $runtime
            $logonTrigger = New-ScheduledTaskTrigger -AtLogOn -User $sid
            $watchdogTrigger = New-ScheduledTaskTrigger -Once -At ([DateTime]::Now.AddMinutes(1)) -RepetitionInterval (New-TimeSpan -Minutes 1)
            $principal = New-ScheduledTaskPrincipal -UserId $sid -LogonType Interactive -RunLevel Limited
            $taskSettings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
            Register-ScheduledTask -TaskName $taskName -Action $taskAction -Trigger @($logonTrigger, $watchdogTrigger) -Principal $principal -Settings $taskSettings -Force | Out-Null
        } catch {
            $startupMode = 'Startup folder'
            New-Shortcut $startupLink 'Run'
        }
        New-Item -ItemType Directory -Path $menuDirectory -Force | Out-Null
        New-Shortcut (Join-Path $menuDirectory 'CodexBar Windows.lnk') 'Show'
        if ($startupMode -eq 'Task Scheduler') { Start-ScheduledTask -TaskName $taskName }
        else { Start-Resident }
        Wait-ForStart $mutexName
        Write-Output "Installed CodexBar Windows Control Center. Startup: $startupMode. Existing phone pairings preserved."
    } catch {
        foreach ($oldScript in $replaced) {
            try { & $powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $oldScript -Action Install -Address $Address | Out-Null } catch { }
        }
        throw
    }
    exit 0
}

if ($Action -eq 'Uninstall') {
    if (Test-Path -LiteralPath $settingsPath) { Send-Control 'exit' }
    $existingTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($existingTask -and ($existingTask.Actions.Arguments -like ('*' + $scriptPath + '*'))) {
        Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
    }
    foreach ($link in @($startupLink, (Join-Path $menuDirectory 'CodexBar Windows.lnk'))) {
        if (Test-Path -LiteralPath $link) { Remove-Item -LiteralPath $link }
    }
    Write-Output 'Automatic startup removed. Pairing identities and local files preserved.'
    exit 0
}

if (!(Test-Path -LiteralPath $settingsPath)) { throw 'Install the Windows Control Center first.' }
if ($Action -in @('Show', 'Restart', 'Pause')) {
    Send-Control $Action.ToLowerInvariant()
    Start-Resident
    exit 0
}

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -Path (Join-Path $PSScriptRoot '..\..\claude\scripts\WindowsChildJob.cs')
$created = $false
$mutex = New-Object Threading.Mutex($true, $mutexName, [ref]$created)
if (!$created) { $mutex.Dispose(); exit 0 }
$config = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
$script:workers = @{}
$script:cards = @{}
$script:paused = Test-Path -LiteralPath $pausePath
$script:quitting = $false
$script:lastStatus = [DateTime]::MinValue
$script:statusBusy = $false
$uiStatePath = Join-Path $runtime 'ui-state.json'
foreach ($id in $providers) {
    $script:workers[$id] = @{ child = $null; job = $null; attempts = 0; nextStart = [DateTime]::UtcNow }
}

function Stop-Worker($id) {
    $worker = $script:workers[$id]
    if ($worker.job) { $worker.job.Dispose(); $worker.job = $null }
    if ($worker.child) { $worker.child.Dispose(); $worker.child = $null }
}
function Start-Worker($id) {
    $worker = $script:workers[$id]
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $config.node
    $info.WorkingDirectory = Join-Path $companionRoot $id
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.RedirectStandardInput = $true
    $arguments = '"' + (Join-Path $centerRoot 'scripts\windows-worker.js') + '" ' + $id + ' --address ' + $config.address + ' --port ' + $ports[$id]
    if ($id -eq 'claude') { $arguments += ' --background --claude-command "' + $config.claude + '" --status-file "' + (Join-Path $runtime 'claude-status.json') + '"' }
    if ($id -eq 'antigravity') { $arguments += ' --background --status-file "' + (Join-Path $runtime 'antigravity-status.json') + '"' }
    if ($id -eq 'gemini') { $arguments += ' --gemini-command "' + $config.gemini + '"' }
    $info.Arguments = $arguments
    try {
        $worker.job = New-Object CodexBarChildJob
        $worker.child = [Diagnostics.Process]::Start($info)
        $worker.job.Add($worker.child.Handle)
        $worker.child.BeginOutputReadLine()
        $worker.child.BeginErrorReadLine()
        $worker.child.StandardInput.WriteLine('start')
        $worker.child.StandardInput.Close()
        $worker.attempts = 0
    } catch {
        Stop-Worker $id
        Retry-Worker $id
    }
}
function Retry-Worker($id) {
    $worker = $script:workers[$id]
    $worker.attempts++
    $seconds = [Math]::Min(300, 5 * [Math]::Pow(2, [Math]::Min($worker.attempts - 1, 6)))
    $worker.nextStart = [DateTime]::UtcNow.AddSeconds($seconds)
}
function Restart-Worker($id) {
    Stop-Worker $id
    $script:workers[$id].attempts = 0
    $script:workers[$id].nextStart = [DateTime]::UtcNow
    $script:lastStatus = [DateTime]::MinValue
}
function Color($hex) { return [Drawing.ColorTranslator]::FromHtml($hex) }
function New-Label($text, $x, $y, $width, $height, $size, $color) {
    $label = New-Object Windows.Forms.Label
    $label.Text = $text
    $label.SetBounds($x, $y, $width, $height)
    $label.ForeColor = Color $color
    $label.Font = New-Object Drawing.Font('Segoe UI', $size)
    return $label
}
function New-Button($text, $x, $y, $width, $height) {
    $button = New-Object Windows.Forms.Button
    $button.Text = $text
    $button.SetBounds($x, $y, $width, $height)
    $button.FlatStyle = 'Flat'
    $button.FlatAppearance.BorderSize = 1
    $button.FlatAppearance.BorderColor = Color '#465064'
    $button.BackColor = Color '#2a3444'
    $button.ForeColor = Color '#f1f4fa'
    $button.Font = New-Object Drawing.Font('Segoe UI', 9)
    return $button
}
function Show-Pairing($id) {
    try {
        $qrScript = Join-Path $centerRoot 'src\qr.js'
        $base64 = & $config.node $qrScript $id $config.address 2>$null
        if ($LASTEXITCODE -ne 0 -or !$base64) { throw 'QR unavailable' }
        $bytes = [Convert]::FromBase64String($base64)
        $stream = New-Object IO.MemoryStream(, $bytes)
        $image = [Drawing.Image]::FromStream($stream)
        $dialog = New-Object Windows.Forms.Form
        $dialog.Text = "Pair $($names[$id]) with CodexBar"
        $dialog.ClientSize = New-Object Drawing.Size(380, 425)
        $dialog.StartPosition = 'CenterParent'
        $dialog.FormBorderStyle = 'FixedDialog'
        $dialog.MaximizeBox = $false
        $dialog.BackColor = Color '#111823'
        $picture = New-Object Windows.Forms.PictureBox
        $picture.Image = $image
        $picture.SizeMode = 'Zoom'
        $picture.SetBounds(40, 28, 300, 300)
        $dialog.Controls.Add($picture)
        $instruction = if ($id -in @('claude', 'antigravity')) { 'Scan inside CodexBar > Connections. Keep this QR private.' } else { 'Scan on your phone, then confirm pairing in CodexBar.' }
        $label = New-Label $instruction 25 343 330 54 9 '#bdc7d8'
        $label.TextAlign = 'MiddleCenter'
        $dialog.Controls.Add($label)
        $dialog.Add_Shown({ $dialog.Activate() })
        [void]$dialog.ShowDialog($form)
        $dialog.Dispose()
        $image.Dispose()
        $stream.Dispose()
    } catch {
        [Windows.Forms.MessageBox]::Show('Pairing QR is unavailable. Restart this provider, then try again.', 'CodexBar Windows', 'OK', 'Warning') | Out-Null
    }
}
function Format-Reset($epoch) {
    if (!$epoch) { return '' }
    try { return ' · resets ' + [DateTimeOffset]::FromUnixTimeSeconds([long]$epoch).ToLocalTime().ToString('M/d HH:mm') }
    catch { return '' }
}
function Set-CardData($id, $row) {
    $card = $script:cards[$id]
    $worker = $script:workers[$id]
    if ($script:paused) {
        $card.status.Text = 'PAUSED'
        $card.data.Text = 'Start again from the tray menu or this window.'
        return
    }
    if (!$worker.child -or $worker.child.HasExited) {
        $card.status.Text = 'STARTING'
        $card.data.Text = 'Local bridge is starting or retrying.'
        return
    }
    if ($row.state -ne 'ready') {
        $card.status.Text = 'WAITING FOR SOURCE'
        if ($id -eq 'antigravity') {
            $diagnostic = 'Open the Antigravity Windows app and sign in.'
            try {
                $source = Get-Content -LiteralPath (Join-Path $runtime 'antigravity-status.json') -Raw | ConvertFrom-Json
                if ($source.pid -eq $worker.child.Id) {
                    switch ($source.failureCode) {
                        'SERVER_NOT_FOUND' { $diagnostic = 'Antigravity local server not found. Open its Windows app.' }
                        'LOCAL_API_UNAVAILABLE' { $diagnostic = 'Local quota API unavailable. Keep Antigravity open.' }
                        'QUOTA_UNAVAILABLE' { $diagnostic = 'Antigravity reported no measured quota windows.' }
                    }
                }
            } catch { }
            $card.data.Text = $diagnostic
        } elseif ($id -eq 'gemini') { $card.data.Text = 'Complete sign-in and folder trust in the dedicated Gemini workspace, verify /model, then Restart bridge.' }
        elseif ($id -eq 'claude') { $card.data.Text = 'Check Claude Code sign-in and /usage in its dedicated workspace.' }
        else { $card.data.Text = 'Codex session telemetry is not available yet.' }
        return
    }
    $card.status.Text = 'LIVE · ANDROID BRIDGE READY'
    if ($row.kind -eq 'telemetry') {
        $card.data.Text = "Today  $($row.todayTokens.ToString('N0')) tokens`r`nLast 7 days  $($row.last7DaysTokens.ToString('N0')) tokens`r`nLocal session telemetry, not subscription limits."
    } else {
        $lines = @($row.windows | ForEach-Object { "{0,-31} {1,3}% used{2}" -f $_.label, $_.usedPercent, (Format-Reset $_.resetsAtEpochSeconds) })
        $card.data.Text = $lines -join "`r`n"
    }
}
function Refresh-Status {
    if ($script:statusBusy) { return }
    $script:statusBusy = $true
    try {
        $statusScript = Join-Path $centerRoot 'src\status.js'
        $raw = & $config.node $statusScript $config.address 2>$null
        if ($LASTEXITCODE -ne 0 -or !$raw) { return }
        $status = $raw | ConvertFrom-Json
        foreach ($id in $providers) { Set-CardData $id $status.$id }
        $footer.Text = 'Updated ' + [DateTime]::Now.ToString('HH:mm:ss') + '  ·  Bound to ' + $config.address + '  ·  Keep Tailscale connected for remote Android access.'
    } catch {
        $footer.Text = 'Local status refresh failed. Bridges continue running.'
    } finally {
        $script:statusBusy = $false
        $script:lastStatus = [DateTime]::UtcNow
    }
}
function Handle-Control($command) {
    switch ($command) {
        'show' {
            $form.Show()
            $form.WindowState = 'Normal'
            $form.Activate()
            @{ visible = $form.Visible; windowState = [string]$form.WindowState; updatedAtEpochSeconds = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() } |
                ConvertTo-Json | Set-Content -LiteralPath $uiStatePath -Encoding UTF8
        }
        'refresh' { $script:lastStatus = [DateTime]::MinValue }
        'restart' {
            if (Test-Path -LiteralPath $pausePath) { Remove-Item -LiteralPath $pausePath }
            $script:paused = $false
            foreach ($id in $providers) { Restart-Worker $id }
        }
        'pause' {
            Set-Content -LiteralPath $pausePath -Value 'paused' -Encoding ASCII
            $script:paused = $true
            foreach ($id in $providers) { Stop-Worker $id }
            foreach ($id in $providers) { Set-CardData $id $null }
        }
        'exit' { $script:quitting = $true; [Windows.Forms.Application]::Exit() }
    }
}
function Resident-Tick {
    if (Test-Path -LiteralPath $controlPath) {
        try {
            $control = Get-Content -LiteralPath $controlPath -Raw | ConvertFrom-Json
            Remove-Item -LiteralPath $controlPath
            if ([Math]::Abs([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() - $control.createdAt) -lt 60) { Handle-Control $control.command }
        } catch { }
    }
    if ($script:quitting) { return }
    if (!$script:paused) {
        foreach ($id in $providers) {
            $worker = $script:workers[$id]
            if ($worker.child -and $worker.child.HasExited) { Stop-Worker $id; Retry-Worker $id }
            if (!$worker.child -and [DateTime]::UtcNow -ge $worker.nextStart) { Start-Worker $id }
        }
        if (([DateTime]::UtcNow - $script:lastStatus).TotalSeconds -ge 25) { Refresh-Status }
    }
    $tray.Text = if ($script:paused) { 'CodexBar Windows - paused' } else { 'CodexBar Windows - four local bridges' }
}

$form = New-Object Windows.Forms.Form
$form.Text = 'CodexBar Windows'
$form.ClientSize = New-Object Drawing.Size(920, 760)
$form.MinimumSize = New-Object Drawing.Size(700, 600)
$form.StartPosition = 'CenterScreen'
$form.BackColor = Color '#101722'
$form.ForeColor = Color '#eef3fa'
$form.Font = New-Object Drawing.Font('Segoe UI', 9)
$form.ShowInTaskbar = $true
$header = New-Object Windows.Forms.Panel
$header.Dock = 'Top'
$header.Height = 96
$header.BackColor = Color '#151e2b'
$title = New-Label 'CodexBar  /  Windows' 28 14 570 37 20 '#f7f9fc'
$title.Font = New-Object Drawing.Font('Segoe UI Semibold', 20)
$header.Controls.Add($title)
$subtitle = New-Label 'Local usage and a private bridge to your Android phone' 30 58 600 27 9 '#aebdd0'
$header.Controls.Add($subtitle)
$refreshButton = New-Button 'Refresh now' 758 30 128 35
$refreshButton.Anchor = 'Top,Right'
$refreshButton.add_Click({ $script:lastStatus = [DateTime]::MinValue; Refresh-Status })
$header.Controls.Add($refreshButton)
$form.Controls.Add($header)
$footer = New-Label 'Starting four local bridges…' 0 0 900 40 9 '#aebdd0'
$footer.Dock = 'Bottom'
$footer.Padding = New-Object Windows.Forms.Padding(28, 7, 0, 0)
$form.Controls.Add($footer)
$list = New-Object Windows.Forms.FlowLayoutPanel
$list.Dock = 'Fill'
$list.FlowDirection = 'TopDown'
$list.WrapContents = $false
$list.AutoScroll = $true
$list.Padding = New-Object Windows.Forms.Padding(18, 18, 18, 10)
$form.Controls.Add($list)
$list.BringToFront()
foreach ($id in $providers) {
    $card = New-Object Windows.Forms.Panel
    $card.Width = 858
    $card.Height = if ($id -eq 'antigravity') { 178 } else { 150 }
    $card.Margin = New-Object Windows.Forms.Padding(0, 0, 0, 12)
    $card.BackColor = Color '#202b3a'
    $stripe = New-Object Windows.Forms.Panel
    $stripe.SetBounds(0, 0, 5, $card.Height)
    $stripe.BackColor = Color $colors[$id]
    $card.Controls.Add($stripe)
    $heading = New-Label $names[$id] 22 13 330 30 14 '#f4f6fa'
    $heading.Font = New-Object Drawing.Font('Segoe UI Semibold', 14)
    $card.Controls.Add($heading)
    $state = New-Label 'STARTING' 22 43 410 20 8 '#92cdbb'
    $card.Controls.Add($state)
    $detail = New-Object Windows.Forms.TextBox
    $detail.SetBounds(22, 69, 620, ($card.Height - 78))
    $detail.Multiline = $true
    $detail.ReadOnly = $true
    $detail.BorderStyle = 'None'
    $detail.ScrollBars = 'Vertical'
    $detail.BackColor = Color '#202b3a'
    $detail.ForeColor = Color '#dce4ef'
    $detail.Font = New-Object Drawing.Font('Consolas', 9)
    $detail.Text = 'Collecting local usage…'
    $card.Controls.Add($detail)
    $pair = New-Button 'Pair phone QR' 680 21 155 35
    $pair.Tag = $id
    $pair.add_Click({ param($sender, $eventArgs) Show-Pairing ([string]$sender.Tag) })
    $card.Controls.Add($pair)
    $restart = New-Button 'Restart bridge' 680 69 155 31
    $restart.Tag = $id
    $restart.add_Click({ param($sender, $eventArgs) Restart-Worker ([string]$sender.Tag) })
    $card.Controls.Add($restart)
    $script:cards[$id] = @{ status = $state; data = $detail; panel = $card }
    $list.Controls.Add($card)
}
$list.add_Resize({
    $width = [Math]::Max(620, $list.ClientSize.Width - 42)
    foreach ($id in $providers) {
        $card = $script:cards[$id].panel
        $card.Width = $width
        foreach ($control in $card.Controls) {
            if ($control -is [Windows.Forms.Button]) { $control.Left = $width - 178 }
            if ($control -is [Windows.Forms.TextBox]) { $control.Width = $width - 215 }
        }
    }
})
$form.add_FormClosing({
    param($sender, $eventArgs)
    if (!$script:quitting -and $eventArgs.CloseReason -eq [Windows.Forms.CloseReason]::UserClosing) {
        $eventArgs.Cancel = $true
        $form.Hide()
        @{ visible = $false; windowState = 'Hidden'; updatedAtEpochSeconds = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() } |
            ConvertTo-Json | Set-Content -LiteralPath $uiStatePath -Encoding UTF8
    }
})
$tray = New-Object Windows.Forms.NotifyIcon
$tray.Icon = [Drawing.SystemIcons]::Application
$tray.Text = 'CodexBar Windows'
$menu = New-Object Windows.Forms.ContextMenuStrip
$menu.Items.Add('Open CodexBar Windows').add_Click({ Handle-Control 'show' })
$menu.Items.Add('Refresh usage').add_Click({ Handle-Control 'refresh' })
$menu.Items.Add('Restart all bridges').add_Click({ Handle-Control 'restart' })
$menu.Items.Add('Pause all bridges').add_Click({ Handle-Control 'pause' })
$menu.Items.Add('Disable auto-start and exit').add_Click({
    Start-Process -FilePath $powershell -ArgumentList @('-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $scriptPath + '"'), '-Action', 'Uninstall') -WindowStyle Hidden | Out-Null
})
$tray.ContextMenuStrip = $menu
$tray.add_DoubleClick({ Handle-Control 'show' })
$timer = New-Object Windows.Forms.Timer
$timer.Interval = 5000
$timer.add_Tick({ try { Resident-Tick } catch { $footer.Text = 'Bridge supervisor is retrying after an error.' } })
try {
    $tray.Visible = $true
    Resident-Tick
    $timer.Start()
    [Windows.Forms.Application]::Run()
} finally {
    $timer.Stop()
    foreach ($id in $providers) { Stop-Worker $id }
    $tray.Visible = $false
    $tray.Dispose()
    $menu.Dispose()
    $form.Dispose()
    $timer.Dispose()
    $mutex.ReleaseMutex()
    $mutex.Dispose()
}
