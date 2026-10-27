package xyz.xray.vpn

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Disabled compatibility service.
 *
 * No verified native protocol daemon is integrated into this APK. This service
 * intentionally starts no listener and must not be treated as tunnel status.
 */
class TunnelDaemonService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
