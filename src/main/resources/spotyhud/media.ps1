# SpotyHUD media bridge (Windows PowerShell 5.1 + Windows Runtime).
# Protocol: reads one command per line on stdin, answers POLL with ONE JSON line on stdout.
#   POLL | PLAY_PAUSE | NEXT | PREVIOUS | EXIT
$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($op, $type) {
    $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
    if ($task.Wait(5000)) { return $task.Result }
    return $null
}

$mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$mgr = Await ($mgrType::RequestAsync()) $mgrType

$script:lastKey = ''
$script:thumbTries = 0
$script:lastThumb = $null

function Get-Session {
    if ($null -eq $mgr) { return $null }
    foreach ($s in $mgr.GetSessions()) {
        if ($s.SourceAppUserModelId -like '*spotify*') { return $s }
    }
    return $mgr.GetCurrentSession()
}

function Get-Thumb($props) {
    try {
        $ref = $props.Thumbnail
        if ($null -eq $ref) { return $null }
        $stream = Await ($ref.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        if ($null -eq $stream) { return $null }
        $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
        $ms = New-Object System.IO.MemoryStream
        $net.CopyTo($ms)
        $net.Dispose()
        if ($ms.Length -lt 100) { return $null }
        return [Convert]::ToBase64String($ms.ToArray())
    } catch { return $null }
}

function Get-State {
    $s = Get-Session
    if ($null -eq $s) { return @{ active = $false } }
    $propType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
    $props = Await ($s.TryGetMediaPropertiesAsync()) $propType
    if ($null -eq $props -or [string]::IsNullOrEmpty($props.Title)) { return @{ active = $false } }
    $status = [string]$s.GetPlaybackInfo().PlaybackStatus
    if ($status -eq 'Closed' -or $status -eq 'Stopped') { return @{ active = $false } }
    $playing = ($status -eq 'Playing')
    $tl = $s.GetTimelineProperties()
    $dur = $tl.EndTime.TotalSeconds - $tl.StartTime.TotalSeconds
    $pos = $tl.Position.TotalSeconds - $tl.StartTime.TotalSeconds
    if ($playing) {
        $delta = ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalSeconds
        if ($delta -gt 0 -and $delta -lt 60) { $pos += $delta }
    }
    if ($dur -lt 0) { $dur = 0 }
    if ($pos -lt 0) { $pos = 0 }
    if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }

    $key = "$($props.Title)|$($props.Artist)|$($props.AlbumTitle)"
    if ($key -ne $script:lastKey) { $script:lastKey = $key; $script:thumbTries = 0; $script:lastThumb = $null }

    $r = @{
        active  = $true
        title   = [string]$props.Title
        artist  = [string]$props.Artist
        album   = [string]$props.AlbumTitle
        playing = $playing
        pos     = [Math]::Round($pos, 3)
        dur     = [Math]::Round($dur, 3)
    }
    # Spotify sometimes publishes the cover a moment after the metadata, so retry a few polls.
    if ($script:thumbTries -lt 8) {
        $script:thumbTries++
        $b64 = Get-Thumb $props
        if ($b64 -and $b64 -ne $script:lastThumb) { $script:lastThumb = $b64; $r.thumb = $b64 }
    }
    return $r
}

function Invoke-Control($name) {
    $s = Get-Session
    if ($null -eq $s) { return }
    switch ($name) {
        'PLAY_PAUSE' { [void](Await ($s.TryTogglePlayPauseAsync()) ([bool])) }
        'NEXT'       { [void](Await ($s.TrySkipNextAsync()) ([bool])) }
        'PREVIOUS'   { [void](Await ($s.TrySkipPreviousAsync()) ([bool])) }
    }
}

while ($true) {
    $line = [Console]::In.ReadLine()
    if ($null -eq $line) { break }
    $cmd = $line.Trim()
    if ($cmd -eq 'EXIT') { break }
    if ($cmd -eq 'POLL') {
        try { $out = Get-State } catch { $out = @{ active = $false } }
        [Console]::Out.WriteLine(($out | ConvertTo-Json -Compress))
        [Console]::Out.Flush()
    } elseif ($cmd.Length -gt 0) {
        try { Invoke-Control $cmd } catch { }
    }
}
