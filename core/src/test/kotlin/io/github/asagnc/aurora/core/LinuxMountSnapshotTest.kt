package io.github.asagnc.aurora.core

import kotlin.test.*

class LinuxMountSnapshotTest {
    @Test fun allKernelPathEscapesAreDecodedExactlyOnce() {
        val input = "36 35 98:0 / /owned/a\\040b\\011c\\012d\\134040 rw shared:1 future:ok - ext4 /dev/root rw\n"
        assertEquals(listOf("/owned/a b\tc\nd\\040"), linuxMountPoints(input))
    }
    @Test fun optionalFieldsCanBeAbsentOrUnknown() {
        assertEquals(listOf("/mnt"), linuxMountPoints("36 35 98:0 / /mnt rw - ext4 /dev/root rw\n"))
        assertEquals(listOf("/mnt"), linuxMountPoints("36 35 98:0 / /mnt rw futureflag unknown:2 - ext4 /dev/root rw\n"))
    }
    @Test fun malformedOrUnboundedSnapshotCannotTurnIntoNoMounts() {
        for (input in listOf("", " ", "36 35 98:0 / /mnt rw ext4 /dev/root rw\n", "36 35 98:0 / /mnt\\077 rw - ext4 /dev/root rw\n",
            "36 35 98:0 / relative rw - ext4 /dev/root rw\n", "36 35 98:0 / /mnt\\ rw - ext4 /dev/root rw\n", "x".repeat(4 * 1024 * 1024 + 1)))
            assertFails { linuxMountPoints(input) }
    }
}
