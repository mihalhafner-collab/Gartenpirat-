package at.hausgarten.service

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context

/** Separate explicit read-only mail consent, independent from backup/tunnel consent. */
internal class MailAccount private constructor() {
    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun token(context: Context, account: String, consent: Activity?): String {
            if (account.isEmpty())
                throw PixelMcpWriteTools.Rejected(
                    "MAIL_AUTH_REQUIRED",
                    "Für Gmail-Versandbelege ein Google-Konto wählen und den getrennten Lesezugriff freigeben.",
                )
            val manager = AccountManager.get(context)
            val selected = Account(account, "com.google")
            val scope = "oauth2:https://www.googleapis.com/auth/gmail.readonly"
            val bundle =
                if (consent == null)
                    manager.getAuthToken(selected, scope, null, false, null, null).result
                else manager.getAuthToken(selected, scope, null, consent, null, null).result
            val token = bundle.getString(AccountManager.KEY_AUTHTOKEN, "")
            if (token.isEmpty())
                throw PixelMcpWriteTools.Rejected(
                    "MAIL_AUTH_REQUIRED",
                    "Gmail-Versandbelege am Gerät freigeben.",
                )
            return token
        }

        @JvmStatic
        fun client(context: Context): OfferDeliveryClient {
            val account =
                context.getSharedPreferences("haus_garten_mail", 0).getString("account", "") ?: ""
            return OfferDeliveryClient({ token(context, account, null) }, PlanningWire())
        }
    }
}
