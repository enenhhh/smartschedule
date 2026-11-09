<?php
/**
 * SmartSchedule share service 2.1 (app 5.1), PHP 7.4+.
 * POST ?action=upload / GET ?action=download&code=NNNNNN / GET ?action=health
 * No database or location data. Client-only city lookup uses BigDataCloud.
 * IMPORTANT: existing legacy data/*.json files are left untouched for compatibility.
 * New files are PHP-guarded, even when open_basedir prevents storage outside the web root.
 */
declare(strict_types=1);
// Never leak PHP notices/warnings into JSON responses (e.g. open_basedir probes).
ini_set('display_errors', '0');
error_reporting(E_ALL);
const MAX_BODY_BYTES = 65536;
const EXPIRE_SECONDS = 604800;
const MAX_TOTAL_FILES = 20000;
const MAX_TOTAL_BYTES = 536870912;
const UPLOADS_PER_HOUR = 20;
const DOWNLOADS_PER_MIN = 30;
const CODE_RETRY = 32;
const GUARD = "<?php http_response_code(404); exit; ?>\n";

function respond(int $status, array $body): void {
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    header('X-Content-Type-Options: nosniff');
    header('Referrer-Policy: no-referrer');
    if ($status !== 204) echo json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
function fail(int $status, string $error): void { respond($status, ['success'=>false, 'error'=>$error]); }

$configured = getenv('SMARTSCHEDULE_DATA_DIR');
/** True if $path lies within an open_basedir root (always true when no restriction). */
function path_allowed(string $path): bool {
    $open = ini_get('open_basedir');
    if (!is_string($open) || $open === '') return true;
    $path = rtrim(str_replace('\\', '/', $path), '/');
    foreach (explode(PATH_SEPARATOR, $open) as $root) {
        $root = rtrim(str_replace('\\', '/', trim($root)), '/');
        if ($root !== '' && ($path === $root || strncmp($path, $root . '/', strlen($root) + 1) === 0)) return true;
    }
    return false;
}
if (is_string($configured) && $configured !== '') {
    $private = $configured;
} else {
    // fxm/ is one level below the document root; TWO parent levels are required.
    $outside = dirname(__DIR__, 2) . '/smartschedule-private';
    if (!path_allowed($outside)) {
        $private = __DIR__ . '/private'; // open_basedir forbids the parent level; stay inside fxm/
    } else {
        $private = ((@is_dir($outside) && @is_writable($outside)) || @mkdir($outside, 0750, true))
            ? $outside : __DIR__ . '/private';
    }
}
define('DATA_DIR', rtrim($private, '/'));
define('CODES_DIR', DATA_DIR . '/codes');
define('RATE_DIR', DATA_DIR . '/rate');
define('LEGACY_DIR', __DIR__ . '/data');
foreach ([DATA_DIR, CODES_DIR, RATE_DIR] as $dir) {
    if (!@is_dir($dir) && !@mkdir($dir, 0750, true)) fail(503, 'storage unavailable');
}
if (!@is_file(DATA_DIR . '/.htaccess')) @file_put_contents(DATA_DIR . '/.htaccess', "Require all denied\nDeny from all\n");

function client_ip(): string {
    // Never trust arbitrary X-Forwarded-For headers. Configure real_ip on the web server if needed.
    return preg_replace('/[^0-9a-fA-F:.]/', '', $_SERVER['REMOTE_ADDR'] ?? '') ?: 'unknown';
}
function read_guarded(string $file): ?array {
    $raw = @file_get_contents($file);
    if ($raw === false || strncmp($raw, GUARD, strlen(GUARD)) !== 0) return null;
    $data = json_decode(substr($raw, strlen(GUARD)), true, 32);
    return is_array($data) ? $data : null;
}
function rate_limit(string $bucket, int $limit, int $seconds): void {
    $path = RATE_DIR . '/' . hash('sha256', $bucket . '|' . client_ip()) . '.php';
    $fp = @fopen($path, 'c+');
    if (!$fp || !flock($fp, LOCK_EX)) fail(503, 'rate limiter unavailable');
    $raw = stream_get_contents($fp);
    $times = strncmp($raw, GUARD, strlen(GUARD)) === 0 ? json_decode(substr($raw, strlen(GUARD)), true) : [];
    $times = array_values(array_filter(is_array($times) ? $times : [], static fn($t) => is_int($t) && $t > time() - $seconds));
    if (count($times) >= $limit) {
        flock($fp, LOCK_UN); fclose($fp);
        header('Retry-After: ' . $seconds); fail(429, 'rate limited, try again later');
    }
    $times[] = time();
    ftruncate($fp, 0); rewind($fp); fwrite($fp, GUARD . json_encode($times)); fflush($fp);
    flock($fp, LOCK_UN); fclose($fp); @chmod($path, 0640);
}
function cleanup(bool $force = false): array {
    $count=0; $bytes=0; $sweep=$force || random_int(1,20)===1;
    foreach (@glob(CODES_DIR . '/*.php') ?: [] as $f) {
        if ($sweep && (@filemtime($f) ?: 0) < time()-EXPIRE_SECONDS) { @unlink($f); continue; }
        $count++; $bytes += @filesize($f) ?: 0;
    }
    if ($sweep) foreach (@glob(RATE_DIR . '/*.php') ?: [] as $f) {
        if ((@filemtime($f) ?: 0) < time()-7200) @unlink($f);
    }
    return ['count'=>$count, 'bytes'=>$bytes];
}
function read_body_limited(int $max): string {
    if ((int)($_SERVER['CONTENT_LENGTH'] ?? 0) > $max) fail(413, 'payload too large (max 64 KB)');
    $in=fopen('php://input','rb'); $body='';
    if (!$in) fail(400,'cannot read body');
    while (!feof($in)) {
        $chunk=fread($in,8192);
        if ($chunk===false) { fclose($in); fail(400,'cannot read body'); }
        $body.=$chunk;
        if (strlen($body)>$max) { fclose($in); fail(413,'payload too large (max 64 KB)'); }
    }
    fclose($in); return $body;
}
function is_code(string $code): bool { return preg_match('/^[0-9]{6}$/D', $code) === 1; }
function code_path(string $code): string { return CODES_DIR . '/' . $code . '.php'; }

function utf8_ok(string $s): bool
{
    return $s === '' || preg_match('//u', $s) === 1;
}

function str_ok($v, int $max): bool
{
    return is_string($v) && strlen($v) <= $max && utf8_ok($v);
}

function validate_time_plan($p): bool
{
    if (!is_array($p) || !str_ok($p['name'] ?? null, 120) || ($p['name'] ?? '') === '') {
        return false;
    }
    $periods = $p['periods'] ?? [];
    if (!is_array($periods) || count($periods) > 30) {
        return false;
    }
    foreach ($periods as $per) {
        if (!is_array($per) || !str_ok($per['start'] ?? null, 5) || !str_ok($per['end'] ?? null, 5)) {
            return false;
        }
        if (!preg_match('/^(?:[01]?\d|2[0-3]):[0-5]\d$/', $per['start']) || !preg_match('/^(?:[01]?\d|2[0-3]):[0-5]\d$/', $per['end'])) {
            return false;
        }
    }
    foreach (['morningStart', 'morningEnd'] as $k) {
        if (isset($p[$k]) && (!str_ok($p[$k], 5) || !preg_match('/^(?:[01]?\d|2[0-3]):[0-5]\d$/', $p[$k]))) {
            return false;
        }
    }
    return true;
}

function validate_day($d): bool
{
    if (!is_array($d)) {
        return false;
    }
    foreach (['courses', 'evenCourses', 'even'] as $k) {
        if (!isset($d[$k]) || $d[$k] === null) {
            continue;
        }
        if (!is_array($d[$k]) || count($d[$k]) > 30) {
            return false;
        }
        foreach ($d[$k] as $c) {
            if (!str_ok($c, 120)) {
                return false;
            }
        }
    }
    foreach (['morningRead', 'evenMorningRead'] as $k) {
        if (isset($d[$k]) && $d[$k] !== null && !str_ok($d[$k], 120)) {
            return false;
        }
    }
    return true;
}

function validate_timetable($t): bool
{
    if (!is_array($t) || !str_ok($t['name'] ?? '', 120)) {
        return false;
    }
    if (isset($t['id']) && !(is_string($t['id']) && preg_match('/^[A-Za-z0-9_\-]{1,40}$/', $t['id']))) {
        return false;
    }
    if (isset($t['timePlanName']) && !str_ok($t['timePlanName'], 120)) {
        return false;
    }
    $days = $t['days'] ?? [];
    if (!is_array($days) || count($days) > 7) {
        return false;
    }
    foreach ($days as $d) {
        if (!validate_day($d)) {
            return false;
        }
    }
    return true;
}

/** v2：课表 + 时间方案 + 简称 + 开学日期 */
function validate_v2(array $root): bool
{
    $allowed = ['v', 'app', 'created', 'startDate', 'timePlans', 'timetables', 'shortNames', 'kind'];
    foreach (array_keys($root) as $k) {
        if (!in_array($k, $allowed, true)) {
            return false;
        }
    }
    if (isset($root['startDate']) && !(is_string($root['startDate']) && preg_match('/^\d{4}-\d{2}-\d{2}$/', $root['startDate']))) {
        return false;
    }
    if (isset($root['startDate'])) {
        $d = explode('-', $root['startDate']);
        if (!checkdate((int)$d[1], (int)$d[2], (int)$d[0])) return false;
    }
    if (isset($root['created']) && !str_ok($root['created'], 40)) {
        return false;
    }
    $tps = $root['timePlans'] ?? [];
    $tts = $root['timetables'] ?? [];
    $sns = $root['shortNames'] ?? [];
    if (!is_array($tps) || !is_array($tts) || !is_array($sns)) {
        return false;
    }
    if (count($tps) > 30 || count($tts) > 30 || count($sns) > 300) {
        return false;
    }
    if (count($tps) === 0 && count($tts) === 0) {
        return false; // 空分享没有意义
    }
    foreach ($tps as $p) {
        if (!validate_time_plan($p)) {
            return false;
        }
    }
    foreach ($tts as $t) {
        if (!validate_timetable($t)) {
            return false;
        }
    }
    foreach ($sns as $k => $v) {
        // PHP 会把纯数字的键转成 int，这里一并接受
        if (!(is_string($k) || is_int($k)) || strlen((string) $k) > 120 || !str_ok($v, 40)) {
            return false;
        }
    }
    return true;
}

/** v1（4.x 客户端）：{prefs:{}, plans:{relPath: jsonString}, homework: string}，只做尺寸 / 形状约束 */
function validate_v1(array $root): bool
{
    $allowed = ['prefs', 'plans', 'homework'];
    foreach (array_keys($root) as $k) {
        if (!in_array($k, $allowed, true)) {
            return false;
        }
    }
    if (!isset($root['plans']) || !is_array($root['plans']) || count($root['plans']) === 0 || count($root['plans']) > 60) {
        return false;
    }
    foreach ($root['plans'] as $rel => $text) {
        // 只允许 time/xxx.json、course/xxx.json、timetable/xxx.json，禁止任何路径穿越
        if (!is_string($rel) || !preg_match('#^(time|course|timetable)/[^/\\\\]{1,120}\.json$#u', $rel)) {
            return false;
        }
        if (!str_ok($text, 32 * 1024) || json_decode($text, true) === null) {
            return false;
        }
    }
    if (isset($root['prefs'])) {
        if (!is_array($root['prefs']) || count($root['prefs']) > 100) {
            return false;
        }
        foreach ($root['prefs'] as $k => $v) {
            if (!is_string($k) || strlen($k) > 60 || (is_string($v) && strlen($v) > 8 * 1024) || is_array($v)) {
                return false;
            }
        }
    }
    if (isset($root['homework']) && !str_ok($root['homework'], 32 * 1024)) {
        return false;
    }
    return true;
}


$action = $_GET['action'] ?? '';
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
if (!is_string($action)) fail(400, 'invalid action');
if ($method === 'OPTIONS') { header('Allow: GET, POST, OPTIONS'); respond(204, []); }
switch ($action) {
case 'upload':
    if ($method !== 'POST') fail(405, 'method not allowed');
    rate_limit('upload', UPLOADS_PER_HOUR, 3600);
    $body = read_body_limited(MAX_BODY_BYTES);
    if ($body === '' || !utf8_ok($body)) fail(400, 'empty or non-UTF-8 body');
    $root = json_decode($body, true, 16, JSON_BIGINT_AS_STRING);
    if (!is_array($root) || json_last_error() !== JSON_ERROR_NONE) fail(400, 'invalid JSON');
    $v2 = isset($root['timetables']) || isset($root['v']);
    if (($v2 && (($root['v'] ?? null) !== 2 || !validate_v2($root))) || (!$v2 && !validate_v1($root)))
        fail(422, 'payload does not look like a timetable export');
    $stored = GUARD . json_encode(['payload'=>$root, 'created_at'=>time(), 'expires_at'=>time()+EXPIRE_SECONDS], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    // Serialize quota checks + allocation; never unlink a candidate another request just created.
    $lock = @fopen(DATA_DIR . '/allocation.php', 'c+');
    if (!$lock || !flock($lock, LOCK_EX)) fail(503, 'storage busy');
    if (fstat($lock)['size'] === 0) { fwrite($lock, GUARD); fflush($lock); }
    $stats = cleanup();
    if ($stats['count']>=MAX_TOTAL_FILES || $stats['bytes']+strlen($stored)>MAX_TOTAL_BYTES) $stats=cleanup(true);
    if ($stats['count']>=MAX_TOTAL_FILES || $stats['bytes']+strlen($stored)>MAX_TOTAL_BYTES) {
        flock($lock,LOCK_UN); fclose($lock); fail(507,'storage full');
    }
    $code=null;
    for ($i=0;$i<CODE_RETRY;$i++) {
        $candidate=(string)random_int(100000,999999);
        // Respect still-live codes from the old service too.
        if (@is_file(LEGACY_DIR . '/' . $candidate . '.json')) continue;
        $path=code_path($candidate); $fp=@fopen($path,'x');
        if (!$fp) continue;
        $written=fwrite($fp,$stored); fflush($fp); fclose($fp); @chmod($path,0640);
        if ($written!==strlen($stored)) { @unlink($path); continue; }
        $code=$candidate; break;
    }
    flock($lock,LOCK_UN); fclose($lock);
    if ($code===null) fail(503,'could not allocate share code');
    respond(200,['success'=>true,'code'=>$code,'expires_in'=>'7天','expires_in_days'=>7]);
case 'download':
    if ($method!=='GET') fail(405,'method not allowed');
    if (!isset($_GET['code']) || !is_string($_GET['code']) || !is_code($_GET['code'])) fail(400,'invalid code format');
    rate_limit('download',DOWNLOADS_PER_MIN,60);
    $code=$_GET['code']; $path=code_path($code); $legacy=false;
    if (@is_file($path)) {
        $stored=read_guarded($path);
    } else {
        $path=LEGACY_DIR . '/' . $code . '.json'; $legacy=true;
        if (!is_file($path) || (@filesize($path) ?: 0) > 600000) fail(404,'code not found or expired');
        $stored=json_decode((string)@file_get_contents($path),true,32);
    }
    if (!is_array($stored) || !isset($stored['payload'])) fail(410,'share data corrupted');
    $expires=(int)($stored['expires_at'] ?? ((int)($stored['created_at'] ?? 0)+EXPIRE_SECONDS));
    if ($expires<=time()) {
        // Do not delete old storage during the migration; rollback remains possible.
        if (!$legacy) @unlink($path);
        fail(404,'code not found or expired');
    }
    cleanup();
    respond(200,['success'=>true,'payload'=>$stored['payload'],'created_at'=>$stored['created_at']??null,'expires_at'=>$expires]);
case 'health':
    if ($method!=='GET') fail(405,'method not allowed');
    respond(200,['success'=>true,'service'=>'smartschedule-share','version'=>'2.1','max_body_kb'=>64,'legacy_compatible'=>true]);
case '':
    respond(200,['service'=>'智能课表分享码服务','version'=>'2.1','status'=>'running']);
default:
    fail(404,'unknown action');
}
