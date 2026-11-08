package at.hausgarten.service

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** Explicit consent continuation; background use never launches a Google UI. */
internal object CalendarAuthorization {
    class ConsentRequired(@JvmField val resolution: PendingIntent) :
        Exception(CalendarAccessRules.message("CALENDAR_AUTH_REQUIRED"))

    @JvmStatic
    @Throws(HttpTransport.Failure::class)
    fun request(account: String?, busy: Boolean): AuthorizationRequest {
        if (account.isNullOrBlank()) throw failure("CALENDAR_ACCOUNT_ACCESS_REQUIRED")
        return AuthorizationRequest.builder()
            .setAccount(Account(account, "com.google"))
            .setRequestedScopes(CalendarAccessRules.requestedScopes(busy).map { Scope(it) })
            .build()
    }

    @JvmStatic
    @Throws(Exception::class)
    fun token(context: Context, account: String, busy: Boolean): String {
        try {
            if (
                GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context) != 0
            )
                throw failure("CALENDAR_GOOGLE_SERVICES_REQUIRED")
            val result =
                Tasks.await(
                    Identity.getAuthorizationClient(context).authorize(request(account, busy)),
                    45,
                    TimeUnit.SECONDS,
                )
            if (result.hasResolution())
                throw ConsentRequired(
                    result.pendingIntent ?: throw failure("CALENDAR_GOOGLE_AUTH_ERROR")
                )
            return accepted(busy, result)
        } catch (required: ConsentRequired) {
            throw required
        } catch (error: Exception) {
            throw failure(error)
        }
    }

    @JvmStatic
    @Throws(HttpTransport.Failure::class)
    fun fromIntent(context: Context, data: Intent?, busy: Boolean): String =
        try {
            if (data == null) throw failure("CALENDAR_AUTH_RESULT_MISSING")
            accepted(
                busy,
                Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data),
            )
        } catch (error: Exception) {
            throw failure(error)
        }

    @JvmStatic
    @Throws(HttpTransport.Failure::class)
    fun fromActivityResult(context: Context, result: Int, data: Intent?, busy: Boolean): String {
        // A non-OK Google result can contain a precise ApiException. Decode it first.
        if (data == null) throw failure(CalendarAccessRules.missingResultCode(result))
        return fromIntent(context, data, busy)
    }

    @Suppress("DEPRECATION") // GET_SIGNATURES and signatures are the API 26-27 path.
    @JvmStatic
    fun signingSha1(context: Context): String =
        try {
            val flag =
                if (android.os.Build.VERSION.SDK_INT >= 28)
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                else android.content.pm.PackageManager.GET_SIGNATURES
            val info = context.packageManager.getPackageInfo(context.packageName, flag)
            val signers =
                if (android.os.Build.VERSION.SDK_INT >= 28) info.signingInfo!!.apkContentsSigners
                else info.signatures
            if (signers == null || signers.size != 1) "nicht eindeutig"
            else
                java.security.MessageDigest.getInstance("SHA-1")
                    .digest(signers[0].toByteArray())
                    .joinToString(":") {
                        String.format(java.util.Locale.ROOT, "%02X", it.toInt() and 255)
                    }
        } catch (unavailable: Exception) {
            "nicht lesbar"
        }

    @Throws(HttpTransport.Failure::class)
    private fun accepted(busy: Boolean, result: AuthorizationResult): String {
        val code = CalendarAccessRules.grantCode(busy, result.accessToken, result.grantedScopes)
        if (code.isNotEmpty()) throw failure(code)
        return result.accessToken!!
    }

    @JvmStatic
    @Throws(HttpTransport.Failure::class)
    fun invalidate(context: Context, rejected: String) {
        try {
            Tasks.await(
                Identity.getAuthorizationClient(context)
                    .clearToken(ClearTokenRequest.builder().setToken(rejected).build()),
                45,
                TimeUnit.SECONDS,
            )
        } catch (error: Exception) {
            throw failure(error)
        }
    }

    @JvmStatic
    fun failure(code: String): HttpTransport.Failure {
        val retry = code == "CALENDAR_TOKEN_NETWORK" || code == "CALENDAR_INTERRUPTED"
        return HttpTransport.Failure(
            code,
            CalendarAccessRules.message(code),
            if (retry) 30000 else 0,
            !retry,
        )
    }

    @JvmStatic
    fun failure(error: Exception): HttpTransport.Failure {
        var cause: Throwable = error
        var i = 0
        while (i++ < 4 && cause is ExecutionException && cause.cause != null) cause = cause.cause!!
        if (cause is HttpTransport.Failure) return cause
        if (cause is ApiException) {
            val known = CalendarAccessRules.authenticationCode(cause)
            return failure(
                if (
                    known == "CALENDAR_APP_REGISTRATION_REQUIRED" ||
                        known == "CALENDAR_SCOPE_UNSUPPORTED"
                )
                    known
                else CalendarAccessRules.googleStatus(cause.statusCode)
            )
        }
        if (cause is java.util.concurrent.TimeoutException) return failure("CALENDAR_TOKEN_NETWORK")
        if (CalendarRecoveryRules.interrupted(cause)) {
            Thread.currentThread().interrupt()
            return failure("CALENDAR_INTERRUPTED")
        }
        return failure(
            CalendarAccessRules.authenticationCode(if (cause is Exception) cause else error)
        )
    }
}
