import { randomUUID } from "node:crypto";
import type { WebSocket } from "ws";

type Pending = {
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
  timeout: NodeJS.Timeout;
};

export class DeviceBridge {
  private socket: WebSocket | null = null;
  private readonly pending = new Map<string, Pending>();

  attach(socket: WebSocket): void {
    if (this.socket && this.socket.readyState === this.socket.OPEN) {
      this.socket.close(4001, "A newer device session connected");
    }
    this.socket = socket;
    socket.on("message", (raw) => this.onMessage(raw.toString()));
    socket.on("close", () => {
      if (this.socket === socket) this.socket = null;
      for (const [id, item] of this.pending) {
        clearTimeout(item.timeout);
        item.reject(new Error("Qt Creator Mobile disconnected"));
        this.pending.delete(id);
      }
    });
    socket.on("error", () => {
      if (this.socket === socket) this.socket = null;
    });
  }

  get connected(): boolean {
    return !!this.socket && this.socket.readyState === this.socket.OPEN;
  }

  async request<T>(command: string, payload: Record<string, unknown> = {}): Promise<T> {
    if (!this.connected || !this.socket) {
      throw new Error("Qt Creator Mobile is offline. Open the app and connect the AI Bridge.");
    }
    const id = randomUUID();
    const timeoutMs = Number(process.env.AGENT_TIMEOUT_MS ?? 15000);
    const result = new Promise<T>((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`Device command timed out: ${command}`));
      }, timeoutMs);
      this.pending.set(id, { resolve: (value) => resolve(value as T), reject, timeout });
    });
    this.socket.send(JSON.stringify({ type: "command", id, command, payload }));
    return result;
  }

  private onMessage(raw: string): void {
    let message: { type?: string; id?: string; ok?: boolean; result?: unknown; error?: string };
    try {
      message = JSON.parse(raw);
    } catch {
      return;
    }
    if (message.type !== "result" || !message.id) return;
    const item = this.pending.get(message.id);
    if (!item) return;
    clearTimeout(item.timeout);
    this.pending.delete(message.id);
    if (message.ok) item.resolve(message.result);
    else item.reject(new Error(message.error || "Device command failed"));
  }
}
