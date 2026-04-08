package dev.dimension.flare.ui.screen.webview

import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import dev.dimension.flare.data.network.vvo.vvoRequiresSecondaryVerification
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.vvo.VVOSecondaryVerificationPresenter
import dev.dimension.flare.ui.route.Route
import kotlinx.coroutines.delay
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VVOSecondaryVerificationScreen(
  args: Route.VVOSecondaryVerification,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by producePresenter(key = args.url) { presenter(args) }
  val webViewState = rememberWebViewState(state.url)

  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  FlareScaffold(
    modifier =
      modifier
        .nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      FlareTopAppBar(
        title = {
        },
        navigationIcon = {
          BackButton(onBack = onBack)
        },
        actions = {
          androidx.compose.material3.TextButton(
            onClick = {
              val url = webViewState.lastLoadedUrl ?: state.url
              val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
              if (cookie.isNotEmpty()) {
                state.saveCookie(cookie)
              }
              onBack()
            },
          ) {
            androidx.compose.material3.Text(text = "完成")
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
  ) { contentPadding ->
    WebView(
      webViewState,
      layoutParams =
        FrameLayout.LayoutParams(
          LayoutParams.MATCH_PARENT,
          LayoutParams.MATCH_PARENT,
        ),
      modifier =
        Modifier
          .padding(contentPadding)
          .fillMaxSize(),
      onCreated = { webView ->
        with(webView.settings) {
          javaScriptEnabled = true
        }
        // 将当前账号 cookie 注入到 WebView，保证验证发生在同一会话里
        val cookieManager = CookieManager.getInstance()
        state.initialCookie
          ?.split(';')
          ?.map { it.trim() }
          ?.filter { it.isNotEmpty() }
          ?.forEach { pair ->
            cookieManager.setCookie(state.url, pair)
          }
        cookieManager.flush()
      },
    )
  }

  LaunchedEffect(state.url) {
    // 通过轮询 url + cookie 判断是否已经完成验证
    // 一旦跳出验证码/二次验证页面，就回写 cookie 并自动返回
    while (true) {
      delay(1.seconds)
      val currentUrl = webViewState.lastLoadedUrl ?: continue
      val cookie = CookieManager.getInstance().getCookie(currentUrl).orEmpty()
      if (cookie.isEmpty()) continue

      val stillRequiresVerification = vvoRequiresSecondaryVerification(currentUrl)
      if (!stillRequiresVerification) {
        state.saveCookie(cookie)
        onBack()
        break
      }
    }
  }
}

@Composable
private fun presenter(args: Route.VVOSecondaryVerification) =
  run {
    remember(args) {
      VVOSecondaryVerificationPresenter(
        accountKey = args.accountKey,
        url = args.url,
      )
    }.invoke()
  }
