# 崩溃日志查看说明

这份文档是给以后排查 `AtommMaster` 真机闪退时用的。

## 1. 先去哪里看

程序如果发生崩溃、闪退，优先看下面两个目录：

`/storage/emulated/0/Android/data/com.auto.master/files/diagnostics/crashes/`

`/storage/emulated/0/Android/data/com.auto.master/files/diagnostics/run_logs/`

如果你是直接在手机文件管理器里找，一般路径会显示成：

`Android/data/com.auto.master/files/diagnostics/crashes`

`Android/data/com.auto.master/files/diagnostics/run_logs`

## 2. 这两个目录分别是什么

### `crashes`

这里放的是崩溃日志文件，文件名通常长这样：

`fatal_crash_20260401_153012_123.log`

或者：

`handled_crash_20260401_153012_123.log`

含义：

- `fatal_crash_*.log`
  代表程序真的崩了，通常里面会有 Java 异常堆栈、线程名、当时正在运行的项目/Task/节点、内存摘要。

- `handled_crash_*.log`
  代表程序内部捕获到了异常，但不一定已经闪退。这类文件也很有参考价值。

### `run_logs`

这里放的是运行过程日志，文件名通常长这样：

`live_run_20260401_152900_456.log`

这个日志是“运行时实时写入”的，不是等正常结束才写。

所以即使程序中途闪退，这里通常也能看到：

- 最后跑到了哪个项目
- 最后跑到了哪个 Task
- 最后开始/完成了哪个节点
- 闪退前最后几秒做了什么

## 3. 出现闪退后怎么排查

推荐按这个顺序看：

1. 先打开 `crashes` 目录，找时间最新的 `fatal_crash_*.log`
2. 看里面的 `stacktrace`
3. 看里面的 `project=...`、`task=...`、`operation=...`
4. 再打开 `run_logs` 目录，找同一时间附近最新的 `live_run_*.log`
5. 看最后 20 到 50 行，确认崩溃前最后执行的是哪个节点

## 4. 日志里重点看什么

### 如果看到这些关键词

- `OutOfMemoryError`
  一般是内存不够了，重点怀疑图片、Bitmap、OpenCV Mat、模板缓存。

- `NullPointerException`
  一般是某个对象为空。

- `IllegalStateException`
  一般是状态不对，比如 View、Window、MediaProjection、无障碍服务状态异常。

- `SIGSEGV`、`native crash`、没有 Java 堆栈但直接闪退
  一般更像是 native 层问题，可能和 OpenCV、截图、底层库有关。

### 运行日志里重点看

例如：

`[start] op_xxx | 模板匹配`

`[done] op_xxx | success=true | 35ms`

如果日志停在某个 `[start]` 后面没有对应 `[done]`，通常说明问题就出在这个节点附近。

## 5. 如果 `crashes` 目录里没有日志怎么办

这通常有几种可能：

- 不是 Java 崩溃，而是 native 崩溃
- 被系统直接杀进程
- 崩溃点来不及写入文件

这时候继续看：

1. `run_logs` 最后一份日志
2. 用 `adb logcat` 抓系统日志

## 6. 用 adb 抓崩溃日志

手机连接电脑后，可以执行：

```powershell
adb logcat -d > crash_logcat.txt
```

如果想只看和应用相关的内容，可以先简单搜：

```powershell
Select-String -Path .\crash_logcat.txt -Pattern "com.auto.master","AndroidRuntime","FATAL EXCEPTION","libc","DEBUG"
```

重点关键词：

- `FATAL EXCEPTION`
- `AndroidRuntime`
- `com.auto.master`
- `libc`
- `signal 11`
- `SIGSEGV`

## 7. 你下次崩溃后最好发我什么

如果你想让我帮你继续定位，最好把下面这几样一起发我：

1. `crashes` 目录里最新那份 `fatal_crash_*.log`
2. `run_logs` 目录里同一时间最新那份 `live_run_*.log`
3. 崩溃前你正在运行的项目名、Task 名
4. 大概运行了多久后崩溃
5. 当时是否在跑这些类型节点
   - 模板匹配
   - 图集匹配
   - 取色
   - 截图
   - OCR

## 8. 一个简单判断经验

- 有 `fatal_crash`，优先按堆栈定位
- 没有 `fatal_crash`，但 `run_logs` 停在模板匹配附近，优先怀疑图片/Mat/native
- 跑十几分钟后才崩，优先怀疑内存累计增长、缓存没释放、图片对象堆积

## 9. 这个项目现在已经加了什么

当前项目已经做了这几件事：

- 运行过程实时写 `run_logs`
- 程序崩溃前尽量写 `crashes`
- 崩溃日志里会记录项目、Task、节点、线程、堆栈、内存摘要

所以以后闪退时，先别只凭感觉猜，先去看这两个目录。

