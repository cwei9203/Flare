package dev.dimension.flare.ui.presenter.vvo

import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

public data class VVOSecondaryVerificationSuccessEvent(
  val accountKey: MicroBlogKey?,
  val url: String,
  val cookie: String,
  val atMs: Long,
)

public object VVOSecondaryVerificationEvents {
  private val _successEvents =
    MutableSharedFlow<VVOSecondaryVerificationSuccessEvent>(
      extraBufferCapacity = 8,
    )

  public val successEvents: SharedFlow<VVOSecondaryVerificationSuccessEvent> = _successEvents

  public fun emitSuccess(event: VVOSecondaryVerificationSuccessEvent) {
    _successEvents.tryEmit(event)
  }
}

