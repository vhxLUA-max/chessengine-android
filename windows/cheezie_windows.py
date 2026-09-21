#!/usr/bin/env python3
import os
import shutil
import sys
import threading
import tkinter as tk
from pathlib import Path
from tkinter import messagebox, ttk
from http.server import ThreadingHTTPServer

from engine_server import APIHandler, EngineService

HOST = "127.0.0.1"
PORT = 8765


def bundled_root():
    if getattr(sys, "frozen", False):
        return Path(sys._MEIPASS)
    return Path(__file__).resolve().parent


def prepare_engine():
    source_root = bundled_root()
    source_engine = source_root / "stockfish.exe"
    if not source_engine.is_file():
        raise FileNotFoundError("Bundled Stockfish engine was not found.")

    runtime_root = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "Cheezie" / "engine"
    runtime_root.mkdir(parents=True, exist_ok=True)

    for source in source_root.glob("*.nnue"):
        target = runtime_root / source.name
        if not target.exists() or source.stat().st_mtime_ns > target.stat().st_mtime_ns:
            shutil.copy2(source, target)

    target_engine = runtime_root / "stockfish.exe"
    if not target_engine.exists() or source_engine.stat().st_mtime_ns > target_engine.stat().st_mtime_ns:
        shutil.copy2(source_engine, target_engine)

    license_source = source_root / "Copying.txt"
    if license_source.is_file():
        shutil.copy2(license_source, runtime_root / "Copying.txt")

    return target_engine, runtime_root


class CheezieWindows:
    def __init__(self, root):
        self.root = root
        self.root.title("Cheezie Windows")
        self.root.geometry("520x330")
        self.root.resizable(False, False)
        self.service = None
        self.httpd = None
        self.engine_path = None
        self.runtime_root = None

        self.depth_var = tk.IntVar(value=12)
        self.multipv_var = tk.IntVar(value=3)
        self.status_var = tk.StringVar(value="Starting engine...")
        self.endpoint_var = tk.StringVar(value=f"http://{HOST}:{PORT}")

        self._build_ui()
        self.root.protocol("WM_DELETE_WINDOW", self._close)
        self.root.after(100, self._start)

    def _build_ui(self):
        main = ttk.Frame(self.root, padding=18)
        main.pack(fill="both", expand=True)

        ttk.Label(main, text="Cheezie Windows", font=("TkDefaultFont", 18, "bold")).pack(anchor="w")
        ttk.Label(
            main,
            text="Local chess engine service. No manual chessboard.",
        ).pack(anchor="w", pady=(2, 18))

        engine_box = ttk.LabelFrame(main, text="Engine", padding=10)
        engine_box.pack(fill="x")

        ttk.Label(engine_box, text="Depth").grid(row=0, column=0, sticky="w")
        ttk.Spinbox(
            engine_box, from_=1, to=30, textvariable=self.depth_var, width=8
        ).grid(row=0, column=1, padx=(6, 18))

        ttk.Label(engine_box, text="MultiPV").grid(row=0, column=2, sticky="w")
        ttk.Spinbox(
            engine_box, from_=1, to=10, textvariable=self.multipv_var, width=8
        ).grid(row=0, column=3, padx=6)

        ttk.Button(engine_box, text="Restart Engine", command=self._restart).grid(
            row=0, column=4, padx=(18, 0)
        )

        service_box = ttk.LabelFrame(main, text="Service", padding=10)
        service_box.pack(fill="x", pady=14)

        ttk.Label(service_box, text="Status").grid(row=0, column=0, sticky="w")
        ttk.Label(service_box, textvariable=self.status_var).grid(
            row=0, column=1, sticky="w", padx=8
        )

        ttk.Label(service_box, text="API").grid(row=1, column=0, sticky="w", pady=(8, 0))
        ttk.Entry(
            service_box, textvariable=self.endpoint_var, state="readonly", width=32
        ).grid(row=1, column=1, sticky="w", padx=8, pady=(8, 0))

        ttk.Label(
            main,
            text="The engine is bundled with Cheezie and starts automatically.",
            wraplength=470,
        ).pack(anchor="w")

    def _start(self):
        try:
            self.engine_path, self.runtime_root = prepare_engine()
            os.chdir(self.runtime_root)

            self.service = EngineService(
                str(self.engine_path),
                threads=max(1, os.cpu_count() or 2),
                hash_mb=256,
                default_multipv=self.multipv_var.get(),
            )
            self.service.start()

            self.httpd = ThreadingHTTPServer((HOST, PORT), APIHandler)
            self.httpd.engine_service = self.service
            self.httpd.auth_token = ""
            threading.Thread(target=self.httpd.serve_forever, daemon=True).start()

            self.status_var.set("Running")
        except Exception as exc:
            self.status_var.set("Engine error")
            messagebox.showerror("Cheezie startup error", str(exc))

    def _restart(self):
        try:
            if self.httpd is not None:
                self.httpd.server_close()
                self.httpd = None
            if self.service is not None:
                self.service.stop()
                self.service = None

            self.status_var.set("Restarting...")
            self._start()
        except Exception as exc:
            self.status_var.set("Engine error")
            messagebox.showerror("Cheezie restart error", str(exc))

    def _close(self):
        if self.httpd is not None:
            self.httpd.server_close()
        if self.service is not None:
            self.service.stop()
        self.root.destroy()


def main():
    root = tk.Tk()
    CheezieWindows(root)
    root.mainloop()


if __name__ == "__main__":
    main()
