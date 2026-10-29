package cat.kardinebi.patcher

import android.app.Application
import cat.kardinebi.patcher.work.News
import cat.kardinebi.patcher.work.Notify

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val words = Strings.of(this)
        Notify.channels(this, words.workChannel, words.newsChannel)
        News.schedule(this)
    }
}
