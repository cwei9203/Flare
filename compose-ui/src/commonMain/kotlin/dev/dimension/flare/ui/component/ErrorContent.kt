package dev.dimension.flare.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleExclamation
import compose.icons.fontawesomeicons.solid.FileCircleExclamation
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.login_expired
import dev.dimension.flare.compose.ui.login_expired_message
import dev.dimension.flare.compose.ui.permission_denied_message
import dev.dimension.flare.compose.ui.permission_denied_title
import dev.dimension.flare.compose.ui.status_loadmore_error
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.data.repository.RequireReLoginException
import dev.dimension.flare.data.network.vvo.VVOVerificationRequiredException
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.DeeplinkRoute.VVOSecondaryVerification
import dev.dimension.flare.ui.route.toUri
import dev.dimension.flare.ui.presenter.vvo.VVOSecondaryVerificationEvents
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@HiddenFromObjC
@Composable
public fun ErrorContent(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (error) {
        is LoginExpiredException -> {
            LoginExpiredError(error, modifier)
        }

        is RequireReLoginException -> {
            RequireReLoginError(error, modifier)
        }
        is VVOVerificationRequiredException -> {
            VvoVerificationRequiredError(
                error = error,
                onRetry = onRetry,
                modifier = modifier,
            )
        }
        else -> {
            CommonError(
                error = error,
                onRetry = onRetry,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun CommonError(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clickable {
                    onRetry.invoke()
                }.fillMaxWidth()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.FileCircleExclamation,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        PlatformText(text = stringResource(Res.string.status_loadmore_error))
        error.message?.let { PlatformText(text = it) }
    }
}

private object VerificationUrlAutoOpener {
    private var lastOpenedAtMs: Long = 0L
    private var lastUrl: String? = null

    fun shouldOpen(url: String): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        val cooldownMs = 10.seconds.inWholeMilliseconds
        val sameUrl = lastUrl == url
        val withinCooldown = (now - lastOpenedAtMs) < cooldownMs
        if (sameUrl && withinCooldown) return false
        lastOpenedAtMs = now
        lastUrl = url
        return true
    }
}

@Composable
private fun VvoVerificationRequiredError(
    error: VVOVerificationRequiredException,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val inAppUri =
        VVOSecondaryVerification(
            accountKey = error.accountKey,
            url = error.url,
        ).toUri()

    // 验证成功后自动重试一次（去重+冷却，避免循环重试）
    androidx.compose.runtime.LaunchedEffect(error.url, error.accountKey) {
        val startedAtMs = Clock.System.now().toEpochMilliseconds()
        VVOSecondaryVerificationEvents.successEvents.collect { event ->
            if (event.accountKey != error.accountKey) return@collect
            if (event.atMs < startedAtMs) return@collect
            // 等 credential 写库 + Flow 分发 + 下一次请求真正取到新 cookie
            delay(900)
            onRetry.invoke()
            return@collect
        }
    }

    androidx.compose.runtime.LaunchedEffect(error.url) {
        if (VerificationUrlAutoOpener.shouldOpen(error.url)) {
            // 给 UI 一个很短的缓冲，避免某些平台上首次渲染期间打开链接失败
            delay(150)
            uriHandler.openUri(inAppUri)
        }
    }

    Column(
        modifier =
            modifier
                .clickable {
                    uriHandler.openUri(inAppUri)
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.CircleExclamation,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        PlatformText(text = "需要完成微博安全验证")
        PlatformText(text = "已在 App 内打开验证页，完成后请返回重试")
        PlatformText(text = "如果未能打开，可点此改用系统浏览器")
        PlatformText(
            text = error.url,
            modifier =
                Modifier.clickable {
                    uriHandler.openUri(error.url)
                },
        )
    }
}

@Composable
private fun RequireReLoginError(
    error: RequireReLoginException,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .clickable {
                    uriHandler.openUri(DeeplinkRoute.Login.toUri())
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.CircleExclamation,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        PlatformText(
            text = stringResource(resource = Res.string.permission_denied_title),
        )
        PlatformText(
            text = stringResource(resource = Res.string.permission_denied_message),
        )
    }
}

@Composable
private fun LoginExpiredError(
    error: LoginExpiredException,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .clickable {
                    uriHandler.openUri(DeeplinkRoute.Login.toUri())
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.CircleExclamation,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        PlatformText(
            text = stringResource(resource = Res.string.login_expired),
        )
        PlatformText(
            text = stringResource(resource = Res.string.login_expired_message),
        )
        PlatformText(
            text = error.accountKey.toString(),
        )
    }
}
