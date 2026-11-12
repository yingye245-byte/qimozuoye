# =====================================================================
#  校园二手闲置交易平台 —— 超时订单自动取消 端到端验证脚本
#  用法(管理员 PowerShell)：powershell -ExecutionPolicy Bypass -File verify_timeout.ps1
#  流程：user01 下单 -> 不做任何操作 -> 等待 / 手动触发超时扫描 -> 检查订单状态与商品状态
# =====================================================================
param(
    [string]$Base = "http://localhost:8080",
    [string]$Mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
    [int]$OrderId = 0
)

$ErrorActionPreference = "Continue"
$script:Sess = $null
$script:LastLoc = $null

function Query([string]$sql) {
    $out = $sql | & $Mysql -ucampus -pcampus123 -D campus_secondhand --default-character-set=utf8mb4 2>$null
    return @($out)
}

function Query2([string]$sql) {
    $out = $sql | & $Mysql -ucampus -pcampus123 -D campus_secondhand --default-character-set=utf8mb4 2>$null
    return @($out)
}

function Get-ScalarId([string[]]$rows) {
    # rows[0] 是表头，rows[1] 是第一行数据，第一个字段是 id
    if ($rows.Count -lt 2) { return 0 }
    $cols = ($rows[1] -split "`t")
    return [int]$cols[0]
}
function New-Session([string]$u) {
    $script:Sess = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $lp = Invoke-WebRequest -Uri "$Base/login" -WebSession $script:Sess -UseBasicParsing
    $csrf = [regex]::Match($lp.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
    $body = [System.Text.Encoding]::UTF8.GetBytes("username=$u&password=123456&_csrf=$csrf")
    $req = [System.Net.HttpWebRequest]::Create("$Base/login")
    $req.Method = "POST"; $req.AllowAutoRedirect = $false
    $req.CookieContainer = $script:Sess.Cookies
    $req.ContentType = "application/x-www-form-urlencoded"
    $req.ContentLength = $body.Length
    $st = $req.GetRequestStream(); $st.Write($body, 0, $body.Length); $st.Close()
    $r = $req.GetResponse(); $loc = $r.Headers["Location"]; $r.Close()
    Write-Output ("[登录 {0}] -> {1}" -f $u, $loc)
}

function Get-Csrf([string]$path) {
    $p = Invoke-WebRequest -Uri ($Base + $path) -WebSession $script:Sess -UseBasicParsing
    return [regex]::Match($p.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
}

function Post-Form([string]$path, [string]$csrf, [string]$data) {
    $all = "_csrf=$csrf" + $(if ($data) { "&" + $data } else { "" })
    $body = [System.Text.Encoding]::UTF8.GetBytes($all)
    $req = [System.Net.HttpWebRequest]::Create($Base + $path)
    $req.Method = "POST"; $req.AllowAutoRedirect = $false
    $req.CookieContainer = $script:Sess.Cookies
    $req.ContentType = "application/x-www-form-urlencoded"
    $req.ContentLength = $body.Length
    $st = $req.GetRequestStream(); $st.Write($body, 0, $body.Length); $st.Close()
    try { $r = $req.GetResponse(); $code = [int]$r.StatusCode; $loc = $r.Headers["Location"]; $r.Close() }
    catch [System.Net.WebException] { $c = $_.Exception.Response; $code = [int]$c.StatusCode; $loc = $c.Headers["Location"]; $c.Close() }
    $script:LastLoc = $loc
    Write-Output ("  POST {0} -> {1} {2}" -f $path, $code, $loc)
    return $loc
}

function Show-Flash([string]$url) {
    try {
        $pg = Invoke-WebRequest -Uri $url -WebSession $script:Sess -UseBasicParsing
        $m = [regex]::Match($pg.Content, 'flash flash-(success|error)">\s*([^<]{0,120})')
        if ($m.Success) { Write-Output ("     flash: " + $m.Groups[2].Value.Trim()) }
    } catch { }
}

Write-Output "=========== 1. 查询一上架且未成交的商品 ==========="
$row = Query2 "SELECT id,title,user_id,price FROM item WHERE status=1 AND deal_status=1 AND audit_status=1 ORDER BY id DESC LIMIT 1;"
Write-Output ($row -join "`n")
$lines = @($row | Where-Object { $_ -match "^\s*\d+\s*`t" })
if ($lines.Count -eq 0) { Write-Output "没有可下单的商品，退出"; exit }
$cols = $lines[0] -split "`t"
$itemId = [int]$cols[0]
Write-Output ("选中商品 id={0} 标题={1} 卖家id={2} 价格={3}" -f $itemId, $cols[1], $cols[2], $cols[3])

Write-Output "=========== 2. user01 下单（30 分钟倒计时开始）==========="
New-Session "user01"
$maxId = Get-ScalarId (Query2 "SELECT IFNULL(MAX(id),0) FROM orders;")
$csrf = Get-Csrf "/item/detail/$itemId"
$loc = Post-Form "/order/create" $csrf ("itemId=" + $itemId)
# 注意：/order/create 成功与失败都会 302，成功跳 /order/detail/{id}，失败跳回 /item/detail/{id}
# 所以这里用「订单表里是否出现新订单」判断，比解析 Location 可靠
$newId = Get-ScalarId (Query2 ("SELECT IFNULL(MAX(id),0) FROM orders WHERE id > {0};" -f $maxId))
if ($newId -gt $maxId) {
    $OrderId = $newId
    Write-Output ("下单成功，新订单 id={0}" -f $OrderId)
} else {
    Write-Output "未产生新订单（该商品可能已被占用），尝试用上一笔待支付订单继续演示"
}
if ($OrderId -le 0) { Write-Output "下单失败，退出"; exit }
Write-Output ((Query ("SELECT id,order_no,status,create_time,cancel_time,cancel_reason FROM orders WHERE id={0};" -f $OrderId)) -join "`n")
Write-Output ((Query ("SELECT id,status,deal_status FROM item WHERE id={0};" -f $itemId)) -join "`n")

Write-Output "=========== 3. （演示用）把订单创建时间提前 31 分钟，模拟已超时 ==========="
Query ("UPDATE orders SET create_time=DATE_SUB(NOW(), INTERVAL 31 MINUTE) WHERE id={0};" -f $OrderId) | Out-Null
Write-Output ((Query ("SELECT id,status,create_time,TIMESTAMPDIFF(MINUTE,create_time,NOW()) AS elapsed_min FROM orders WHERE id={0};" -f $OrderId)) -join "`n")
Write-Output "说明：RabbitMQ 延迟队列在 30 分钟后由 campus.order.cancel.queue 自动消费；"
Write-Output "      这里为了让演示即时可见，直接点后台【立即扫描超时订单】按钮，二者走同一个 OrderStateService.cancelOrder。"

Write-Output "=========== 4. 管理员点击【立即扫描超时订单】 ==========="
New-Session "admin"
$csrf2 = Get-Csrf "/admin/dashboard"
Post-Form "/admin/task/scan-timeout" $csrf2 "" | Out-Null
Start-Sleep -Milliseconds 800
Show-Flash "$Base/admin/dashboard"

Write-Output "=========== 5. 校验结果 ==========="
Write-Output "--- 订单（期望 status=4 已取消，cancel_reason 有值）"
Write-Output ((Query ("SELECT id,status,cancel_time,cancel_reason FROM orders WHERE id={0};" -f $OrderId)) -join "`n")
Write-Output "--- 商品（期望 status=1 在售 / deal_status=1 未成交，即库存已恢复）"
Write-Output ((Query ("SELECT id,title,status,deal_status FROM item WHERE id={0};" -f $itemId)) -join "`n")
Write-Output "--- 运行日志里的 MQ 与定时任务记录（最近 6 条）"
$log = "D:\projects\tmp\run.log"
if (Test-Path $log) {
    $fs = [System.IO.File]::Open($log, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    $sr = New-Object System.IO.StreamReader($fs, [System.Text.Encoding]::UTF8)
    $txt = $sr.ReadToEnd(); $sr.Close(); $fs.Close()
    ($txt -split "`r?`n" | Where-Object { $_ -match "MQ|ScheduledTasks|超时" } | Select-Object -Last 6) |
        ForEach-Object { Write-Output ("   " + $_) }
}
Write-Output "=========== 验证结束 ==========="