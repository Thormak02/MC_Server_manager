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

        initResourcesLive();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
