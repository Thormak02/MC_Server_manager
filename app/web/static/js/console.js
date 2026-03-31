(function () {
    var serverId = window.MCSM_CONSOLE_SERVER_ID;
    if (!serverId) {
        return;
    }

    var output = document.getElementById("console-output");
    if (!output) {
        return;
    }

    function scrollToBottom() {
        output.scrollTop = output.scrollHeight;
    }

    function appendLine(line) {
        var row = document.createElement("div");
        row.textContent = line;
        output.appendChild(row);

        while (output.childElementCount > 1200) {
            output.removeChild(output.firstChild);
        }
        scrollToBottom();
    }

    var protocol = window.location.protocol === "https:" ? "wss" : "ws";
    var wsUrl = protocol + "://" + window.location.host + "/ws/servers/" + serverId + "/console";
    var socket = new WebSocket(wsUrl);

    socket.onmessage = function (event) {
        appendLine(String(event.data || ""));
    };
    socket.onopen = function () {
        scrollToBottom();
    };
    socket.onerror = function () {
        appendLine("[SYSTEM] WebSocket Fehler.");
    };
    socket.onclose = function () {
        appendLine("[SYSTEM] WebSocket Verbindung beendet.");
    };
})();
