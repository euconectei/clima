package app.clima

import android.app.Application
import app.clima.widget.RefreshWorker

class ClimaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshWorker.schedule(this)
    }
}
