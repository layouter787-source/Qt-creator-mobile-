import { createServer } from "node:http";
import { timingSafeEqual } from "node:crypto";
import { WebSocketServer } from "ws";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { z } from "zod";
import { DeviceBridge } from "./bridge.js";

const port = Number(process.env.PORT ?? 8787);
const mcpToken = process.env.MCP_BEARER_TOKEN ?? "";
const deviceToken = process.env.DEVICE_TOKEN ?? "";
const deviceId = process.env.DEVICE_ID ?? "qtcm-android-device";

if (mcpToken.length < 32 || deviceToken.length < 32) {
  throw new Error("MCP_BEARER_TOKEN and DEVICE_TOKEN must each contain at least 32 characters.");
}

function safeEqual(actual: string, expected: string): boolean {
  const a = Buffer.from(actual);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

function bearer(request: import("node:http").IncomingMessage): string {
  const value = request.headers.authorization ?? "";
  return value.startsWith("Bearer ") ? value.slice(7) : "";
}

const bridge = new DeviceBridge();
const server = createServer(async (request, response) => {
  if (request.url === "/health" && request.method === "GET") {
    response.writeHead(200, { "content-type": "application/json" });
    response.end(JSON.stringify({ ok: true, deviceConnected: bridge.connected }));
    return;
  }

  if (request.url !== "/mcp" || request.method !== "POST") {
    response.writeHead(404).end("Not found");
    return;
  }
  if (!safeEqual(bearer(request), mcpToken)) {
    response.writeHead(401, { "www-authenticate": "Bearer", "content-type": "application/json" });
    response.end(JSON.stringify({ error: "Unauthorized" }));
    return;
  }

  try {
    const chunks: Buffer[] = [];
    for await (const chunk of request) chunks.push(Buffer.from(chunk));
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));

    const mcp = new McpServer({ name: "qt-creator-mobile-bridge", version: "0.1.0" });
    mcp.tool("qtcm_status", "Check whether the paired Qt Creator Mobile device is connected.", {}, async () => ({
      content: [{ type: "text", text: JSON.stringify({ deviceId, connected: bridge.connected }) }],
    }));
    mcp.tool("qtcm_list_projects", "List projects currently stored inside Qt Creator Mobile.", {}, async () => {
      const result = await bridge.request("list_projects");
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    });
    mcp.tool("qtcm_read_file", "Read a text file from a project. Paths are relative to the selected project.", {
      project: z.string().min(1).max(80),
      path: z.string().min(1).max(400),
    }, async ({ project, path }) => {
      const result = await bridge.request("read_file", { project, path });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    });
    mcp.tool("qtcm_write_file", "Write a text file in a project. The device applies workspace and path safety checks.", {
      project: z.string().min(1).max(80),
      path: z.string().min(1).max(400),
      content: z.string().max(200000),
    }, async ({ project, path, content }) => {
      const result = await bridge.request("write_file", { project, path, content });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    });
    mcp.tool("qtcm_build_project", "Start a build for a project already configured in Qt Creator Mobile.", {
      project: z.string().min(1).max(80),
    }, async ({ project }) => {
      const result = await bridge.request("build_project", { project });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    });

    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    await mcp.connect(transport);
    await transport.handleRequest(request, response, body);
  } catch (error) {
    if (!response.headersSent) response.writeHead(500, { "content-type": "application/json" });
    response.end(JSON.stringify({ error: error instanceof Error ? error.message : "Internal error" }));
  }
});

const agents = new WebSocketServer({ noServer: true, maxPayload: 1024 * 1024 });
server.on("upgrade", (request, socket, head) => {
  if (request.url !== "/agent" || !safeEqual(bearer(request), deviceToken)) {
    socket.write("HTTP/1.1 401 Unauthorized\r\n\r\n");
    socket.destroy();
    return;
  }
  agents.handleUpgrade(request, socket, head, (ws) => agents.emit("connection", ws, request));
});
agents.on("connection", (socket) => bridge.attach(socket));

server.listen(port, "0.0.0.0", () => {
  console.log(`Qt Creator Mobile MCP bridge listening on :${port}`);
});
