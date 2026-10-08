param(
    [int]$Runs = 3,
    [int]$MaxAttempts = 3,
    [string[]]$Strategies = @('nolock', 'forupdate', 'optimistic', 'redis')
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$outDir = Join-Path $root 'docs\evidence\bench'
New-Item -ItemType Directory -Force $outDir | Out-Null

$container = 'flashdrop-postgres-1'
$seedFile = Join-Path $root 'scripts\seed-demo-campaigns.sql'
$claimTables = @{
    nolock     = 'claim_no_lock'
    forupdate  = 'claim_for_update'
    optimistic = 'claim_optimistic'
    redis      = 'claim_redis'
}

function Invoke-Seed {
    Get-Content $seedFile | docker exec -i $container psql -q -U flashdrop -d flashdrop | Out-Null
}

function Get-DbCount([string]$table) {
    $raw = docker exec $container psql -U flashdrop -d flashdrop -tAc "SELECT count(*) FROM $table"
    return [int](($raw | Select-Object -First 1).Trim())
}

function Get-Count($metrics, [string]$name) {
    if ($metrics.PSObject.Properties.Name -contains $name) { return [int]$metrics.$name.count }
    return 0
}

function Get-Median([double[]]$values) {
    $sorted = @($values | Sort-Object)
    $n = $sorted.Count
    if ($n % 2 -eq 1) { return $sorted[[int][math]::Floor($n / 2)] }
    return ($sorted[$n / 2 - 1] + $sorted[$n / 2]) / 2
}

$cpu = (Get-CimInstance Win32_Processor | Select-Object -First 1).Name
$ram = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 1)
$k6Version = (& k6 version | Select-Object -First 1)
@(
    "date: $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    "cpu: $cpu"
    "ram_gb: $ram"
    "k6: $k6Version"
    "scenario: shared-iterations, 300 VUs, 300 iterations, 50 coupons"
    "runs_per_strategy: $Runs (+1 warm-up run discarded)"
) | Set-Content (Join-Path $outDir 'environment.txt') -Encoding UTF8

$rows = @()
foreach ($s in $Strategies) {
    for ($i = 0; $i -le $Runs; $i++) {
        for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
            if ($s -ne 'redis') { Invoke-Seed }
            Start-Sleep -Seconds 3
            $label = if ($i -eq 0) { 'warmup' } else { "run$i" }
            $json = Join-Path $outDir "k6-$s-$label-a$attempt.json"
            Write-Host "[$s] $label attempt $attempt"
            & k6 run --quiet --log-output=none --summary-export $json "k6/$s.js" | Out-Null

            if ($s -eq 'redis') { Start-Sleep -Seconds 2 }
            $m = (Get-Content $json -Raw | ConvertFrom-Json).metrics
            $failed = [int][math]::Round([double]$m.http_reqs.count * [double]$m.http_req_failed.value)
            $valid = $failed -eq 0

            if ($i -gt 0) {
                $rows += [pscustomobject]@{
                    strategy  = $s
                    run       = $i
                    attempt   = $attempt
                    valid     = $valid
                    failed    = $failed
                    rps       = [math]::Round([double]$m.iterations.rate, 1)
                    avg_ms    = [math]::Round([double]$m.http_req_duration.avg, 1)
                    p95_ms    = [math]::Round([double]$m.http_req_duration.'p(95)', 1)
                    success   = Get-Count $m 'claim_success'
                    sold_out  = Get-Count $m 'claim_sold_out'
                    db_issued = Get-DbCount $claimTables[$s]
                }
            }

            if ($valid) { break }
            Write-Host "  $failed requests failed, retrying" -ForegroundColor Yellow
        }
    }
}

$rows | Export-Csv (Join-Path $outDir 'results.csv') -NoTypeInformation -Encoding UTF8

$summary = foreach ($s in $Strategies) {
    $r = @($rows | Where-Object { $_.strategy -eq $s -and $_.valid })
    [pscustomobject]@{
        strategy      = $s
        valid_runs    = $r.Count
        invalid_runs  = @($rows | Where-Object { $_.strategy -eq $s -and -not $_.valid }).Count
        median_rps    = Get-Median ($r | ForEach-Object { $_.rps })
        median_avg_ms = Get-Median ($r | ForEach-Object { $_.avg_ms })
        median_p95_ms = Get-Median ($r | ForEach-Object { $_.p95_ms })
        success       = (($r | ForEach-Object { $_.success }) | Select-Object -Unique) -join '/'
        db_issued     = (($r | ForEach-Object { $_.db_issued }) | Select-Object -Unique) -join '/'
    }
}

$summary | Export-Csv (Join-Path $outDir 'summary.csv') -NoTypeInformation -Encoding UTF8

$rows | Format-Table -AutoSize
$summary | Format-Table -AutoSize
