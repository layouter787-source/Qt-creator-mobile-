# Qt Creator Mobile AI Connector

## Goal

Connect an MCP-compatible AI client to the user's running Qt Creator Mobile IDE. The AI can inspect project metadata, read and edit project text files, and request builds through the IDE's existing build pipeline.

## Topology

    AI client (MCP)
          |
          | HTTPS / MCP Streamable HTTP
          v
    Cloud connector (Node.js)
          |
          | authenticated WSS, outbound from Android
          v
    Qt Creator Mobile agent
          |
          +-- private app storage: files/projects
          +-- existing BuildPipelineFactory

The phone does not open an inbound port. The cloud server brokers narrowly scoped commands over the device's authenticated WebSocket session.

## Trust boundaries

- MCP bearer token authenticates the AI client to the cloud endpoint.
- A separate device bearer token authenticates the Android agent.
- Project names and relative paths are validated on-device using canonical paths.
- Only project file operations and the existing build pipeline are exposed.
- There is no general shell, arbitrary process, or unrestricted filesystem tool.
- File writes and builds can change user data or consume device resources; the connected state and each operation should be visible.

## Initial tools

| MCP tool | Purpose |
|---|---|
| qtcm_status | Check whether the device is online |
| qtcm_list_projects | List IDE projects |
| qtcm_read_file | Read a project text file |
| qtcm_write_file | Write a project text file |
| qtcm_build_project | Run the IDE build pipeline for a project |

## Delivery phases

1. Cloud MCP endpoint, authentication, and WebSocket command broker.
2. Android agent and manual pairing settings.
3. End-to-end tests and visible approval/connection controls.
4. Public deployment with TLS and a supported OAuth onboarding flow for clients that require it.
5. Additional IDE tools (diagnostics, project creation, build logs) after the first path is stable.
