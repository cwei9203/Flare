package dev.dimension.flare.ui.presenter.vvo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class VVOSecondaryVerificationPresenter(
  private val accountKey: MicroBlogKey?,
  private val url: String,
) : PresenterBase<VVOSecondaryVerificationState>(),
  KoinComponent {
  private val accountRepository: AccountRepository by inject()

  private val cookieFlow: Flow<String?> by lazy {
    accountKey?.let { key ->
      accountRepository
        .credentialFlow<UiAccount.VVo.Credential>(key)
        .map { it.chocolate }
    } ?: emptyFlow()
  }

  @Composable
  override fun body(): VVOSecondaryVerificationState {
    val cookie by cookieFlow.collectAsState(initial = null)
    return remember(cookie) {
      object : VVOSecondaryVerificationState {
        override val url: String = this@VVOSecondaryVerificationPresenter.url
        override val accountKey: MicroBlogKey? = this@VVOSecondaryVerificationPresenter.accountKey
        override val initialCookie: String? = cookie

        override fun saveCookie(cookie: String) {
          val key = accountKey ?: return
          val job = accountRepository.updateCredential(
            accountKey = key,
            credential = UiAccount.VVo.Credential(chocolate = cookie),
          )
          job.invokeOnCompletion {
            VVOSecondaryVerificationEvents.emitSuccess(
              VVOSecondaryVerificationSuccessEvent(
                accountKey = key,
                url = url,
                cookie = cookie,
                atMs = Clock.System.now().toEpochMilliseconds(),
              ),
            )
          }
        }
      }
    }
  }
}

@Immutable
public interface VVOSecondaryVerificationState {
  public val url: String
  public val accountKey: MicroBlogKey?
  public val initialCookie: String?

  public fun saveCookie(cookie: String)
}

