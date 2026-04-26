(function () {
    function init() {
        const root = document.documentElement;
        const body = document.body;
        const themeToggle = document.getElementById("theme-toggle");
        const sidebarToggle = document.getElementById("sidebar-toggle");
        const hasSidebar = body.classList.contains("layout-authenticated");
        const mediaMobile = window.matchMedia("(max-width: 980px)");

        function updateThemeToggle(theme) {
            if (!themeToggle) return;
            const label = theme === "dark" ? "Light" : "Dark";
            themeToggle.textContent = label;
            themeToggle.setAttribute("aria-pressed", theme === "dark" ? "true" : "false");
        }

        function applyTheme(theme) {
            const value = theme === "dark" ? "dark" : "light";
            root.setAttribute("data-theme", value);
            updateThemeToggle(value);
            try {
                localStorage.setItem("mcsm.theme", value);
            } catch (_) {}
        }

        function applySidebarState(collapsed, persist = true) {
            if (!hasSidebar) return;
            if (mediaMobile.matches) {
                body.classList.toggle("sidebar-open", !collapsed);
                body.classList.remove("sidebar-collapsed");
            } else {
                body.classList.remove("sidebar-open");
                body.classList.toggle("sidebar-collapsed", collapsed);
            }
            if (persist) {
                try {
                    localStorage.setItem("mcsm.sidebar.collapsed", collapsed ? "1" : "0");
                } catch (_) {}
            }
        }

        let preferredTheme = "light";
        try {
            preferredTheme = localStorage.getItem("mcsm.theme") || "light";
        } catch (_) {}
        applyTheme(preferredTheme);

        if (themeToggle) {
            themeToggle.addEventListener("click", function () {
                const current = root.getAttribute("data-theme");
                applyTheme(current === "dark" ? "light" : "dark");
            });
        }

        if (hasSidebar) {
            let collapsed = false;
            try {
                collapsed = localStorage.getItem("mcsm.sidebar.collapsed") === "1";
            } catch (_) {}
            if (mediaMobile.matches) {
                applySidebarState(true, false);
            } else {
                applySidebarState(collapsed);
            }

            if (sidebarToggle) {
                sidebarToggle.addEventListener("click", function () {
                    if (mediaMobile.matches) {
                        body.classList.toggle("sidebar-open");
                        return;
                    }
                    const next = !body.classList.contains("sidebar-collapsed");
                    applySidebarState(next);
                });
            }

            const sideNav = document.querySelector(".side-nav");
            if (sideNav) {
                sideNav.addEventListener("click", function (event) {
                    const target = event.target;
                    if (!(target instanceof HTMLElement)) return;
                    if (!target.closest("a")) return;
                    if (mediaMobile.matches) {
                        body.classList.remove("sidebar-open");
                    }
                });
            }

            if (typeof mediaMobile.addEventListener === "function") {
                mediaMobile.addEventListener("change", function () {
                    if (mediaMobile.matches) {
                        applySidebarState(true, false);
                    } else {
                        const stored = body.classList.contains("sidebar-collapsed");
                        applySidebarState(stored);
                    }
                });
            } else if (typeof mediaMobile.addListener === "function") {
                mediaMobile.addListener(function () {
                    if (mediaMobile.matches) {
                        applySidebarState(true, false);
                    } else {
                        const stored = body.classList.contains("sidebar-collapsed");
                        applySidebarState(stored);
                    }
                });
            }
        }

        function initResourcesLive() {
            const container = document.getElementById("resources-live");
            if (!container) return;
            const endpoint = container.dataset.endpoint || "/api/resources";
            const interval = parseInt(container.dataset.interval || "5000", 10);
            const hostCpu = document.getElementById("host-cpu");
            const hostRam = document.getElementById("host-ram");
            const hostCpuLogical = document.getElementById("host-cpu-logical");
            const tbody = document.getElementById("resources-body");

            function escapeHtml(value) {
                return String(value)
                    .replace(/&/g, "&amp;")
                    .replace(/</g, "&lt;")
                    .replace(/>/g, "&gt;")
                    .replace(/\"/g, "&quot;")
                    .replace(/'/g, "&#39;");
            }

            function formatNumber(value, digits) {
                if (value === null || value === undefined || Number.isNaN(value)) {
                    return "-";
                }
                return Number(value).toFixed(digits);
            }

            function updateHost(host) {
                if (hostCpu) {
                    hostCpu.textContent =
                        host.cpu_percent === null || host.cpu_percent === undefined
                            ? "-"
                            : `${formatNumber(host.cpu_percent, 1)}%`;
                }
                if (hostRam) {
                    if (
                        host.memory_used_mb === null ||
                        host.memory_used_mb === undefined ||
                        host.memory_total_mb === null ||
                        host.memory_total_mb === undefined
                    ) {
                        hostRam.textContent = "-";
                    } else {
                        hostRam.textContent = `${formatNumber(host.memory_used_mb, 0)}/${formatNumber(
                            host.memory_total_mb,
                            0
                        )} MB`;
                    }
                }
                if (hostCpuLogical) {
                    hostCpuLogical.textContent =
                        host.cpu_logical === null || host.cpu_logical === undefined
                            ? "-"
                            : String(host.cpu_logical);
                }
            }

            function updateEntries(entries) {
                if (!tbody) return;
                if (!entries || entries.length === 0) {
                    tbody.innerHTML = "<tr><td colspan=\"8\">Keine Server sichtbar.</td></tr>";
                    return;
                }

                const rows = entries
                    .map((entry) => {
                        const server = entry.server || {};
                        const usage = entry.usage || {};
                        const playersCurrent =
                            entry.players_current === null || entry.players_current === undefined
                                ? 0
                                : entry.players_current;
                        const playersMax =
                            entry.players_max === null || entry.players_max === undefined
                                ? "?"
                                : entry.players_max;
                        const uptime =
                            usage.uptime_seconds === null || usage.uptime_seconds === undefined
                                ? "-"
                                : `${formatNumber(usage.uptime_seconds, 0)}s`;

                        return `
<tr>
    <td><a href="/servers/${server.id}">${escapeHtml(server.name || "-")}</a></td>
    <td>${escapeHtml(server.status || "-")}</td>
    <td>${playersCurrent}/${playersMax}</td>
    <td>${formatNumber(usage.cpu_percent || 0, 1)}</td>
    <td>${formatNumber(usage.memory_mb || 0, 1)}</td>
    <td>${formatNumber(entry.memory_share_percent || 0, 1)}%</td>
    <td>${usage.pid || "-"}</td>
    <td>${uptime}</td>
</tr>`;
                    })
                    .join("");

                tbody.innerHTML = rows;
            }

            async function refresh() {
                try {
                    const response = await fetch(endpoint, {
                        headers: { "Accept": "application/json" },
                        cache: "no-store",
                    });
                    if (!response.ok) return;
                    const data = await response.json();
                    updateHost(data.host || {});
                    updateEntries(data.entries || []);
                } catch (_) {}
            }

            refresh();
            const intervalId = window.setInterval(refresh, Number.isFinite(interval) ? interval : 5000);
            window.addEventListener("beforeunload", function () {
                window.clearInterval(intervalId);
            });
        }

        function initDashboardLive() {
            const container = document.getElementById("dashboard-live");
            if (!container) return;

            const endpoint = container.dataset.endpoint || "/api/resources";
            const interval = parseInt(container.dataset.interval || "5000", 10);
            const hostCpu = document.getElementById("dashboard-host-cpu");
            const hostRam = document.getElementById("dashboard-host-ram");
            const totalServers = document.getElementById("dashboard-total-servers");
            const runningServers = document.getElementById("dashboard-running-servers");

            function escapeHtml(value) {
                return String(value)
                    .replace(/&/g, "&amp;")
                    .replace(/</g, "&lt;")
                    .replace(/>/g, "&gt;")
                    .replace(/\"/g, "&quot;")
                    .replace(/'/g, "&#39;");
            }

            function formatNumber(value, digits) {
                if (value === null || value === undefined || Number.isNaN(value)) {
                    return "-";
                }
                return Number(value).toFixed(digits);
            }

            function updateHost(host) {
                if (hostCpu) {
                    hostCpu.textContent =
                        host.cpu_percent === null || host.cpu_percent === undefined
                            ? "-"
                            : `${formatNumber(host.cpu_percent, 1)}%`;
                }
                if (hostRam) {
                    hostRam.textContent =
                        host.memory_percent === null || host.memory_percent === undefined
                            ? "-"
                            : `${formatNumber(host.memory_percent, 1)}%`;
                }
            }

            function updateServers(entries) {
                if (!Array.isArray(entries)) return;

                if (totalServers) {
                    totalServers.textContent = String(entries.length);
                }

                let runningCount = 0;
                entries.forEach((entry) => {
                    const server = entry.server || {};
                    const usage = entry.usage || {};
                    const serverId = server.id;
                    if (!serverId) return;

                    const status = String(server.status || "-");
                    const isOnline = status === "running" || usage.running === true;
                    if (isOnline) {
                        runningCount += 1;
                    }

                    const statusEl = document.getElementById(`dashboard-status-${serverId}`);
                    const playersEl = document.getElementById(`dashboard-players-${serverId}`);
                    const cpuEl = document.getElementById(`dashboard-cpu-${serverId}`);
                    const ramEl = document.getElementById(`dashboard-ram-${serverId}`);

                    if (statusEl) {
                        statusEl.dataset.online = isOnline ? "true" : "false";
                        statusEl.innerHTML = `
<span class="status-dot ${isOnline ? "status-dot-online" : "status-dot-offline"}" aria-hidden="true"></span>
<span class="dashboard-status-label">${escapeHtml(status)}</span>
`;
                    }
                    if (playersEl) {
                        const playersCurrent =
                            entry.players_current === null || entry.players_current === undefined
                                ? 0
                                : entry.players_current;
                        const playersMax =
                            entry.players_max === null || entry.players_max === undefined
                                ? "?"
                                : entry.players_max;
                        playersEl.textContent = `${playersCurrent}/${playersMax}`;
                        const onlinePlayers = Array.isArray(entry.online_players)
                            ? entry.online_players.map((name) => String(name).trim()).filter((name) => name.length > 0)
                            : [];
                        if (onlinePlayers.length > 0) {
                            playersEl.setAttribute("title", onlinePlayers.join(", "));
                        } else {
                            playersEl.removeAttribute("title");
                        }
                    }
                    if (cpuEl) {
                        cpuEl.textContent = `${formatNumber(usage.cpu_percent || 0, 1)}%`;
                    }
                    if (ramEl) {
                        ramEl.textContent = `${formatNumber(usage.memory_mb || 0, 1)} MB`;
                    }
                });

                if (runningServers) {
                    runningServers.textContent = String(runningCount);
                }
            }

            async function refresh() {
                try {
                    const response = await fetch(endpoint, {
                        headers: { Accept: "application/json" },
                        cache: "no-store",
                    });
                    if (!response.ok) return;
                    const data = await response.json();
                    updateHost(data.host || {});
                    updateServers(data.entries || []);
                } catch (_) {}
            }

            refresh();
            const intervalId = window.setInterval(refresh, Number.isFinite(interval) ? interval : 5000);
            window.addEventListener("beforeunload", function () {
                window.clearInterval(intervalId);
            });
        }

        function initSystemStatusLive() {
            const container = document.getElementById("system-status-live");
            if (!container) return;

            const summaryEndpoint = container.dataset.summaryEndpoint || "/api/system/summary";
            const processesEndpoint = container.dataset.processEndpoint || "/api/system/processes";
            const interval = parseInt(container.dataset.interval || "5000", 10);
            const limit = parseInt(container.dataset.limit || "50", 10);

            const hostCpu = document.getElementById("system-host-cpu");
            const hostRam = document.getElementById("system-host-ram");
            const managedRunning = document.getElementById("system-managed-running");
            const managedTotal = document.getElementById("system-managed-total");
            const generatedAt = document.getElementById("system-generated-at");
            const disksBody = document.getElementById("system-disks-body");
            const managedBody = document.getElementById("system-managed-body");
            const hostBody = document.getElementById("system-host-body");

            function escapeHtml(value) {
                return String(value)
                    .replace(/&/g, "&amp;")
                    .replace(/</g, "&lt;")
                    .replace(/>/g, "&gt;")
                    .replace(/\"/g, "&quot;")
                    .replace(/'/g, "&#39;");
            }

            function formatNumber(value, digits) {
                if (value === null || value === undefined || Number.isNaN(value)) {
                    return "-";
                }
                return Number(value).toFixed(digits);
            }

            function updateSummary(summary) {
                if (!summary) return;
                const host = summary.host || {};
                if (hostCpu) {
                    hostCpu.textContent =
                        host.cpu_percent === null || host.cpu_percent === undefined
                            ? "-"
                            : `${formatNumber(host.cpu_percent, 1)}%`;
                }
                if (hostRam) {
                    hostRam.textContent =
                        host.memory_percent === null || host.memory_percent === undefined
                            ? "-"
                            : `${formatNumber(host.memory_percent, 1)}%`;
                }
                if (managedRunning) managedRunning.textContent = String(summary.managed_running ?? 0);
                if (managedTotal) managedTotal.textContent = String(summary.managed_total ?? 0);
                if (generatedAt) generatedAt.textContent = summary.generated_at || "-";

                if (disksBody) {
                    const disks = Array.isArray(summary.disks) ? summary.disks : [];
                    if (disks.length === 0) {
                        disksBody.innerHTML = '<tr><td colspan="7">Keine Datentraegerinfos verfuegbar.</td></tr>';
                    } else {
                        disksBody.innerHTML = disks
                            .map((disk) => {
                                return `
<tr>
    <td>${escapeHtml(disk.device || "-")}</td>
    <td>${escapeHtml(disk.mountpoint || "-")}</td>
    <td>${escapeHtml(disk.fstype || "-")}</td>
    <td>${formatNumber(disk.total_gb, 2)} GB</td>
    <td>${formatNumber(disk.used_gb, 2)} GB</td>
    <td>${formatNumber(disk.free_gb, 2)} GB</td>
    <td>${formatNumber(disk.percent, 1)}%</td>
</tr>`;
                            })
                            .join("");
                    }
                }
            }

            function formatUptime(seconds) {
                if (seconds === null || seconds === undefined || Number.isNaN(seconds)) {
                    return "-";
                }
                const mins = Math.floor(Number(seconds) / 60);
                return `${mins} min`;
            }

            function updateProcesses(data) {
                if (!data) return;

                if (managedBody) {
                    const managed = Array.isArray(data.managed) ? data.managed : [];
                    if (managed.length === 0) {
                        managedBody.innerHTML = '<tr><td colspan="6">Keine managed Prozesse.</td></tr>';
                    } else {
                        managedBody.innerHTML = managed
                            .map((row) => {
                                return `
<tr>
    <td><a href="/servers/${row.server_id}">${escapeHtml(row.server_name || "-")}</a></td>
    <td>${escapeHtml(row.status || "-")}</td>
    <td>${row.pid || "-"}</td>
    <td>${formatNumber(row.cpu_percent || 0, 1)}%</td>
    <td>${formatNumber(row.memory_mb || 0, 1)} MB</td>
    <td>${formatUptime(row.uptime_seconds)}</td>
</tr>`;
                            })
                            .join("");
                    }
                }

                if (hostBody) {
                    const host = Array.isArray(data.host) ? data.host : [];
                    if (host.length === 0) {
                        hostBody.innerHTML = '<tr><td colspan="5">Keine Prozessdaten verfuegbar.</td></tr>';
                    } else {
                        hostBody.innerHTML = host
                            .map((proc) => {
                                return `
<tr>
    <td>${proc.pid || "-"}</td>
    <td>${escapeHtml(proc.name || "-")}</td>
    <td>${escapeHtml(proc.username || "-")}</td>
    <td>${formatNumber(proc.cpu_percent || 0, 1)}%</td>
    <td>${formatNumber(proc.memory_mb || 0, 1)} MB</td>
</tr>`;
                            })
                            .join("");
                    }
                }
            }

            async function refresh() {
                try {
                    const [summaryResp, processResp] = await Promise.all([
                        fetch(summaryEndpoint, {
                            headers: { Accept: "application/json" },
                            cache: "no-store",
                        }),
                        fetch(`${processesEndpoint}?limit=${encodeURIComponent(Number.isFinite(limit) ? limit : 50)}`, {
                            headers: { Accept: "application/json" },
                            cache: "no-store",
                        }),
                    ]);
                    if (summaryResp.ok) {
                        const summary = await summaryResp.json();
                        updateSummary(summary);
                    }
                    if (processResp.ok) {
                        const processes = await processResp.json();
                        updateProcesses(processes);
                    }
                } catch (_) {}
            }

            refresh();
            const intervalId = window.setInterval(refresh, Number.isFinite(interval) ? interval : 5000);
            window.addEventListener("beforeunload", function () {
                window.clearInterval(intervalId);
            });
        }

        initDashboardLive();
        initResourcesLive();
        initSystemStatusLive();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
