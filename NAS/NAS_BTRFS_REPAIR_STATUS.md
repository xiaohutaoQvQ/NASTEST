# 飞牛OS (fnOS) NAS BTRFS 存储空间不格式化修复交接文档

**更新时间**：2026-11-02 15:38 CST  
**原则**：**绝不格式化（不格式化修复）**，保留 1.55 TiB 全部用户数据。

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
| **当前正在运行的后台任务** | **`PID 113903`**：`btrfs check --repair --init-csum-tree /dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0` |
| **当前任务日志路径** | NAS 本机 `/root/btrfs_init_csum.log`（启动脚本 `/root/run_init_csum.sh`） |

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

### 3. 终极根治操作：已在 NAS 后台启动 `--init-csum-tree`（进行中 ⏳）
要同时彻底清除所有残留孤儿校验和（防止写入时重叠）并补齐所有文件缺失的校验和（消除读取时 `some csum missing`），标准且唯一的根治方案是重建校验和树（`--init-csum-tree`）。
由于此前阻碍 `--init-csum-tree` 的 10 个损坏叶子节点头部已被我们全部修好，现在 `--init-csum-tree` 可以完全无障碍运行！

- **启动时间**：`2026-11-02 12:02:06 CST`
- **后台命令**：
  ```bash
  yes | btrfs check --repair --init-csum-tree /dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0 >> /root/btrfs_init_csum.log 2>&1
  ```
- **当前状态**：`PID 113903` 正在 NAS 后台独占运行（`Open count: 0`），处于 `Creating a new CRC tree` 阶段。
- **实时进度追踪记录（总需读取约 `1,704 GB` 已分配数据）**：

| 检查时间 | 已运行时间 | `/proc/113903/io` `read_bytes` | 已读取数据量 | 进度占比 | 阶段平均速度 | 报错数 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `12:02:06` | `0m` | `0` | `0 GB` | `0.0%` | - | `0` |
| `12:04:20` | `2m` | `3,186,106,368` | `3.19 GB` | `0.2%` | `~72.7 MB/s` | `0` |
| `14:41:35` | `2h 39m` | `461,320,675,328` | `461.32 GB` | `27.1%` | `~48.4 MB/s` | `0` |
| `15:24:32` | `3h 22m` | `538,273,165,312` | `538.27 GB` | `31.6%` | `~44.4 MB/s` | `0` |
| `15:36:25` | `3h 34m` | `565,268,967,424` | `565.27 GB` | `33.2%` | `~36.1 MB/s` | `0` |

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

### 2. 当 `/root/btrfs_init_csum.log` 显示 `=== INIT CSUM TREE FINISHED ===` 后的收尾步骤
1. **只读复检确认零错误**：
   ```bash
   btrfs check --readonly /dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0
   ```
2. **挂载 `/vol1` 并恢复服务**：
   ```bash
   mount /dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0 /vol1
   btrfs device stats -z /vol1
   systemctl start containerd docker smbd
   ```
3. **验证读写与内核日志**：
   ```bash
   cat /proc/mounts | grep /vol1
   btrfs device stats /vol1
   dmesg -T | grep -iE 'btrfs' | tail -20
   ```

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
