(function () {
    const root = document.documentElement;
    const body = document.body;
    const themeToggle = document.getElementById("theme-toggle");
    const sidebarToggle = document.getElementById("sidebar-toggle");
    const hasSidebar = body.classList.contains("layout-authenticated");
    const mediaMobile = window.matchMedia("(max-width: 980px)");

    function applyTheme(theme) {
        const value = theme === "dark" ? "dark" : "light";
        root.setAttribute("data-theme", value);
        try {
            localStorage.setItem("mcsm.theme", value);
        } catch (_) {}
    }

    function applySidebarState(collapsed) {
        if (!hasSidebar) return;
        if (mediaMobile.matches) {
            body.classList.toggle("sidebar-open", !collapsed);
            body.classList.remove("sidebar-collapsed");
        } else {
            body.classList.remove("sidebar-open");
            body.classList.toggle("sidebar-collapsed", collapsed);
        }
        try {
            localStorage.setItem("mcsm.sidebar.collapsed", collapsed ? "1" : "0");
        } catch (_) {}
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
        applySidebarState(collapsed);

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

        mediaMobile.addEventListener("change", function () {
            const stored = body.classList.contains("sidebar-collapsed");
            applySidebarState(stored);
        });
    }
})();
