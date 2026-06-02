package com.example.projectwatchapp

import android.app.Application
import android.util.Log
import com.google.firebase.database.FirebaseDatabase

/**
 * Points Firebase at your Realtime Database URL (required for europe-west1 and other regional DBs).
 * You must still place the real [google-services.json] from Firebase Console in the app module.
 */
class ProjectWatchApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val databaseUrl = getString(R.string.firebase_realtime_database_url)
        FirebaseDatabase.getInstance(databaseUrl)

        val projectId = runCatching {
            getString(R.string.project_id)
        }.getOrNull()
        if (projectId == null || projectId == "YOUR_PROJECT_ID") {
            Log.e(
                TAG,
                "google-services.json is still the placeholder. Download the real file from " +
                    "Firebase Console → Project settings → Your apps → google-services.json"
            )
        } else {
            Log.d(TAG, "Firebase project: $projectId, database: $databaseUrl")
        }
    }

    companion object {
        private const val TAG = "ProjectWatchFirebase"
    }
}
