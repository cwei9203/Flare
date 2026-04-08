package dev.dimension.flare.ui.screen.webview

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.ui.route.Route

internal fun EntryProviderScope<NavKey>.webViewEntryBuilder(
  onBack: () -> Unit,
) {
  entry<Route.VVOSecondaryVerification> { args ->
    VVOSecondaryVerificationScreen(
      args = args,
      onBack = onBack,
    )
  }
}

