#!/usr/bin/env bash
# ANR-05（背面起動 ANR）の WorkManager 経路を1コマンドで再現し、証拠（ApplicationExitInfo）まで表示する。
#
# 前提:
#   - エミュレータ/実機が1台だけ接続されている
#     （adb が PATH に無ければ ADB=~/Library/Android/sdk/platform-tools/adb を前置）
#   - アプリがインストール済みで、設定画面の demoMode で master / ANR-02 / ANR-05 が ON（要再起動）
#   - 実行中はデバイスに触らないこと（手でアプリを起動すると前面起動になり、仕掛けどおり ANR しない）
#
# 汚れた状態（前回の残骸・生き残りプロセス・期限切れ Work）から始めても通るように書いてある。
# 目覚ましは「前面起動のたびに REPLACE で1つだけ張り直す」設計なので、
# この スクリプト の 1/6（冷やす）→ 2/6（武装）を通れば必ず正常化する。
#
# 仕組みは README「連結レシピ：ANR-05 背面起動 ANR」と app/startup/StartupOrigin.kt の KDoc 参照。
set -u

PKG=com.pelantica.dorodorotimer
ADB=${ADB:-adb}

# 目覚ましの初期遅延（AnrLogUploadScheduler.INITIAL_DELAY_SECONDS と揃える）。
ARM_DELAY_SECONDS=30
# 「殺す」に使ってよい最大秒数。am kill は cached になるまで no-op なのでリトライで吸収する。
KILL_TIMEOUT_SECONDS=45
# 目覚ましが鳴る（＝プロセスが起こされる）のを待つ最大秒数。
WAKE_TIMEOUT_SECONDS=90
# 起こされてから無言 kill されるまでを待つ最大秒数（実測は約15秒）。
ANR_TIMEOUT_SECONDS=40

say()  { echo "▶ $*"; }
warn() { echo "⚠️ $*" >&2; }

# pid を1つだけ返す（複数プロセスや \r 混入を潰す）。生きていなければ空文字。
current_pid() {
  $ADB shell pidof "$PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}'
}

# 保留中の WorkManager ジョブ。名前空間付きで登録されるため素の "JOB #" では出ない
#   例: JOB androidx.work.systemjobscheduler:u0a237/9: adf1bc9 #AnrLogUploadWorker#@...@PKG/...SystemJobService
pending_jobs() {
  $ADB shell dumpsys jobscheduler 2>/dev/null \
    | grep -E "^[[:space:]]*JOB .*#AnrLogUploadWorker#.*$PKG" | tr -d '\r'
}

# OS 側の記録（ApplicationStartInfo）の最新1件＝「誰がこのプロセスを起こしたか」。
# reason は "START ACTIVITY" のように空白を含むので grep -oE "reason=[A-Z_]+" では欠ける。
# startType= までを切り出す。ANR のトレースは巨大で logcat をすぐ押し流すため、
# 背面起動の判定にはログではなくこの記録を使う（こちらは消えない）。
os_start_reason() {
  $ADB shell dumpsys activity start-info "$PKG" 2>/dev/null | tr -d '\r' \
    | sed -n 's/.*reason=\(.*\) startType=.*/\1/p' | head -1
}

# アプリ自身の判定（StartupOrigin のログ）。取れれば嬉しい程度の補足情報。
#   必ず「今回起こされた pid」で絞ること。絞らないと直前の**前面**起動の行を拾ってしまい
#   「background=false」と表示されて真逆の印象になる（実際にそれで一度混乱した）。
#   ANR のトレースは巨大で logcat をすぐ押し流すので、取れないことも普通にある。
app_start_verdict() {
  $ADB logcat -d -s StartupOrigin 2>/dev/null | tr -d '\r' \
    | grep "start reason=" | grep " $1 " | tail -1
}

# 保留ジョブから jobId を取り出す（"...systemjobscheduler:u0a237/19: ..." の 19）。
pending_job_id() {
  pending_jobs | sed -n 's#.*systemjobscheduler:u0a[0-9]*/\([0-9]*\):.*#\1#p' | head -1
}

# JobScheduler のクォータを確保する。
#   充電していない端末では QuotaController が働き、短時間に何度もジョブを走らせると
#   WITHIN_QUOTA が外れて「保留のまま鳴らない」状態になる（デモを繰り返すと必ず踏む）。
#   充電中はクォータが免除されるので、給電状態でなければ給電中に見せかける。
#   元に戻すには: adb shell cmd battery reset
ensure_job_quota() {
  local powered
  powered=$($ADB shell dumpsys battery 2>/dev/null | tr -d '\r' \
    | grep -E "(AC|USB|Wireless) powered: true" | head -1)
  if [ -n "$powered" ]; then
    say "    給電中: ジョブのクォータ制限は掛からない"
    return 0
  fi
  say "    非給電のため給電中に見せかける（クォータ制限で目覚ましが鳴らなくなるのを防ぐ）"
  say "    元に戻すには: adb shell cmd battery reset"
  $ADB shell cmd battery set ac 1 > /dev/null 2>&1
  $ADB shell cmd battery set status 2 > /dev/null 2>&1
  sleep 3
}

# Android 15+ の「フレキシブルなジョブ実行」を切る。
#   急ぎでないジョブは電力効率のためまとめて実行されるので、初期遅延が切れても
#   すぐには鳴らない（FLEXIBILITY 制約）。デモでは待たされるだけなので無効化する。
#   元に戻すには: adb shell cmd jobscheduler reset-flex-policy
disable_flex_batching() {
  $ADB shell cmd jobscheduler disable-flex-policy > /dev/null 2>&1
}

# プロセスが確実に死ぬまで am kill を撃ち続ける。
#   am kill は「cached になるまで no-op」なので、1発撃って sleep する方式だと取りこぼす。
#   pid が消えたか / 別 pid に変わっていないかを毎回確かめ、変化を実況する。
#   force-stop はジョブもアラームも消えてしまうので絶対に使わない。
ensure_dead() {
  local label="$1" deadline pid first_pid last_pid=""
  pid=$(current_pid)
  if [ -z "$pid" ]; then
    say "   $label: 既に死んでいる"
    return 0
  fi
  first_pid=$pid
  $ADB shell input keyevent KEYCODE_HOME > /dev/null 2>&1
  deadline=$(( $(date +%s) + KILL_TIMEOUT_SECONDS ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    $ADB shell am kill "$PKG" > /dev/null 2>&1
    sleep 1
    pid=$(current_pid)
    if [ -z "$pid" ]; then
      say "   $label: pid=$first_pid を kill 完了"
      return 0
    fi
    if [ "$pid" != "$last_pid" ] && [ "$pid" != "$first_pid" ]; then
      say "   $label: pid が $last_pid → $pid に変わった（起こし直された）。続行"
    fi
    last_pid=$pid
  done
  warn "$label: ${KILL_TIMEOUT_SECONDS}秒 am kill を撃ち続けても pid=$pid が死にません。"
  warn "  前面に居る（cached になっていない）か、前景サービスが動いている可能性があります。"
  warn "  デバイスの画面を HOME にしてから再実行してください（force-stop は使わないこと）。"
  return 1
}

# ---------------------------------------------------------------- 0/6 事前確認
if [ -z "$($ADB shell pm path "$PKG" 2>/dev/null)" ]; then
  warn "$PKG がインストールされていません。"
  exit 1
fi
mult=$($ADB shell getprop ro.hw_timeout_multiplier | tr -d '\r')
say "0/6 ro.hw_timeout_multiplier='${mult:-<空>}'（空か 1 なら起動締切は15秒）"
leftover=$(pending_jobs | grep -c . | tr -d ' ')
say "    開始時の保留ジョブ: ${leftover}件（前回の残骸があってもこの後の武装で1件に揃う）"
ensure_job_quota
disable_flex_batching

# ---------------------------------------------------------------- 1/6 冷やす
say "1/6 冷たい状態にする（プロセスが生きていると am start が温かい復帰になり onCreate＝武装が走らない）"
ensure_dead "冷却" || exit 1

# ---------------------------------------------------------------- 2/6 武装
say "2/6 冷起動して武装（onCreate が Work を REPLACE で1つ enqueue・初期遅延${ARM_DELAY_SECONDS}秒）..."
# 起動そのものに約10秒かかる（ANR-02 の重い onCreate）。カウントダウンの起点は
# その onCreate の終盤＝am start が返る直前なので、armed_at は起動**後**に取る。
$ADB shell am start -W -n "$PKG/.MainActivity" > /dev/null 2>&1
armed_at=$(date +%s)
jobs_now=$(pending_jobs)
if [ -z "$jobs_now" ]; then
  warn "武装できていません（保留ジョブが0件）。"
  warn "  設定画面で master と ANR-05 が ON か、ON にした後アプリを再起動したかを確認してください。"
  exit 1
fi
say "    武装 OK: 保留ジョブ $(printf '%s\n' "$jobs_now" | wc -l | tr -d ' ')件"
printf '%s\n' "$jobs_now" | sed 's/^[[:space:]]*/      /'

# ---------------------------------------------------------------- 3/6 殺す
say "3/6 プロセスを kill（am kill をリトライ。force-stop はジョブごと消えるので使わない）..."
ensure_dead "kill" || exit 1
elapsed=$(( $(date +%s) - armed_at ))
say "    武装から ${elapsed}秒 で kill 完了（${ARM_DELAY_SECONDS}秒以内なら目覚ましは残っている）"
if [ "$elapsed" -ge "$ARM_DELAY_SECONDS" ]; then
  warn "  武装から${ARM_DELAY_SECONDS}秒を超えました。前面のまま消化された可能性があります（次段で判明）。"
fi
say "    ここからはデバイスに触らないこと"

# ---------------------------------------------------------------- 4/6 起床待ち
say "4/6 WorkManager がプロセスを起こすのを待機（最大${WAKE_TIMEOUT_SECONDS}秒）..."
woke=""
nudged=""
deadline=$(( $(date +%s) + WAKE_TIMEOUT_SECONDS ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  woke=$(current_pid)
  [ -n "$woke" ] && break
  # 初期遅延を過ぎたら JobScheduler を小突く。
  #   放っておいても鳴るが、JobScheduler は冷えたアプリのジョブをまとめて処理するので
  #   遅延が切れてから実際に鳴るまで更に1分以上かかることがある（実測: kill から約102秒後）。
  #   デモで2分待つ意味は無いので、遅延が切れたら強制実行で前に倒す。
  #   これは「鳴らせるはずのものを鳴らす」だけで、起こされ方（reason=JOB）も結果も変わらない。
  if [ -z "$nudged" ] && [ $(( $(date +%s) - armed_at )) -gt $(( ARM_DELAY_SECONDS + 5 )) ]; then
    nudged=1
    job_id=$(pending_job_id)
    if [ -n "$job_id" ]; then
      say "    初期遅延が切れたので jobId=$job_id を強制実行（待てば自然に鳴るが約1〜2分かかる）..."
      $ADB shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler "$PKG" "$job_id" \
        > /dev/null 2>&1
    fi
  fi
  sleep 1
done
if [ -z "$woke" ]; then
  warn "${WAKE_TIMEOUT_SECONDS}秒待っても起こされませんでした。"
  echo "   保留ジョブ:" >&2
  pending_jobs | sed 's/^[[:space:]]*/     /' >&2
  echo "   （0件なら武装が消化済み。アプリを一度前面で開き直すと張り直されます）" >&2
  echo "   保留はあるのに鳴らない場合は制約を確認:" >&2
  $ADB shell dumpsys jobscheduler 2>/dev/null | tr -d '\r' \
    | grep -A 20 "^  JOB .*#AnrLogUploadWorker#" | grep -E "^    (Un)?[Ss]atisfied" \
    | sed 's/^/     /' >&2
  echo "   WITHIN_QUOTA が Unsatisfied なら給電すれば解消します（adb shell cmd battery set ac 1）" >&2
  exit 1
fi

# 背面起動の可視化。ANR のトレースは logcat をすぐ押し流すので、判定は OS の記録を正とする。
reason=$(os_start_reason)
say "    起こされた！ pid=$woke"
say "    OS の記録 (ApplicationStartInfo): reason=${reason:-取得不可}   ← JOB なら WorkManager 経由"
verdict=$(app_start_verdict "$woke")
[ -n "$verdict" ] && say "    アプリの判定 (StartupOrigin):     $verdict"
case "$reason" in
  LAUNCHER|"START ACTIVITY")
    warn "前面起動です（実行中に手でアプリが起動された可能性）。"
    warn "  仕掛けどおり ANR しません。デバイスに触らずに再実行してください。"
    exit 1
    ;;
esac
case "$verdict" in
  *"lastExitWasAnr=true"*)
    warn "安全弁が作動しています（直前が ANR 死）。今回は軽く起動するので ANR しません。"
    warn "  一度アプリを普通に開いて閉じてから再実行してください。"
    exit 1
    ;;
esac
say "    重い onCreate が走行中。約15秒後に無言 kill されるはず..."

# ---------------------------------------------------------------- 5/6 ANR 確認
died=""
deadline=$(( $(date +%s) + ANR_TIMEOUT_SECONDS ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  [ -z "$(current_pid)" ] && { died=1; break; }
  sleep 1
done
if [ -z "$died" ]; then
  warn "${ANR_TIMEOUT_SECONDS}秒経ってもプロセスが生きています＝ANR していません。"
  warn "  ro.hw_timeout_multiplier（締切が倍増していないか）と ANR-02 / ANR-05 トグルを確認してください。"
  exit 1
fi

# ---------------------------------------------------------------- 6/6 証拠
say "5/6 無言 kill を確認（ダイアログは出ない）。ApplicationExitInfo の最新記録:"
exit_info=$($ADB shell dumpsys activity exit-info "$PKG" 2>/dev/null | tr -d '\r' | sed -n '5,9p')
printf '%s\n' "$exit_info" | sed 's/^/    /'
if printf '%s' "$exit_info" | grep -q "pid=$woke"; then
  if printf '%s' "$exit_info" | grep -q "reason=6 (ANR)"; then
    say "6/6 ✅ 期待どおり: 起こされた pid=$woke が reason=6 (ANR) で死んでいる"
  else
    warn "6/6 pid=$woke の記録はあるが reason=6 (ANR) ではありません。上の記録を確認してください。"
    exit 1
  fi
else
  warn "6/6 最新の ApplicationExitInfo が今回の pid=$woke ではありません。上の記録を確認してください。"
  exit 1
fi
echo
say "完了。次にアプリを普通に開くと（安全弁で軽く起動します）、Crashlytics がこの ANR を回収・送信します。"
