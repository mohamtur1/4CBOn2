// Compile-only stubs (see AndroidCompileStubs.kt header).
package android.net

class Uri private constructor(private val raw: String) {
    override fun toString(): String = raw

    companion object {
        fun parse(uriString: String): Uri = Uri(uriString)
    }
}

class NetworkCapabilities {
    fun hasCapability(capability: Int): Boolean = false

    companion object {
        const val NET_CAPABILITY_INTERNET: Int = 12
    }
}

class ConnectivityManager {
    val activeNetwork: Any? get() = null
    fun getNetworkCapabilities(network: Any?): NetworkCapabilities? = null
}
