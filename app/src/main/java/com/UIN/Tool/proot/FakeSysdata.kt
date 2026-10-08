package com.UIN.Tool.proot

import android.content.Context
import com.UIN.Tool.log.Logger
import java.io.File
import java.io.FileOutputStream

/**
 * Creates fake /proc and /sys entries required by proot on Android.
 *
 * Android restricts or blocks several /proc files; providing static
 * replacements ensures distro tools that read them (top, htop, apt, etc.)
 * work correctly. Modeled after proot-distro's sysdata.py.
 */
object FakeSysdata {

    private const val TAG = "FakeSysdata"

    private const val FAKE_LOADAVG = "0.12 0.07 0.02 2/165 765\n"

    /**
     * D-37：/proc/stat 按**真实核数**生成（原先写死 8 核，
     * 4 核真机上 nproc=8 会让 make -j 等过度并行、观感与调度都不对）。
     */
    private val FAKE_STAT: String by lazy { buildFakeStat() }

    private fun buildFakeStat(): String {
        val cpus = Runtime.getRuntime().availableProcessors().coerceIn(1, 64)
        val total = LongArray(10)
        val perCpu = ArrayList<String>(cpus)
        for (i in 0 until cpus) {
            val user = 30L + (i * 17) % 400
            val nice = 0L
            val system = 10L + (i * 7) % 60
            val idle = 11_000L + (i * 137) % 900
            val ioWait = 10L + i % 5
            val irq = 20L + i % 7
            val softirq = 5L + i % 4
            val steal = 0L
            val guest = 0L
            val guestNice = 0L
            val values = longArrayOf(user, nice, system, idle, ioWait, irq, softirq, steal, guest, guestNice)
            perCpu.add("cpu$i " + values.joinToString(" "))
            for (j in values.indices) total[j] += values[j]
        }
        val aggregate = "cpu  " + total.joinToString(" ")
        return (listOf(aggregate) + perCpu + listOf(TAIL_LINES)).joinToString("\n") + "\n"
    }

    private val TAIL_LINES = """
        |intr 127541 38 290 0 0 0 0 4 0 1 0 0 25329 258 0 5777 277 0 0
        |ctxt 140223
        |btime 1680020856
        |processes 772
        |procs_running 2
        |procs_blocked 0
        |softirq 75663 0 5903 6 25375 10774 0 243 11685 0 21677
    """.trimMargin()

    private const val FAKE_UPTIME = "124.08 932.80\n"

    private const val FAKE_VMSTAT = """nr_free_pages 1743136
nr_zone_inactive_anon 179281
nr_zone_active_anon 7183
nr_zone_inactive_file 22858
nr_zone_active_file 51328
nr_zone_unevictable 642
nr_zone_write_pending 0
nr_mlock 0
nr_bounce 0
nr_zspages 0
nr_free_cma 0
nr_inactive_anon 179281
nr_active_anon 7183
nr_inactive_file 22858
nr_active_file 51328
nr_unevictable 642
nr_slab_reclaimable 8091
nr_slab_unreclaimable 7804
nr_isolated_anon 0
nr_isolated_file 0
workingset_nodes 0
workingset_refault_anon 0
workingset_refault_file 0
workingset_activate_anon 0
workingset_activate_file 0
workingset_restore_anon 0
workingset_restore_file 0
workingset_nodereclaim 0
nr_anon_pages 7723
nr_mapped 8905
nr_file_pages 253569
nr_dirty 0
nr_writeback 0
nr_shmem 178741
nr_file_hugepages 0
nr_anon_transparent_hugepages 1
nr_vmscan_write 0
nr_dirtied 0
nr_written 0
nr_kernel_misc_reclaimable 0
nr_kernel_stack 2780
nr_page_table_pages 344
nr_swapcached 0
pgpgin 890508
pgpgout 0
pswpin 0
pswpout 0
pgalloc_normal 1328079
pgfree 3077011
pgfault 176973
pgmajfault 488
pgreuse 19230
oom_kill 0
"""

    // D-37：内核串与 ProotRuntime.DEFAULT_KERNEL_RELEASE(6.17.0-UIN-Tool) 对齐，
    // 原先 fake version 写 6.2.1 与 --kernel-release 报的 6.17.0 互相打架
    private const val FAKE_VERSION = "Linux version 6.17.0-UIN-Tool (proot@uin-tool) (gcc (Ubuntu 13.2.0-23ubuntu4) 13.2.0, GNU ld (GNU Binutils for Ubuntu) 2.42) #1 SMP PREEMPT_DYNAMIC\n"

    private data class FakeEntry(
        val filename: String,
        val guestPath: String,
        val content: String
    )

    private val FAKE_ENTRIES = listOf(
        FakeEntry("loadavg", "/proc/loadavg", FAKE_LOADAVG),
        FakeEntry("stat", "/proc/stat", FAKE_STAT),
        FakeEntry("uptime", "/proc/uptime", FAKE_UPTIME),
        FakeEntry("version", "/proc/version", FAKE_VERSION),
        FakeEntry("vmstat", "/proc/vmstat", FAKE_VMSTAT),
        FakeEntry("cap_last_cap", "/proc/sys/kernel/cap_last_cap", "40\n"),
        FakeEntry("max_user_watches", "/proc/sys/fs/inotify/max_user_watches", "4096\n"),
        FakeEntry("overflowuid", "/proc/sys/kernel/overflowuid", "65534\n"),
        FakeEntry("overflowgid", "/proc/sys/kernel/overflowgid", "65534\n")
    )

    /**
     * Create fake /proc and /sys entries in sysdata/ directory.
     * Returns the sysdata directory path.
     */
    fun setup(context: Context, containerDir: File): File? {
        val sysdataDir = File(containerDir, "sysdata")
        val sysEmptyDir = File(sysdataDir, "sys_empty")

        try {
            if (!sysdataDir.exists()) {
                sysdataDir.mkdirs()
            }
            if (!sysEmptyDir.exists()) {
                sysEmptyDir.mkdirs()
            }

            for (entry in FAKE_ENTRIES) {
                val file = File(sysdataDir, entry.filename)
                if (!file.exists()) {
                    FileOutputStream(file).use { fos ->
                        fos.write(entry.content.toByteArray())
                    }
                    Logger.d(TAG, "Created fake ${entry.guestPath}")
                }
            }

            Logger.i(TAG, "Fake sysdata setup complete: ${sysdataDir.absolutePath}")
            return sysdataDir
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to setup fake sysdata", e)
            return null
        }
    }

    /**
     * Get bind mount arguments for fake sysdata.
     */
    fun getBindMounts(sysdataDir: File): List<String> {
        val mounts = mutableListOf<String>()

        // SELinux fake
        val sysEmptyDir = File(sysdataDir, "sys_empty")
        if (sysEmptyDir.exists()) {
            mounts.add("${sysEmptyDir.absolutePath}:/sys/fs/selinux")
        }

        // Fake /proc entries
        for (entry in FAKE_ENTRIES) {
            val file = File(sysdataDir, entry.filename)
            if (file.exists()) {
                mounts.add("${file.absolutePath}:${entry.guestPath}")
            }
        }

        return mounts
    }
}
