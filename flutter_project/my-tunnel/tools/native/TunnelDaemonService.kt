package xyz.xray.vpn

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Dedicated isolated sub-process service (:tunnel).
 * Runs Go/C native background protocol daemons (DNSTT, SlowDNS, Psiphon, OpenConnect)
 * in an isolated process to prevent Go runtime signal and memory collisions with
 * the main Flutter UI process or other VPN engines.
 */
class TunnelDaemonService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }
}
