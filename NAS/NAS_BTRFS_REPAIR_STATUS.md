# 飞牛OS (fnOS) NAS BTRFS 存储空间不格式化修复交接文档

**更新时间**：2026-11-03 14:28 CST  
**原则**：**绝不格式化（不格式化修复）**，保留 1.55 TiB 全部用户数据。  
**最终状态**：**已 100% 修复完成，并通过整机重启（`reboot`）验证开机自动 `rw` 挂载及服务自启！🎉**

---

## 一、连接与环境信息

| 项目 | 信息 |
| :--- | :--- |
| **NAS 系统** | 飞牛OS (fnOS)，Debian Linux 内核，自带 `btrfs-progs v6.2` |
| **NAS 当前局域网 IP** | `192.168.10.8`（重启后 DHCP 分配为 `.10.8`，原为 `.10.7`） |
| **SSH 账号 / 密码** | `root` / `13161312286z`（端口 `22`） |
| **数据盘设备路径** | `/dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0`（对应 `/dev/dm-0`） |
| **BTRFS UUID** | `1fba27f1-61a3-4f3b-a155-cc4d2074f016` |
| **SSD 缓存状态** | 已通过 `lvconvert --yes --splitcache` 永久剥离，当前为纯 HDD 线性逻辑卷（底层 `/dev/md127` -> `/dev/sda1`） |
| **当前正在运行的后台任务** | **全部修复与挂载已完成！`/vol1` 已正常 `rw` 挂载，Docker / SMB 服务已全面恢复运行 ✅** |
| **当前任务日志路径** | NAS 本机 `/root/btrfs_readonly_nov3.log`（`--init-csum-tree` 历史日志 `/root/btrfs_init_csum.log`） |

---

## 二、2026-11-02 上午已完成的关键突破（外科手术 + 校验和树重建启动）

### 1. 外科手术修复 10 个损坏的 `CSUM_TREE` 叶子节点头部（已完成 ✅）
通过对 `/root/btrfs_repair_nov.log` 的深入分析，定位并原地修复了全部 10 个头部字段乱码的 `CSUM_TREE`（`root=7`）叶子节点（每个节点在 `METADATA|DUP` 下有 2 个物理镜像副本，共修复 20 个物理镜像块，原始 16KB 备份保存在 NAS `/root/btrfs_leaf_backups/`）：

- **7 个 `generation` 乱码叶子块**（已修正回父节点期望 `wanted` 值并重算 `CRC32C`）：
  1. `595279872`（物理偏移 `603668480`, `1677410304`）：`28316086345258983` -> `645095`
  2. `777366192128`（物理偏移 `732277424128`, `733351165952`）：`12200024205809718` -> `645174`
  3. `1867655987200`（物理偏移 `1662579687424`, `1663653429248`）：`58324724341978327` -> `439511`
  4. `1867783831552`（物理偏移 `1662707531776`, `1663781273600`）：`37875006243723592` -> `383304`
  5. `1868193153024`（物理偏移 `1663116853248`, `1664190595072`）：`9619098413628776` -> `64872`
  6. `2421788360704`（物理偏移 `48827318272`, `49901060096`）：`12375031489878183` -> `645287`
  7. `2422134128640`（物理偏移 `49173086208`, `50246828032`）：`63854436921480110` -> `627630`

- **3 个 `owner` 乱码叶子块**（高字节污染导致 `owner` 非 7，已修正回 `owner = 7 (CSUM_TREE)` 并重算 `CRC32C`）：
  1. `776398864384`（物理偏移 `731310096384`, `732383838208`）：`62503462255984647` -> `7`
  2. `1867436343296`（物理偏移 `1662360043520`, `1663433785344`）：`38755159798448135` -> `7`
  3. `2422064775168`（物理偏移 `49103732736`, `50177474560`）：`5666037122990087` -> `7`

- **同步 Extent 树记录（已完成 ✅）**：
  随后运行了 5 分钟的 `btrfs check --repair`（日志 `/root/btrfs_repair_nov_step2.log`），成功将 Extent 树中被污染的 7 个 `extent_item` 的 `generation` 重置回合法值（`Reset extent item ... generation to 645095` 等）。
  - 全盘 **`parent transid verify failed` 降为 0**！
  - 全盘 **`invalid generation` / `owner mismatch` 崩溃点降为 0**！
  - 全盘 **`[3/7] checking free space tree` 错误降为 0**！

### 2. 挂载读写实测与最后一个问题的根因定位
- 挂载 `/vol1` 为 `rw` 后，解决了此前 51 秒即因 `invalid generation` 掉只读的问题，且修复了 `/vol1/docker/volumes/metadata.db`（`ino 17115`）缺失校验和的页面。
- 但当启动 Docker 进行较大规模新数据写入时，内核在向第一个数据块组（`3251634176 .. 4325376000`）分配空闲空间并追加校验和时，触发了：
  ```text
  BTRFS critical (device dm-0): corrupt leaf: root=7 block=140165120 slot=31, csum end range (3587031040) goes beyond the start range (3587026944) of the next csum item
  ```
- **根因查明**：
  通过 `btrfs inspect-internal dump-tree -b 45268992` 和 `56279040` 检查发现，`CSUM_TREE` 中残留有**未分配空闲空间的孤儿校验和条目（orphan csum items，如 `3587026944`、`3619020800`）**。这些空间在 `EXTENT_TREE` 和 `FREE_SPACE_TREE` 中已被标记为空闲未分配，但旧 `CSUM_TREE` 里还留着它们的 4 字节校验和；一旦内核把这段空闲空间分配给新文件并扩展前一条 `EXTENT_CSUM` 记录，就会撞上后面残留的孤儿 `EXTENT_CSUM` 条目，触发 `write time tree block corruption`！

### 3. 终极根治操作：`--init-csum-tree` 已完成 ✅（2026-11-03 01:13:14）
要同时彻底清除所有残留孤儿校验和（防止写入时重叠）并补齐所有文件缺失的校验和（消除读取时 `some csum missing`），标准且唯一的根治方案是重建校验和树（`--init-csum-tree`）。
由于此前阻碍 `--init-csum-tree` 的 10 个损坏叶子节点头部已被我们全部修好，`--init-csum-tree` 顺利跑完全程！

- **启动时间**：`2026-11-02 12:02:06 CST`
- **完成时间**：`2026-11-03 01:13:14 CST`（总耗时 `13h 11m`）
- **后台命令**：
  ```bash
  yes | btrfs check --repair --init-csum-tree /dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0 >> /root/btrfs_init_csum.log 2>&1
  ```
- **完成结果**：
  1. **`Creating a new CRC tree` 100% 完成**：全盘读取 `1,731.03 GB`，成功写入 `5.97 GB` 全新 `CSUM_TREE`；
  2. **`[1/7] checking root items` 通过**（`Fixed 0 roots.`）；
  3. **`[2/7] checking extents` 清理完成**：共输出 `790,128` 行日志，将旧 `root 7` 遗留的全部 **`131,683` 个未引用 16KB 叶子块记录**（从逻辑地址 `30,457,856` 一直到最后一个块组 `2,422,391,799,808`）逐一回收提交（superblock `generation` 推进至 `2907405`）；
  4. 在 `[2/7]` 最后一步修正 `super bytes used` 时，因现有 `free-space-tree (v2)` 缓存与已回收的旧 extent 范围存在微小偏差触发 `remove_free_space_extent` 断言退出（`exit=134`），但此前全部新 `CSUM_TREE` 和 `131,683` 个旧 extent 删除事务均已安全落盘提交！
- **全程进度追踪记录（归档）**：

| 检查时间 | 已运行时间 | `/proc/113903/io` `read_bytes` | 已读取数据量 | 进度占比 | 阶段平均速度 | 报错数 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `12:02:06` | `0m` | `0` | `0 GB` | `0.0%` | - | `0` |
| `12:04:20` | `2m` | `3,186,106,368` | `3.19 GB` | `0.2%` | `~72.7 MB/s` | `0` |
| `14:41:35` | `2h 39m` | `461,320,675,328` | `461.32 GB` | `27.1%` | `~48.4 MB/s` | `0` |
| `15:24:32` | `3h 22m` | `538,273,165,312` | `538.27 GB` | `31.6%` | `~44.4 MB/s` | `0` |
| `15:36:25` | `3h 34m` | `565,268,967,424` | `565.27 GB` | `33.2%` | `~36.1 MB/s` | `0` |
| `15:46:48` | `3h 45m` | `588,169,162,752` | `588.17 GB` | `34.5%` | `~35.1 MB/s` | `0` |
| `16:06:43` | `4h 05m` | `637,640,704,000` | `637.64 GB` | `37.4%` | `~39.5 MB/s` | `0` |
| `16:26:45` | `4h 25m` | `689,550,094,336` | `689.55 GB` | `40.5%` | `~41.2 MB/s` | `0` |
| `16:46:48` | `4h 45m` | `751,651,823,616` | `751.65 GB` | `44.1%` | `~49.2 MB/s` | `0` |
| `17:07:04` | `5h 05m` | `824,586,424,320` | `824.59 GB` | `48.4%` | `~57.2 MB/s` | `0` |
| `17:26:44` | `5h 25m` | `882,094,379,008` | `882.09 GB` | `51.8%` | `~46.5 MB/s` | `0` |
| `17:46:46` | `5h 45m` | `934,266,765,312` | `934.27 GB` | `54.8%` | `~41.4 MB/s` | `0` |
| `18:06:44` | `6h 05m` | `977,863,487,488` | `977.86 GB` | `57.4%` | `~34.7 MB/s` | `0` |
| `18:26:44` | `6h 25m` | `1,022,326,374,400` | `1,022.33 GB` | `60.0%` | `~35.3 MB/s` | `0` |
| `20:34:20` | `8h 32m` | `1,355,327,119,360` | `1,355.33 GB` | `79.5%` | `~41.5 MB/s` | `0` |
| `21:26:59` | `9h 25m` | `1,535,217,909,760` | `1,535.22 GB` | `90.1%` | `~54.3 MB/s` | `0` |
| `21:46:45` | `9h 45m` | `1,564,124,770,304` | `1,564.12 GB` | `91.8%` | `~23.2 MB/s` | `0` |
| `22:06:49` | `10h 05m` | `1,613,636,890,624` | `1,613.64 GB` | `94.7%` | `~39.2 MB/s` | `0` |
| `22:26:45` | `10h 25m` | `1,680,617,058,304` | `1,680.62 GB` | `98.6%` | `~53.4 MB/s` | `0` |
| `22:46:48` | `10h 45m` | `1,715,748,548,608` | `1,715.75 GB`（逻辑 `rchar` `1,687.15 GB`） | `~99.0%` | `~27.8 MB/s` | `0` |
| `23:06:47` | `11h 05m` | `1,731,025,051,648` | `1,731.03 GB`（已写入 `5.97 GB` 新 CRC 树） | **`100%`（已进入 `[2/7] checking extents`）** | 清理旧 `root 7` 引用中（日志 `11,838` 行） | `0` |
| `23:27:07` | `11h 25m` | `1,731,025,051,648` | `1,731.03 GB`（累计写入 `15.74 GB`） | **`[2/7] checking extents` 进行中** | 清理旧 `root 7` 引用中（日志 `135,437` 行，`~22,568` 个旧叶子已回收） | `0` |
| `23:46:47` | `11h 45m` | `1,731,025,051,648` | `1,731.03 GB`（累计写入 `22.92 GB`） | **`[2/7] checking extents` 进行中** | 清理旧 `root 7` 引用中（日志 `253,625` 行，`~42,265` 个旧叶子已回收） | `0` |
| `00:06:51` | `12h 05m` | `1,731,025,051,648` | `1,731.03 GB`（累计写入 `31.85 GB`） | **`[2/7] checking extents` 进行中** | 清理旧 `root 7` 引用中（日志 `386,226` 行，`~64,366` 个旧叶子已回收） | `0` |
| `00:26:59` | `12h 25m` | `1,731,025,051,648` | `1,731.03 GB`（累计写入 `39.87 GB`） | **`[2/7] checking extents` 进行中** | 清理旧 `root 7` 引用中（日志 `498,261` 行，`~83,038` 个旧叶子已回收） | `0` |
| `00:46:47` | `12h 45m` | `1,731,025,051,648` | `1,731.03 GB`（累计写入 `48.20 GB`） | **`[2/7] checking extents` 进行中** | 清理旧 `root 7` 引用中（日志 `618,675` 行，`~103,107` 个旧叶子已回收） | `0` |
| `01:06:47` | `13h 05m` | `1,731,025,051,648` | `1,731.03 GB`（累计写入 `57.61 GB`） | **`[2/7] checking extents` 尾声（`2.42 TB` 块组）** | 清理旧 `root 7` 引用中（日志 `749,050` 行，`~124,836` 个旧叶子已回收） | `0` |
| `01:13:14` | `13h 11m` | `1,731,025,051,648` | `1,731.03 GB`（累计写入 `~60.5 GB`） | **`FINISHED` ✅** | 全部 `131,683` 个旧 `root 7` 叶子引用回收完毕（日志 `790,128` 行） | `0`（末尾 `free-space-tree` 待重建） |

- **预计完成时间**：按当前平均速度（`44 ~ 48 MB/s`），剩余约 `1,166 GB` 数据预计还需要 **6.5 ~ 7.5 小时**（大约今晚 **22:00 ~ 23:00** 左右完成）。**请勿中断该进程**。

---

## 三、白天 / 晚上如何查看进度与后续收尾步骤

### 1. 检查 `--init-csum-tree` 是否还在运行
SSH 登录 `root@192.168.10.8`（密码 `13161312286z`）：
```bash
# 查看进程是否仍在运行
pgrep -a btrfs | grep -v kworker

# 查看已读取的数据量（除以 1.704e12 即为当前百分比）
cat /proc/113903/io

# 查看实时日志尾部与行数
wc -l /root/btrfs_init_csum.log
tail -n 30 /root/btrfs_init_csum.log

# 查看磁盘实时读写吞吐（确认机械盘正在高速读取计算 CRC）
iostat -xm 2 3 /dev/sda
```
> **注意**：在 `Creating a new CRC tree` 阶段，工具会先清空旧 `CSUM_TREE` 并遍历全盘数据块计算 CRC，期间日志可能长时间停留在 `10 9 8 7 6 5 4 3 2 1` 而不输出新行（因为 `btrfs check` 缓冲输出且计算阶段无报错不打印），只要 `pgrep -a btrfs` 还在且 `cat /proc/113903/io` 的 `read_bytes` 在持续增长，就说明一切正常！

### 2. `btrfs check --readonly` 完整复检结果（2026-11-03 10:42 完成 ✅）
`/root/btrfs_readonly_nov3.log` 完整输出仅 26 行，核心树结构与校验和树**全部零错误通过**：
```text
Opening filesystem to check...
Checking filesystem on /dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0
UUID: 1fba27f1-61a3-4f3b-a155-cc4d2074f016
[1/7] checking root items
[2/7] checking extents
[3/7] checking free space tree
wanted bytes 81920, found 16384 for off 30441472
cache appears valid but isn't 30408704
there is no free space entry for 776346566656-776346583040
cache appears valid but isn't 776345747456
there is no free space entry for 1867267506176-1867267522560
cache appears valid but isn't 1867267440640
there is no free space entry for 2421355757568-2421355773952
cache appears valid but isn't 2421318221824
[4/7] checking fs roots
[5/7] checking only csums items (without verifying data)
[6/7] checking root refs
[7/7] checking quota groups skipped (not enabled on this FS)
found 1704134660096 bytes used, error(s) found
total csum bytes: 1661590036
total tree bytes: 2666463232
total fs tree bytes: 771620864
total extent tree bytes: 86360064
btree space waste bytes: 318495387
file data blocks allocated: 2357123534848
 referenced 1705084125184
```
- **`[1/7] checking root items`**：**0 报错** ✅
- **`[2/7] checking extents`**：**0 报错** ✅（`131,683` 个旧 `root 7` 未引用块已彻底清空，`total tree bytes` 从 `5.41 GB` 降至健康的 `2.67 GB`！）
- **`[4/7] checking fs roots`**：**0 报错** ✅（此前所有的 `some csum missing` 已 100% 消失！）
- **`[5/7] checking only csums items`**：**0 报错** ✅（孤儿校验和与重叠范围已 100% 消除！）
- **`[6/7] checking root refs`**：**0 报错** ✅
- **唯一剩余收尾项（`[3/7] checking free space tree`）**：仅剩 4 个元数据块组的空闲空间缓存（`space_cache=v2`）因刚回收旧叶子需刷新，只需执行 `btrfs check --clear-space-cache v2` 清除旧缓存并在挂载时自动重建即可。

### 3. 最终收尾与恢复服务（2026-11-03 10:46 已全部执行完成 ✅）
1. **清理 `v2` 空闲空间缓存（已完成，`CLEAR_EXIT=0`）**：
   ```text
   Clear free space cache v2
   free space cache v2 cleared
   ```
2. **挂载 `/vol1` 并由内核自动重建全新 `free-space-tree (v2)`（已完成 ✅）**：
   ```text
   /dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0 /vol1 btrfs rw,relatime,discard=async,space_cache=v2,subvolid=5,subvol=/ 0 0
   Filesystem                                               Size  Used Avail Use% Mounted on
   /dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0  1.9T  1.6T  265G  86% /vol1
   ```
   内核 `dmesg` 确认空闲空间树已全新生成并启用：
   ```text
   [Tue Nov  3 10:45:01 2026] BTRFS info (device dm-0): creating free space tree
   [Tue Nov  3 10:45:28 2026] BTRFS info (device dm-0): setting compat-ro feature flag for FREE_SPACE_TREE (0x1)
   [Tue Nov  3 10:45:28 2026] BTRFS info (device dm-0): setting compat-ro feature flag for FREE_SPACE_TREE_VALID (0x2)
   [Tue Nov  3 10:45:29 2026] BTRFS info (device dm-0): checking UUID tree
   [Tue Nov  3 10:45:29 2026] BTRFS info (device dm-0): turning on async discard
   [Tue Nov  3 10:45:29 2026] BTRFS info (device dm-0): enabling free space tree
   ```
3. **20MB 读写落盘压测 + 恢复 `containerd` / `docker` / `smbd` 服务（已完成 ✅）**：
   - `dd` 写入 20MB 随机数据并 `sync` + 删除 `sync`：**`WRITE_TEST_OK`**，不再触发 `write time tree block corruption` 或掉只读！
   - `btrfs device stats /vol1` 全部 5 项错误计数器均为 **`0`**！
   - `containerd`、`docker`（含 `1Panel-ddns-go` 容器）、`smbd` 已全部正常启动并稳定运行！
4. **整机重启（`reboot`）开机自动挂载与读写验证（2026-11-03 14:27 完成 ✅）**：
   - 重启前连续运行 3 小时 40 分钟保持 `rw` 零错误（`PRE_REBOOT_RW_OK`）。
   - 执行 `reboot` 整机重启后，飞牛 OS 原生存储服务在 **2 秒内（`14:26:08` -> `14:26:10`）开机自动挂载 `/vol1` 为 `rw`**（含 `trimacl,space_cache=v2`）：
     ```text
     /dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0 /vol1 btrfs rw,relatime,discard=async,trimacl,space_cache=v2,subvolid=5,subvol=/ 0 0
     ```
   - 重启后读写测试通过（`POST_REBOOT_RW_OK`），`btrfs device stats /vol1` 全部 5 项计数保持 **`0`**，Docker 容器与 FRP 隧道全部随开机自动恢复，内核 `dmesg` **100% 干净零报错**！

---

## 四、附：如果未来需要再次干净卸载 `/vol1` 的标准命令
飞牛 OS 的 `trim_file_monit` 进程和 Docker 容器（如 `ddns-go`）会在私有挂载命名空间中持有 `/vol1`。如需卸载并让 `Open count` 归零，请按以下顺序执行（切勿直接先跑 `umount -l /vol1`）：
```bash
systemctl stop docker docker.socket containerd smbd 2>/dev/null || true
pkill -9 ddns-go containerd-shim trim_file_monit 2>/dev/null || true
fuser -km -9 /vol1 2>/dev/null || true
for p in /proc/[0-9]*; do
    pid=${p#/proc/}
    grep -q vol1 $p/mountinfo 2>/dev/null && nsenter -t $pid -m umount -R -l /vol1 2>/dev/null
done
umount /vol1 2>/dev/null || true
dmsetup info trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0 | grep 'Open count'
```
