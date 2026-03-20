package com.ganha.test

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ganha.test.inappmessaging.MyClickListener
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.Firebase
import com.google.firebase.inappmessaging.inAppMessaging
import com.google.firebase.messaging.messaging

class MessageActivity : AppCompatActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notifications permission granted", Toast.LENGTH_SHORT)
                .show()
        } else {
            Toast.makeText(
                this,
                "FCM can't post notifications without POST_NOTIFICATIONS permission",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create channel to show notifications.
            val channelId = getString(R.string.default_notification_channel_id)
            val channelName = "Weather"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }

        // If a notification message is tapped, any data accompanying the notification
        // message is available in the intent extras. In this sample the launcher
        // intent is fired when the notification is tapped, so any accompanying data would
        // be handled here. If you want a different intent fired, set the click_action
        // field of the notification message to the desired intent. The launcher intent
        // is used when no click_action is specified.
        //
        // Handle possible data accompanying notification message.
        // [START handle_data_extras]
        intent.extras?.let {
            for (key in it.keySet()) {
                val value = intent.extras?.getString(key)
                Log.d(TAG, "Key: $key Value: $value")
            }
        }
        // [END handle_data_extras]

        findViewById<Button>(R.id.subscribeButton).setOnClickListener {
            Log.d(TAG, "Subscribing to weather topic")
            // [START subscribe_topics]
            Firebase.messaging.subscribeToTopic("weather")
                .addOnCompleteListener { task ->
                    var msg = "Subscribed to weather topic"
                    if (!task.isSuccessful) {
                        msg = "Failed to subscribe to weather topic"
                    }
                    Log.d(TAG, msg)
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                }
            // [END subscribe_topics]
        }

        findViewById<Button>(R.id.logTokenButton).setOnClickListener {
            // Get token
            // [START log_reg_token]
            Firebase.messaging.token.addOnCompleteListener(
                OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                        return@OnCompleteListener
                    }

                    // Get new FCM registration token
                    val token = task.result

                    // Log and toast
                    val msg = "FCM registration Token: ${token}"
                    Log.d(TAG, msg)
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                },
            )
            // [END log_reg_token]
        }

        Toast.makeText(this, "See README for setup instructions", Toast.LENGTH_SHORT).show()
        askNotificationPermission()


        enableDataCollection()
        addClickListener()
    }

    private fun askNotificationPermission() {
        // This is only necessary for API Level > 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun addClickListener() {
        // [START fiam_add_click_listener]
        val listener = MyClickListener()
        Firebase.inAppMessaging.addClickListener(listener)
        // [END fiam_add_click_listener]
    }

    private fun suppressMessages() {
        // [START fiam_suppress_messages]
        Firebase.inAppMessaging.setMessagesSuppressed(true)
        // [END fiam_suppress_messages]
    }

    private fun enableDataCollection() {
        // [START fiam_enable_data_collection]
        // Only needed if firebase_inapp_messaging_auto_data_collection_enabled is set to
        // false in AndroidManifest.xml
        Firebase.inAppMessaging.isAutomaticDataCollectionEnabled = true
        // [END fiam_enable_data_collection]
    }

    private fun triggerEvent() {
        // [START fiam_trigger_event]
        // somewhere in the app's code
        Firebase.inAppMessaging.triggerEvent("exampleTrigger")
        // [END fiam_trigger_event]
    }

    companion object {

        private const val TAG = "MessageActivity"
    }
}