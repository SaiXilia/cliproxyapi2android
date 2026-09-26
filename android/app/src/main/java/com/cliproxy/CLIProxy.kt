package com.cliproxy

object CLIProxy {
    const val STATUS_STOPPED = 0
    const val STATUS_STARTING = 1
    const val STATUS_RUNNING = 2
    const val STATUS_STOPPING = 3
    const val STATUS_FAILED = 4

    init {
        System.loadLibrary("cliproxy")
    }

    @JvmStatic
    private external fun nativeStartServer(configDir: String, host: String?, port: Int): Int

    @JvmStatic
    private external fun nativeStopServer()

    @JvmStatic
    private external fun nativeGetServerStatus(): Int

    @JvmStatic
    private external fun nativePollOAuthURL(timeoutMs: Int): String?

    fun startServer(configDir: String, host: String? = "127.0.0.1", port: Int = 8317): Int {
        return nativeStartServer(configDir, host, port)
    }

    fun stopServer() {
        nativeStopServer()
    }

    fun getServerStatus(): Int {
        return nativeGetServerStatus()
    }

    fun pollOAuthURL(timeoutMs: Int = 1000): String? {
        return nativePollOAuthURL(timeoutMs)
    }
}
