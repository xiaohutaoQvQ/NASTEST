# 飞牛OS (fnOS) NAS BTRFS 存储空间不格式化修复交接文档

**更新时间**：2026-11-02 11:25 CST  
**原则**：**绝不格式化（不格式化修复）**，保留 1.55 TiB 全部用户数据。

---

## 一、连接与环境信息

| 项目 | 信息 |
| :--- | :--- |
| **NAS 系统** | 飞牛OS (fnOS)，Debian Linux 内核，自带 `btrfs-progs v6.2` |
| **NAS 当前局域网 IP** | `192.168.10.8`（重启后 DHCP 分配为 `.10.8`，原为 `.10.7`） |
| **SSH 账号 / 密码** | `root` / `13161312286z`（端口 `22`） |
| **数据盘设备路径** | `/dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0`（对应 `/dev/dm-0`） |
| **挂载点** | `/vol1`（当前处于**已卸载**、`Open count: 0` 状态，可直接离线操作） |
| **BTRFS UUID** | `1fba27f1-61a3-4f3b-a155-cc4d2074f016` |
| **SSD 缓存状态** | 已通过 `lvconvert --yes --splitcache` 永久剥离，当前为纯 HDD 线性逻辑卷（底层 `/dev/md127` -> `/dev/sda1`） |
| **最近一次修复日志** | NAS 本机 `/root/btrfs_repair_nov.log`（启动脚本 `/root/run_repair_nov.sh`） |

---

## 二、来龙去脉：之前发生了什么？

1. **起因（SSD 回写缓存断电不一致）**：
   - NAS 原配置使用金百达 256GB SSD 作为 2TB 西数机械盘（WD20SPZX）的 LVM `dm-cache` 回写缓存（writeback）。
   - 早期因 SATA 线 CRC 错误及异常断电，导致 SSD 缓存与 HDD 底盘元数据产生 `transid` 版本断层。
2. **第一阶段（剥离 SSD 缓存 + 重建 Extent 树）**：
   - 通过 `lvconvert --splitcache` 剥离了损坏的 SSD 缓存。
   - 运行 `--init-extent-tree` 解决了无法挂载的问题，但导致 1.55 TiB 数据的 extent 引用与校验和树（`CSUM_TREE`，`root=7`）需要全量补回。
3. **第二阶段（9月~10月全量 `btrfs check --repair`）**：
   - 后台运行全量 `btrfs check --repair`，完成了超过 210 万次事务（文件系统 `generation` 推进至 `2794484`）。
   - **重大成果**：**1.55 TiB 数据容量和目录树已全部找回**（`/vol1/1000` 下的 `BT`、`javaProjects`、`KTV`、`MC`、`Photos` 等全部可读）。
4. **第三阶段（11月1日晚重启测试与 11月2日凌晨修复结果）**：
   - **11月1日晚重启现象**：开机后 `/vol1` 能挂载并在前 51 秒正常读写，但随后内核报：
     ```text
     BTRFS critical (device dm-0): corrupt leaf: block=2421349335040 slot=36 extent bytenr=1867783831552 len=16384 invalid generation, have 37875006243723592 expect (0, 2794486]
     BTRFS info (device dm-0 state EA): forced readonly
     ```
     内核树检查器（tree-checker）发现某个元数据块的 `generation` 是乱码大数 `37875006243723592`（远超当前 `superblock.generation` `2794486`），触发保护机制强制变为只读（`ro`）。
   - **11月2日凌晨 `btrfs check --repair` 结果（耗时仅 5 分钟，日志在 `/root/btrfs_repair_nov.log`）**：
     - `[1/7] checking root items`：0 错误。
     - `[2/7] checking extents`：文件树与数据块引用已全部健康，**仅剩 7 个 `CSUM_TREE`（`root=7`）叶子节点的 `generation` 字段异常**。
     - `[4/7] checking fs roots`：文件树结构正常，仅报部分 inode 缺少校验和（`errors 1000, some csum missing`）。

---

## 三、核心病灶定位（为什么普通 `btrfs check --repair` 没修好这 7 个块？）

通过 `btrfs inspect-internal dump-tree -b <bytenr>` 逐一排查发现，全盘仅剩的 7 个 `parent transid verify failed` / `invalid generation` 报错块，**100% 全部是 `CSUM_TREE`（校验和树，`root=7`）的 16KB 叶子节点**：

| 序号 | 逻辑字节地址 (`bytenr`) | 所属树 (`owner`) | 父节点期望 `generation` (`wanted`) | 叶子头部实际乱码 `generation` (`found`) |
| :---: | :--- | :---: | :--- | :--- |
| 1 | `595279872` | `CSUM_TREE` (root 7) | `645095` | `28316086345258983` (`0x006498b8b6bc4be7`) |
| 2 | `777366192128` | `CSUM_TREE` (root 7) | `645174` | `12200024205809718` (`0x002b57ac2b03a836`) |
| 3 | `1867655987200` | `CSUM_TREE` (root 7) | `439511` | `58324724341978327` (`0x00cf3613eb026cd7`) |
| 4 | `1867783831552` | `CSUM_TREE` (root 7) | `383304` | `37875006243723592` (`0x00868f407e1b9948`) |
| 5 | `1868193153024` | `CSUM_TREE` (root 7) | `64872` | `9619098413628776` (`0x00222bf28734b168`) |
| 6 | `2421788360704` | `CSUM_TREE` (root 7) | `645287` | `12375031489878183` (`0x002bf6db27d70ca7`) |
| 7 | `2422134128640` | `CSUM_TREE` (root 7) | `627630` | `63854436921480110` (`0x00e2dadffd7e0bae`) |

### 为什么会导致死结？
1. 这 7 个 `CSUM_TREE` 叶子节点的头部 CRC32C 校验和是合法的（因此 `btrfs check` 认为叶子本身没坏）。
2. 当运行普通 `btrfs check --repair` 时，工具看到叶子头部的 `generation` 是 `37875006243723592`，竟然**把 Extent 树里对应记录的 generation 也改成了这个乱码大数**：
   ```text
   Reset extent item (1867783831552) generation to 37875006243723592
   ```
3. 结果一挂载到 Linux 内核，内核的 `tree-checker` 严格校验 `extent_item.generation <= superblock.generation (2794486)`，一读到 `37875006243723592` 立刻判定 `corrupt leaf: invalid generation` 并强制只读（`forced readonly`）！

---

## 四、现在打算怎么处理？（下一步修复方案）

目前已确认**所有用户文件树（FS_TREE / subvolumes）和 Extent 树的主体结构都已经修复完成**，唯一阻碍正常 `rw` 读写挂载的只有两点：
1. 上述 **7 个 `CSUM_TREE` 叶子节点的头部 `generation` 乱码**（以及它们同步污染到 Extent 树里的 7 个 `extent_item` 的 `generation`）。
2. 部分文件缺少数据校验和（`errors 1000, some csum missing`，导致内核读取该文件时报 `csum hole found`）。

### 推荐执行步骤（两步走，先外科手术秒修 7 个块，再按需补全校验和）

#### 第一步：外科手术修正 7 个 `CSUM_TREE` 叶子的 `generation`（耗时 < 5 分钟，立竿见影）

1. **原理**：
   - 将上述 7 个 `CSUM_TREE` 叶子块（每个大小 `16384` 字节）头部的 `generation` 字段（位于块偏移 `0x50`，8 字节小端序 `uint64`）修改回父节点期望的 `wanted` 值（如 `645095`、`645174` 等，均小于 `2794486`）。
   - 重新计算这 7 个 16KB 叶子块的 `CRC32C` 校验和（对偏移 `0x20` 到 `0x4000` 共 `16352` 字节计算 `crc32c`，写入块偏移 `0x00` 的前 4 字节）。
2. **随后跑一次 5 分钟的 `btrfs check --repair`**：
   - 此时 `btrfs check --repair` 会自动输出：
     `Reset extent item (1867783831552) generation to 383304`（将 Extent 树里被污染的 7 个乱码 `generation` 自动重置回合法值！）。
   - 同时彻底消除所有的 `parent transid verify failed` 和内核 `invalid generation` 崩溃点！
   - 完成后直接挂载 `/vol1`，**将不再因为 `invalid generation` 触发 `forced readonly`！**

> **注意**：BTRFS 逻辑字节地址 (`logical bytenr`) 到块设备物理偏移 (`physical offset`) 需要通过 `CHUNK_TREE` 映射转换（可通过 `btrfs inspect-internal dump-tree -t chunk /dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0` 查看各个 `CHUNK_ITEM` 的 `logical -> physical stripe offset` 映射关系，在单盘 linear 模式下每个 chunk 是简单的线性加减偏移）。

#### 第二步：处理缺失的校验和（`some csum missing`）

完成第一步并确认可以正常挂载读写后，针对部分文件的 `some csum missing`（读取这些特定旧文件时会报 `csum hole` I/O error）：
- **方案 A（推荐，快速轻量）**：正常挂载使用，通过 `dmesg` 看到哪几个具体 inode 报 `csum failed root X ino Y`（主要是系统日志或临时文件，如 `root 5 ino 17115`），用 `find /vol1 -inum <ino>` 找到对应损坏缓存/日志文件删除或重新复制即可。
- **方案 B（彻底重建全盘校验和树）**：如果希望全盘零校验和警告，可在空闲时间运行：
  ```bash
  btrfs check --repair --init-csum-tree /dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0
  ```
  > 注：`--init-csum-tree` 会清空整个 `CSUM_TREE` 并重新读取全盘 1.55 TiB 数据计算 CRC32C，在机械硬盘上耗时较长（预计数小时至十余小时），建议先执行**第一步**恢复正常挂载读写后再决定是否需要运行。

---

## 五、常用操作速查（供白天/晚上接力使用）

### 1. 如果 `/vol1` 被自动挂载且 `Open count: 1` 无法卸载
千万不要先执行 `umount -l /vol1`（懒卸载会隐藏挂载点导致 `fuser` 找不到占用进程）。正确卸载释放 `Open count: 0` 的步骤：
```bash
# 1. 如果已经懒卸载但 Open count 仍为 1，先重新只读挂载把 superblock 暴露出来：
mount -o ro /dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0 /vol1

# 2. 查出并杀掉占用 /vol1 的飞牛后台进程（通常是 owjdxb / ownbsvman）：
fuser -vm /vol1
fuser -km /vol1

# 3. 正常卸载并确认 Open count 变为 0：
umount /vol1
dmsetup info trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0 | grep 'Open count'
```

### 2. 查看最近一次修复日志
```bash
less /root/btrfs_repair_nov.log
```
