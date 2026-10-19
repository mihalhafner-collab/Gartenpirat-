package at.hausgarten.service;

/** Explicit callback for the same Activity and tunnel contexts that previously scheduled MCP sync. */
interface McpSyncHost { void requestMcpSync(); }
