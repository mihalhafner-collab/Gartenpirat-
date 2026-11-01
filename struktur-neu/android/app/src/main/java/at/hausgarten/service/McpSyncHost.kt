package at.hausgarten.service

/** Callback shared by Activity and tunnel contexts for scheduling after MCP writes. */
internal interface McpSyncHost {
    fun requestMcpSync()
}
