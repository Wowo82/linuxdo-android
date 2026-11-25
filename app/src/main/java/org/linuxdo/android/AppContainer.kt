package org.linuxdo.android

import android.content.Context
import okhttp3.OkHttpClient
import org.linuxdo.android.data.SessionStore
import org.linuxdo.android.data.UserRepository
import org.linuxdo.android.data.local.DraftStore
import org.linuxdo.android.data.local.SearchHistoryStore
import org.linuxdo.android.data.remote.DiscourseApi
import org.linuxdo.android.data.remote.HttpClients
import java.util.concurrent.TimeUnit

/**
 * 极简依赖容器：一个 API client、一个仓库、两个本地 store。
 * 没有 DI 框架是刻意的——整个 App 就这么几个单例。
 */
class AppContainer(context: Context) {

    val sessionStore: SessionStore = SessionStore(context)

    /** 图片客户端：不挂 cookie、独立连接池 */
    val imageHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val api: DiscourseApi = DiscourseApi(
        baseUrlProvider = { sessionStore.baseUrl },
        clientProvider = { HttpClients.forSite(sessionStore.baseUrl, sessionStore.cookieJar) },
    )

    val repository: UserRepository = UserRepository(api = api, sessionStore = sessionStore)

    val draftStore: DraftStore = DraftStore(context.applicationContext)
    val searchHistoryStore: SearchHistoryStore = SearchHistoryStore(context.applicationContext)
}
