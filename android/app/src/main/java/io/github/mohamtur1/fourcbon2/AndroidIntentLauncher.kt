package io.github.mohamtur1.fourcbon2

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract

/**
 * Division 21 (Android binding): the only class in the intent path that touches `android.*`.
 *
 * Split out of `ActionTimelineAndIntents.kt` in v0.3 (Division 43) so the timeline model, the
 * intent specifications, and [HarmlessAppIntentExecutor] — the parts that enforce scope, takeover,
 * and critical-action policy — stay pure Kotlin and remain executable in the sandbox harness.
 */
class AndroidPlatformIntentLauncher(private val context: Context) : PlatformIntentLauncher {
    override fun launchPreparedIntent(spec: HarmlessIntentSpec): Boolean {
        val intent = when (spec) {
            is HarmlessIntentSpec.SharePlainTextDraft -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                `package` = spec.targetPackage
                putExtra(Intent.EXTRA_SUBJECT, spec.subject)
                putExtra(Intent.EXTRA_TEXT, spec.draftBody)
            }
            is HarmlessIntentSpec.PrepareCalendarDraft -> Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                `package` = spec.targetPackage
                putExtra(CalendarContract.Events.TITLE, spec.title)
                putExtra(CalendarContract.Events.DESCRIPTION, spec.description)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, spec.beginTimeEpochMs)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, spec.endTimeEpochMs)
            }
            is HarmlessIntentSpec.OpenHttpsDestination -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse(spec.httpsUrl),
            ).apply {
                `package` = spec.targetPackage
            }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
