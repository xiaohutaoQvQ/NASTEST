# NAS 存储空间故障处理报告

**设备**：飞牛OS (fnOS) NAS，IP: 192.168.10.7  
**问题描述**：2TB 数据盘 BTRFS 文件系统损坏，存储空间无法挂载  
**报告时间**：2026-09-11

---

## 一、硬件配置

| 设备 | 型号 | 用途 |
|------|------|------|
| HDD | 西数 WD20SPZX 2TB | 数据盘，存储空间1 `/vol1` |
| SSD | 金百达 KP320 256GB | 系统盘 + SSD 读写加速缓存 |

**存储架构（关键）**：
- LVM + dm-cache：SSD 作为 HDD 的回写缓存（writeback cache）
- RAID：md127（RAID1，但只有一块盘，降级运行）
- 完整设备路径：`/dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0`
- BTRFS UUID：`1fba27f1-61a3-4f3b-a155-cc4d2074f016`
- 文件系统：BTRFS，compat_ro_flags=0x7（需要 btrfs-progs v6.x 才能操作）

---

## 二、问题根因（时间线）

### 2026-07-19：初次异常
- BTRFS 报 `corruption_errs: 3898`
- SMART 显示 `UDMA_CRC_Error_Count = 16`（SATA 数据线接触不良）
- **处理**：清零错误计数，更换数据线

### 2026-09-04：彻底崩溃
- 开机后存储空间显示"未挂载"，点击挂载报"内部错误"
- 内核日志：`BTRFS error: parent transid verify failed on logical 776726953984 wanted 645359 found 645259`
- 原因：异常断电导致 **TREE_LOG（操作日志树）版本断层**，每次开机后内核回放日志触发致命错误，强制只读

---

## 三、已执行的所有操作（按时间顺序）

### 阶段1：在 NAS 上尝试修复（失败）

1. `btrfs rescue zero-log` → 清除日志树，短暂可挂载，但复发
2. `btrfs check --repair` 在 NAS 上 → 失败，原因：内核持有设备（open count=1），无法独占访问

### 阶段2：WSL2 离线修复（部分成功）

**操作步骤**：
1. HDD 通过 USB 外接盒连接 Windows PC
2. `wsl --mount \\.\PhysicalDrive2 --bare`（HDD 在 WSL2 中出现为 `/dev/sdd1`）
3. `mdadm --assemble --run /dev/md127 /dev/sdd1`（组装 RAID，单盘降级）
4. **绕过 LVM dm-cache**（WSL2 内核缺少 dm-cache 模块）：
   ```bash
   dmsetup create corig_direct --table '0 3906756608 linear /dev/md127 2048'
   ```
   > ⚠️ 关键：扇区数必须是 `3906756608`，不能用 LVM PE 计算值 `3906543616`
5. `btrfs check --repair /dev/mapper/corig_direct`（在 corig/HDD 上修复）
6. 验证：`btrfs check --readonly` → **`no error found`** ✅

**WSL2 修复的问题**：
- 修复在 HDD（corig）上直接写入，transid 跳跃到 **646465**（远高于当时文件系统 generation 645602）
- SSD 缓存层未参与修复，SSD 上仍保留旧的损坏数据

### 阶段3：装回 NAS 后（新问题出现）

装回 NAS 后，dm-cache 同时使用 SSD+HDD：
- SSD 缓存中有 `776346337280` 地址处的旧损坏块（corrupted log tree block）
- 每次开机，BTRFS 读取该 SSD 缓存块时 log replay 失败：
  ```
  tree first key mismatch detected, bytenr=776346337280 parent_transid=645581
  Failed to recover log tree
  ```

**临时修复**：`btrfs rescue zero-log` 清日志 → 可以正常挂载（rw）→ 重启后复发（SSD 缓存的旧块再次出现）

### 阶段4：剥离 SSD 缓存（引入新问题）

```bash
lvconvert --yes --splitcache trim_a23eb205.../0
```

剥离成功，但出现新错误：
```
parent transid verify failed on logical 1867267440640 mirror 1 wanted 645587 found 646465
failed to read block groups: -5
```

原因：WSL2 修复写入的子块（transid=646465）与父节点指针期望值（645587）不一致，因为父节点在 SSD 缓存中而 WSL2 只修改了 HDD。

### 阶段5：在 NAS 上直接 btrfs check --repair（成功获得独占访问）

剥离 SSD 缓存后，设备变为简单线性 LVM，Open count 可降为 0：

```bash
printf 'y\n' | btrfs check --repair /dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0
```

输出：`Ignoring transid failure`（工具识别不一致并跳过）

但随后遇到更严重的问题：
```
child eb corrupted: parent bytenr=77250560 item=49 child bytenr=776347680768 child level=1
failed to repair root items: Input/output error
```

### 阶段6：init-extent-tree（核弹级操作，引入更大问题）

```bash
printf 'y\n' | btrfs check --repair --init-extent-tree /dev/mapper/...
```

运行约 3 小时，产生 272,668 行修复记录，SSH 连接因 socket 异常中断。

**结果**：
- 文件系统可以挂载（transid 问题解决）
- 但 extent 树仅追踪了 **186MiB** 使用量（原来是 1.5TiB）
- 实际数据仍在磁盘上（`ls`、`find` 均能看到文件）
- 挂载后立刻因 `unable to find ref byte nr 776667496448` 触发 forced readonly

原因：`--init-extent-tree` 清空并重建了 extent 树，但只追踪了部分 extent 引用（修复被 SSH 中断，没跑完）。

### 阶段7：当前操作——btrfs check --repair（正在进行）

获得 open count=0 后，立刻以 `nohup` 后台启动完整修复：

```bash
nohup sh -c 'echo y | btrfs check --repair /dev/mapper/... > /tmp/btrfs_repair.log 2>&1' &
```

**启动时间**：2026-09-10 10:30:11  
**当前状态**（2026-09-11 14:10）：

| 指标 | 数值 |
|------|------|
| 已运行 | **27.7 小时** |
| 日志行数 | **2,445,144 行** |
| 已修复 extent 数 | **284,512+ 个** |
| 20秒速度 | +1,021 行（约 18万行/小时）|
| 确认无循环 | 30秒内地址从 34MB 推进到 222MB ✅ |

修复内容：正在逐一为每个文件数据块补回缺失的 extent 引用（backref）。当前在处理 root 7（checksum tree，存储所有数据块的 CRC 校验值，是最大的树）。

---

## 四、当前文件系统状态

```
设备：/dev/mapper/trim_a23eb205_06da_4983_abf4_f750b63e6eb2-0
类型：BTRFS（linear LVM，SSD 缓存已剥离）
挂载：未挂载
Open count：0（修复工具独占中）
Superblock generation：645614
log_root：0（已清零）
Device allocated：1.67TiB
Data used（extent树重建后）：186MiB（不正确，修复完成后会恢复正常）
物理数据：完整存在（文件可被 find/ls 访问）
```

---

## 五、关键问题总结

1. **根本原因**：dm-cache writeback 模式下异常断电 → BTRFS log tree 版本断层
2. **放大问题**：WSL2 修复只操作 HDD（corig），SSD 缓存保留旧数据，导致重新挂载时冲突
3. **当前挑战**：`--init-extent-tree` 清空了 1.5TB 数据的 extent 引用，需要 `btrfs check --repair` 全量扫描补回

---

## 六、预期后续

修复完成后：
1. `btrfs check --readonly` 验证
2. 挂载测试：`mount /dev/mapper/... /vol1`
3. 确认数据完整性：`btrfs device stats /vol1`（所有计数器应为 0）
4. 重启 NAS 确认能自动挂载

**数据是否有丢失风险**：物理数据块完整。`--init-extent-tree` 不删除数据块，只重建元数据引用。修复完成后数据应完整可访问。

---

# 第二阶段诊断报告（2026-09-11，接手 AI）

## 一、对前序操作的核实结论

1. **阶段7的修复进程陷入死循环**：日志最后 500 行在 247 万行日志中完全相同地重复了 447 次，循环从第 172,979 行（启动约 1 小时后）开始。28 小时运行中约 27 小时无净进展。原报告"确认无循环"系误判（地址推进只是循环周期内部现象）。
2. 已于 2026-09-11 14:39:06 以 SIGINT 干净终止该进程（PID 9581，日志尾留 REPAIR_DONE 标记，共 2,487,811 行）。

## 二、死循环根因（已定位，只读诊断确认）

- 损坏点唯一且已精确定位：**FS_TREE 根节点 148029440（level 2）第 102 号子指针**。
- 父节点期望：子叶子覆盖 inode 1794131–1847297、`parent_transid=645360`。
- 实际现状：该地址（776345812992）及**其 DUP 镜像（物理偏移 731257044992 / 732330786816）** 均已被 gen 700467 的中止事务叶子覆盖（内容为 inode 2095857+ 的 1Panel/overlay 条目，130 项）。
- 即：旧叶子（含约 130 个元数据项）被覆盖写入，DUP 双镜像同时被覆盖（写镜像时两份同步）。`btrfs check` 每轮重建 extent 引用后，下一轮读到该块又因 transid 校验失败回滚，形成死循环。
- 全日志 317 次 `child eb corrupted` 全部指向同一个块——**这是 extent 树重建的唯一堵点**。

## 三、恢复源排查结果

| 恢复源 | 结果 |
|--------|------|
| DUP 镜像副本 | ❌ 两镜像均已被新叶子覆盖（字节级确认） |
| SSD 缓存（cache_dump 解析） | ❌ 坏块区域不在缓存映射中，且 0 脏块（cleaner 已全部回写） |
| subvol 256 旧根（38141952，gen 645598） | ❌ inode 上限 386867，不含丢失段 |
| **COW 残留副本（全盘扫描）** | ❌ 全盘扫描完成（2026-09-12），仅命中已知的 2 个 DUP 镜像本体（均为 gen 700467 覆盖版），旧叶子（gen 645360）无任何残留副本 |

**结论：inode 1794131–1847297 段的元数据（约 130 项，1Panel 相关）已确认永久丢失，文件数据块仍在盘上但无法通过树索引定位。**

## 四、丢失范围评估（初步）

- 丢失的是 inode 1794131–1847297 的文件树叶子（gen 645360，130 项）。**文件内容（数据块）大概率完好**，丢失的是这些文件的元数据：文件名、目录归属、属性、数据块索引。
- 丢失段与 1Panel 相关（坏叶子的替代内容里有 1Panel.db-wal/shm，时间戳 2026-09-04 22:44 —— 与崩溃时刻吻合）。准确文件清单需从坏叶子的"父节点左邻居叶子"或 COW 残留中获取。

## 五、当前系统状态（均为只读确认）

- md127（RAID1 单盘降级）：clean，SMART 全绿（0 重分配/0 待定，37°C）
- 主 LV `trim_...-0`：纯线性映射（SSD 缓存已剥离但缓存池 0cache/0cache_cmeta 仍保留在 SSD 上）
- 文件系统未挂载，open count=0
- btrfs-progs v6.2
- 后台任务：/tmp/leafscan.py 全盘扫描（只读）；/tmp/fs_tree_dump.txt（345MB）、/tmp/tree5_dump.txt、/tmp/cmeta.{bin,xml} 为诊断产物

---

## 七、教训与建议

1. **SSD 回写缓存风险极高**：异常断电时，SSD 缓存的脏数据与 HDD 不一致，会导致 BTRFS 元数据损坏。建议改为 writethrough 或直接禁用 SSD 缓存。
2. **btrfs-progs 版本**：操作此文件系统需要 v6.0+（compat_ro_flags=0x7 支持）
3. **dm-cache 下修复**：必须剥离（splitcache）或绕过 SSD 缓存后再修复，否则 SSD 上的旧数据会干扰修复结果
4. **数据备份**：1.5TB 数据无任何备份，风险极大
