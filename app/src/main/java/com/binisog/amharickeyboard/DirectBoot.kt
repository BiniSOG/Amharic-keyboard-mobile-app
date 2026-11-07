package com.binisog.amharickeyboard

import android.content.Context
import android.os.UserManager

/**
 * Direct Boot support. The keyboard is `directBootAware`, so after a restart it can run on
 * the lock screen before the phone is first unlocked. Until then only *device-protected*
 * storage is readable: settings and the dictionary cache live there, while learned words
 * (private typing data) stay in normal credential-protected storage and are loaded after
 * unlock.
 */
object DirectBoot {
    fun isUnlocked(ctx: Context): Boolean =
        ctx.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true

    /** Context whose files/preferences are readable before the first unlock. */
    fun deviceProtected(ctx: Context): Context =
        if (ctx.isDeviceProtectedStorage) ctx else ctx.createDeviceProtectedStorageContext()
}
