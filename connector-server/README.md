# Qt Creator Mobile AI Connector - cloud bridge

This service exposes the paired Qt Creator Mobile device as an MCP server. An MCP-compatible AI client connects to POST /mcp; the Android app maintains an outbound authenticated WebSocket to /agent. The cloud service never needs an inbound connection to the phone.

## Tools exposed

- qtcm_status: device connection state.
- qtcm_list_projects: projects available in the IDE.
- qtcm_read_file: read a text file from a project.
- qtcm_write_file: write a project file (the Android agent validates the workspace path).
- qtcm_build_project: request the IDE's existing build pipeline.

The server deliberately does not expose a shell or arbitrary code-execution tool.

## Run locally

1. Install Node.js 22.
2. Run npm install in this directory.
3. Copy .env.example to .env and generate two different random secrets of at least 32 characters.
4. Run npm run dev.

The MCP endpoint is http://localhost:8787/mcp; the device WebSocket endpoint is ws://localhost:8787/agent. Use TLS (HTTPS/WSS) in any public deployment.

## Security

- MCP and device sessions use separate bearer tokens.
- The device connection is outbound-only.
- The Android agent limits file access to files/projects/<project>.
- No shell execution is exposed.
- Use a private deployment and rotate tokens if a device is lost.
- Do not commit .env, API keys, or device tokens.

## Current implementation boundary

The cloud MCP server and command broker are implemented. The Android agent and manual pairing controls are implemented in the IDE branch. Public deployment, TLS, OAuth-based client onboarding, reconnect policy, and end-to-end tests still need to be completed before treating this as production-ready.
