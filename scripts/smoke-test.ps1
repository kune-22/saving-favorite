param([string]$BaseUrl = 'http://localhost:18081')
$ErrorActionPreference = 'Stop'
function Assert($condition, $message) { if (!$condition) { throw $message } }
function New-TestSession($email) {
    $page = Invoke-WebRequest "$BaseUrl/register" -SessionVariable session
    $token = [regex]::Match($page.Content, 'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value
    $page = Invoke-WebRequest "$BaseUrl/register" -Method Post -WebSession $session -Body @{name='動作確認';email=$email;password='TestPass123!';passwordConfirmation='TestPass123!';_csrf=$token}
    Assert ($page.Content -match 'app-sidebar') 'Registration did not open the application'
    $token = [regex]::Match($page.Content, 'name="csrf-token" content="([^"]+)"').Groups[1].Value
    Assert ($token.Length -gt 20) 'Missing application CSRF token'
    return @{Session=$session; Headers=@{'X-CSRF-TOKEN'=$token}}
}
function Api($context, $path, $method='GET', $body=$null, $expected=200) {
    $args = @{Uri="$BaseUrl/api/$path";Method=$method;WebSession=$context.Session;Headers=$context.Headers;SkipHttpErrorCheck=$true}
    if ($null -ne $body) { $args.ContentType='application/json; charset=utf-8'; $args.Body=($body | ConvertTo-Json -Depth 8 -Compress) }
    $response = Invoke-WebRequest @args
    Assert ($response.StatusCode -eq $expected) "$method $path expected $expected, got $($response.StatusCode): $($response.Content)"
    if ($response.Content) { return $response.Content | ConvertFrom-Json }
}
$tag = [guid]::NewGuid().ToString('N').Substring(0,10)
$one = New-TestSession "app-$tag@example.com"
$two = New-TestSession "other-$tag@example.com"
$f = Api $one favorites POST @{name='青空';description='テスト用の推し';monthlyBudget=10000}
Api $one favorites POST @{name='不正な予算';monthlyBudget=-1} 400 | Out-Null
$f2 = Api $one favorites POST @{name='紫苑';description='2人目'}
$date = Get-Date -Format 'yyyy-MM-dd'
$entry = @{favoriteId=$f.id;name='アクリルスタンド';category='アクリルスタンド';price=1200;quantity=2;purchasedDate=$date;deadline=$null;status='OWNED';recurrence='NONE';storeUrl='https://example.com/product'}
$item = Api $one items POST $entry
$fee = Api $one items POST @{favoriteId=$f2.id;name='月会費';category='会費';price=500;quantity=1;purchasedDate=$date;deadline=$null;status='PAID';recurrence='MONTHLY';storeUrl=''}
$plan = Api $one items POST @{favoriteId=$f.id;name='予約グッズ';category='その他';price=3000;quantity=1;purchasedDate=$date;deadline=$date;status='PLANNED';recurrence='NONE';storeUrl=''}
$eventInput = @{favoriteId=$f.id;title='コラボカフェ';kind='コラボ';startDate=$date;endDate=$date;reminderDays=3;notes='通知テスト'}
$event = Api $one events POST $eventInput
$snapshot = Api $one state
Assert ($snapshot.favorites.Count -eq 2 -and $snapshot.items.Count -eq 3 -and $snapshot.events.Count -eq 1) 'Snapshot missing records'
Assert (($snapshot | ConvertTo-Json -Depth 10) -notmatch 'passwordHash') 'Private account fields exposed'
$other = Api $two state
Assert ($other.favorites.Count -eq 0 -and $other.items.Count -eq 0 -and $other.events.Count -eq 0) 'Account isolation failed'
Api $two "favorites/$($f.id)" PUT @{name='改ざん';description=''} 404 | Out-Null
Api $two "items/$($item.id)" PUT $entry 404 | Out-Null
Api $two "events/$($event.id)" DELETE $null 404 | Out-Null
$entry.favoriteId=$f.id; Api $two items POST $entry 404 | Out-Null
$entry.quantity=0; Api $one "items/$($item.id)" PUT $entry 400 | Out-Null
$entry.quantity=3; $entry.storeUrl='javascript:alert(1)'; Api $one "items/$($item.id)" PUT $entry 400 | Out-Null
$entry.storeUrl='https://example.com/product'; Api $one "items/$($item.id)" PUT $entry | Out-Null
$eventInput.endDate='2000-01-01'; Api $one "events/$($event.id)" PUT $eventInput 400 | Out-Null
$eventInput.endDate=$date; $eventInput.title='更新済みのコラボ'; Api $one "events/$($event.id)" PUT $eventInput | Out-Null
Api $one "favorites/$($f.id)" PUT @{name='青空（更新）';description='更新したメモ';monthlyBudget=15000} | Out-Null
$snapshot = Api $one state
Assert (($snapshot.items | Where-Object id -eq $item.id).quantity -eq 3) 'Item edit failed'
Assert ($snapshot.events[0].title -eq '更新済みのコラボ') 'Event edit failed'
Assert (($snapshot.favorites | Where-Object id -eq $f.id).monthlyBudget -eq 15000) 'Budget edit failed'
$one.Session.Headers.Remove('X-CSRF-TOKEN') | Out-Null
$noCsrf = Invoke-WebRequest "$BaseUrl/api/favorites" -Method Post -WebSession $one.Session -ContentType application/json -Body '{"name":"bad"}' -SkipHttpErrorCheck
Assert ($noCsrf.StatusCode -eq 403) 'CSRF protection failed'
Api $one "favorites/$($f.id)" DELETE | Out-Null
$snapshot=Api $one state
Assert ($snapshot.items.Count -eq 1 -and $snapshot.events[0].favoriteId -eq $null) 'Favorite deletion did not cascade/unlink safely'
Api $one "events/$($event.id)" DELETE | Out-Null
Api $one "items/$($fee.id)" DELETE | Out-Null
Api $one "favorites/$($f2.id)" DELETE | Out-Null
$snapshot=Api $one state
Assert ($snapshot.favorites.Count -eq 0 -and $snapshot.items.Count -eq 0 -and $snapshot.events.Count -eq 0) 'Record deletion failed'
Write-Output 'PASS: auto-login; favorite/item/event CRUD; validation; unsafe URL rejection; CSRF; cross-account isolation; cascade deletion.'

