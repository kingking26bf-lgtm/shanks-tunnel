package com.shanks.tunnel

import android.net.VpnService
import android.content.Intent
import android.os.IBinder
import android.util.Log

class ShanksVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.shanks.tunnel.START"
        const val ACTION_STOP = "com.shanks.tunnel.STOP"
        const val TAG = "SHANKS_TUNNEL"
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "Demande de démarrage reçue")
                // Intégration libbox à l'étape suivante.
            }

            ACTION_STOP -> {
                Log.i(TAG, "Demande d'arrêt reçue")
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Service VPN arrêté")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }
}
