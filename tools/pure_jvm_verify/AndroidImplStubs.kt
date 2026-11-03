// Compile-only stubs (see AndroidCompileStubs.kt header).
package io.github.mohamtur1.fourcbon2

import android.content.Context

class AndroidSqliteBackend(context: Context) : LocalStoreBackend {
    override fun upsert(namespace: String, id: String, payloadJson: String, updatedAtEpochMs: Long) {
        stub<Unit>()
    }
    override fun get(namespace: String, id: String): StoredEntityRow? = stub()
    override fun listByNamespace(namespace: String): List<StoredEntityRow> = stub()
    override fun delete(namespace: String, id: String): Boolean = stub()
    override fun clearNamespace(namespace: String) {
        stub<Unit>()
    }
}

class AndroidKeyStoreCredentialStore(context: Context) : CredentialStore {
    override fun save(slot: CredentialSlot, secret: CharArray) {
        stub<Unit>()
    }
    override fun load(slot: CredentialSlot): CharArray? = stub()
    override fun remove(slot: CredentialSlot) {
        stub<Unit>()
    }
    override fun contains(slot: CredentialSlot): Boolean = stub()
}

class AndroidPlatformIntentLauncher(context: Context) : PlatformIntentLauncher {
    override fun launchPreparedIntent(spec: HarmlessIntentSpec): Boolean = stub()
}

class AndroidCustomTabPlatformDriver(context: Context) : CustomTabPlatformDriver {
    override fun openHttpsCustomTab(url: String, preferChromePackage: Boolean): BrowserOpenMode = stub()
}

private fun <T> stub(): T = throw UnsupportedOperationException(
    "Android platform stub: unreachable from pure-JVM tests.",
)
