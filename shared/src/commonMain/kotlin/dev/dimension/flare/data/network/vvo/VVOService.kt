package dev.dimension.flare.data.network.vvo

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.vvo.api.ConfigApi
import dev.dimension.flare.data.network.vvo.api.StatusApi
import dev.dimension.flare.data.network.vvo.api.TimelineApi
import dev.dimension.flare.data.network.vvo.api.UserApi
import dev.dimension.flare.data.network.vvo.api.createConfigApi
import dev.dimension.flare.data.network.vvo.api.createStatusApi
import dev.dimension.flare.data.network.vvo.api.createTimelineApi
import dev.dimension.flare.data.network.vvo.api.createUserApi
import dev.dimension.flare.data.network.vvo.model.EmojiData
import dev.dimension.flare.data.network.vvo.model.UploadResponse
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvoHost
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.utils.io.core.writeFully
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Duration.Companion.minutes

private val baseUrl = "https://$vvoHost/"

private fun config(
    url: String = baseUrl,
    chocolateFlow: Flow<String>,
    accountKey: MicroBlogKey? = null,
) = ktorfit(url) {
    expectSuccess = false
    install(VVOHeaderPlugin) {
        this.chocolateFlow = chocolateFlow
        this.accountKey = accountKey
    }
}

internal class VVOService(
    private val chocolateFlow: Flow<String>,
    private val accountKey: MicroBlogKey? = null,
) : TimelineApi by config(chocolateFlow = chocolateFlow, accountKey = accountKey).createTimelineApi(),
    UserApi by config(chocolateFlow = chocolateFlow, accountKey = accountKey).createUserApi(),
    ConfigApi by config(chocolateFlow = chocolateFlow, accountKey = accountKey).createConfigApi(),
    StatusApi by config(chocolateFlow = chocolateFlow, accountKey = accountKey).createStatusApi() {
    companion object {
        fun checkChocolates(chocolate: String): Boolean =
            chocolate
                .split(';')
                .mapNotNull {
                    val res = it.split('=')
                    val key = res.getOrNull(0)?.trim()
                    val value = res.getOrNull(1)?.trim()
                    if (key != null && value != null) {
                        key to value
                    } else {
                        null
                    }
                }.toMap()
                .let {
                    it.containsKey("MLOGIN") && it["MLOGIN"] == "1"
                }

        fun requiresSecondaryVerification(url: String?): Boolean =
            url
                ?.let(::containsVerificationHints)
                ?: false

        internal fun containsVerificationHints(url: String): Boolean {
            val normalized = url.lowercase()
            return normalized.contains("/captcha/") ||
                normalized.contains("captcha/show") ||
                normalized.contains("secondverify") ||
                normalized.contains("passport.weibo")
        }
    }

    suspend fun getUid(screenName: String): String? {
        val response =
            ktorClient {
                followRedirects = false
                install(VVOHeaderPlugin) {
                    this.chocolateFlow = this@VVOService.chocolateFlow
                }
            }.get("https://$vvoHost/n/$screenName")
        return response.headers["Location"]?.let {
            return it.split('/').last()
        }
    }

    suspend fun uploadPic(
        st: String,
        filename: String,
        bytes: ByteArray,
        xsrfToken: String = st,
        type: String = "json",
    ): UploadResponse =
        ktorClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 2.minutes.inWholeMilliseconds
                requestTimeoutMillis = 2.minutes.inWholeMilliseconds
                socketTimeoutMillis = 2.minutes.inWholeMilliseconds
            }
            install(VVOHeaderPlugin) {
                this.chocolateFlow = this@VVOService.chocolateFlow
            }
        }.submitFormWithBinaryData(
            url = "https://$vvoHost/api/statuses/uploadPic",
            formData =
                formData {
                    append("type", type)
                    append(
                        "pic",
                        filename,
                        bodyBuilder = {
                            writeFully(bytes)
                        },
                        size = bytes.size.toLong(),
                        contentType = ContentType.Image.JPEG,
                    )

                    append("st", st)
                },
            block = {
                header("X-Xsrf-Token", xsrfToken)
            },
        ).bodyAsText()
            .decodeJson<UploadResponse>()

    suspend fun emojis(): EmojiData = ktorClient().get("https://flareapp.moe/emoji.json").body()
}

private class VVOHeaderConfig {
    var chocolateFlow: Flow<String>? = null
    var accountKey: MicroBlogKey? = null
}

private val VVOHeaderPlugin =
    createClientPlugin("VVOHeaderPlugin", ::VVOHeaderConfig) {
        val chocolateFlow = pluginConfig.chocolateFlow
        val accountKey = pluginConfig.accountKey
        onRequest { request, _ ->
            chocolateFlow?.let { flow ->
                val chocolate = flow.firstOrNull()
                if (chocolate != null) {
                    request.headers.append("Cookie", chocolate)
                }
            }
            request.headers.append("Referer", "https://$vvoHost/")
        }
        onResponse { response ->
            if (response.requiresVerification()) {
                val exception =
                    accountKey?.let {
                        LoginExpiredException(
                            accountKey = it,
                            platformType = PlatformType.VVo,
                        )
                    } ?: VVOVerificationRequiredException(response.call.request.url.toString())
                throw exception
            }
        }
    }

private fun io.ktor.client.statement.HttpResponse.requiresVerification(): Boolean {
    val contentType = contentType()
    return isHtmlResponse(contentType) || containsVerificationHints(call.request.url)
}

private fun isHtmlResponse(contentType: ContentType?): Boolean =
    contentType?.match(ContentType.Text.Html) == true

private fun containsVerificationHints(url: Url): Boolean = VVOService.containsVerificationHints(url.toString())

internal class VVOVerificationRequiredException(
    url: String,
) : Exception("Secondary verification required: $url")
