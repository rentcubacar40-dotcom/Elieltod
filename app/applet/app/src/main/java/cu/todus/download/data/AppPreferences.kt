package cu.todus.download.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("todus_download_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_NATIONAL_IP = "152.207.206.86"
        const val DEFAULT_SNI_HOST = "s3.todus.cu"
        const val DEFAULT_THREADS = 4
        const val DEFAULT_USER_AGENT = "toDus Download"
        const val DEFAULT_FOLDER = "toDus Download"
    }

    var nationalIp: String
        get() = prefs.getString("national_ip", DEFAULT_NATIONAL_IP) ?: DEFAULT_NATIONAL_IP
        set(value) = prefs.edit().putString("national_ip", value.trim()).apply()

    var sniHost: String
        get() = prefs.getString("sni_host", DEFAULT_SNI_HOST) ?: DEFAULT_SNI_HOST
        set(value) = prefs.edit().putString("sni_host", value.trim()).apply()

    var defaultThreads: Int
        get() = prefs.getInt("default_threads", DEFAULT_THREADS)
        set(value) = prefs.edit().putInt("default_threads", value.coerceIn(1, 16)).apply()

    var userAgent: String
        get() = prefs.getString("user_agent", DEFAULT_USER_AGENT) ?: DEFAULT_USER_AGENT
        set(value) = prefs.edit().putString("user_agent", value).apply()

    var subFolder: String
        get() = prefs.getString("sub_folder", DEFAULT_FOLDER) ?: DEFAULT_FOLDER
        set(value) = prefs.edit().putString("sub_folder", value).apply()

    var autoResume: Boolean
        get() = prefs.getBoolean("auto_resume", true)
        set(value) = prefs.edit().putBoolean("auto_resume", value).apply()

    var showNotifications: Boolean
        get() = prefs.getBoolean("show_notifications", true)
        set(value) = prefs.edit().putBoolean("show_notifications", value).apply()
}
