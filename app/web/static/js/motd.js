/* MOTD-Editor: Codes einfuegen und live anzeigen, wie es im Spiel aussieht.
 *
 * Der Text kommt aus einem Eingabefeld und wird unmittelbar wieder angezeigt. Deshalb
 * entsteht die Vorschau AUSSCHLIESSLICH aus DOM-Knoten (createElement/textContent) -
 * niemals aus zusammengebautem HTML. Sonst waere das Eingabefeld ein Einfallstor.
 */
(function () {
    "use strict";

    /* Minecrafts Palette. Die Werte sind die echten Farben des Spiels - eine eigene
     * Auswahl waere huebscher, aber die Vorschau soll nicht luegen. */
    var FARBEN = [
        ["0", "Schwarz", "#000000"], ["1", "Dunkelblau", "#0000aa"],
        ["2", "Dunkelgruen", "#00aa00"], ["3", "Petrol", "#00aaaa"],
        ["4", "Dunkelrot", "#aa0000"], ["5", "Violett", "#aa00aa"],
        ["6", "Gold", "#ffaa00"], ["7", "Grau", "#aaaaaa"],
        ["8", "Dunkelgrau", "#555555"], ["9", "Blau", "#5555ff"],
        ["a", "Gruen", "#55ff55"], ["b", "Tuerkis", "#55ffff"],
        ["c", "Rot", "#ff5555"], ["d", "Rosa", "#ff55ff"],
        ["e", "Gelb", "#ffff55"], ["f", "Weiss", "#ffffff"]
    ];

    var FORMATE = [
        ["l", "Fett", "F"], ["o", "Kursiv", "K"], ["n", "Unterstrichen", "U"],
        ["m", "Durchgestrichen", "D"], ["k", "Verschluesselt", "?"],
        ["r", "Zuruecksetzen", "↺"]
    ];

    var PARAGRAF = "§";
    var VERWUERFELN = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#$%&*";

    /* Eigene Farben (Paragraf-x) kann in der MOTD nur die Paper-Familie. Bei Vanilla,
     * Fabric und Forge sendet der Server die sechs Codes einzeln - die Vorschau muss
     * das genauso zeigen, sonst verspricht sie eine Farbe, die es im Spiel nicht gibt. */
    var hexErlaubt = true;

    /* Knoten mit Paragraf-k, die der Takt unten durchwuerfelt. Gehoert hierher und
     * nicht zur Editor-Verdrahtung: stueck() benutzt sie, und der Darsteller laeuft
     * auch ohne Editor (Vorschau im Test). */
    var verwuerfelt = [];

    // Nach aussen nur das, was ohne Editor Sinn ergibt - damit es pruefbar ist.
    window.MotdDarsteller = {
        zeichneZeile: zeichneZeile,
        ohneCodes: ohneCodes,
        hexCode: hexCode,
        // Nur fuer die Pruefung: ohne Editor gibt es kein data-hex am Element.
        hexFarbenErlauben: function (an) { hexErlaubt = !!an; },
        sichtbareLaenge: sichtbareLaenge,
        FARBEN: FARBEN
    };

    var wurzel = document.getElementById("motd-editor");
    if (!wurzel) { return; }

    var eingaben = Array.prototype.slice.call(wurzel.querySelectorAll(".motd-eingabe"));
    var hinweis = wurzel.querySelector(".motd-hinweis");
    var letztesFeld = eingaben[0] || null;

    eingaben.forEach(function (feld) {
        feld.addEventListener("focus", function () { letztesFeld = feld; });
        feld.addEventListener("input", zeichnen);
        feld.addEventListener("click", function () { letztesFeld = feld; });
    });

    /* ---------------------------------------------------------- Palette bauen */

    function knopf(text, titel, stil, aufCode) {
        var b = document.createElement("button");
        b.type = "button";                 // sonst schickt jeder Klick das Formular ab
        b.className = "motd-knopf";
        b.title = titel;
        b.setAttribute("aria-label", titel);
        b.textContent = text;
        if (stil) { Object.keys(stil).forEach(function (k) { b.style[k] = stil[k]; }); }
        b.addEventListener("click", function () { einfuegen(aufCode); });
        return b;
    }

    var farbkasten = wurzel.querySelector('[data-gruppe="farben"]');
    FARBEN.forEach(function (eintrag) {
        var code = eintrag[0], name = eintrag[1], farbe = eintrag[2];
        farbkasten.appendChild(knopf("", name + " (" + PARAGRAF + code + ")",
            { background: farbe }, PARAGRAF + code));
    });

    var formatkasten = wurzel.querySelector('[data-gruppe="formate"]');
    FORMATE.forEach(function (eintrag) {
        var code = eintrag[0], name = eintrag[1], zeichen = eintrag[2];
        var b = knopf(zeichen, name + " (" + PARAGRAF + code + ")", null, PARAGRAF + code);
        b.classList.add("motd-format", "motd-format-" + code);
        formatkasten.appendChild(b);
    });

    /* Eigene Farbe nur dort anbieten, wo sie auch wirkt: §x funktioniert in der MOTD
     * nur bei Paper und Verwandten, nicht bei Vanilla. Ein Knopf, der nichts tut,
     * ist schlimmer als keiner. */
    hexErlaubt = wurzel.dataset.hex === "ja";
    var hexFeld = document.getElementById("motd-hex");
    if (hexFeld && hexErlaubt) {
        hexFeld.parentNode.hidden = false;
        hexFeld.addEventListener("change", function () {
            einfuegen(hexCode(hexFeld.value));
        });
    }

    function hexCode(farbe) {
        var hex = String(farbe || "").replace("#", "").toLowerCase();
        if (!/^[0-9a-f]{6}$/.test(hex)) { return ""; }
        var raus = PARAGRAF + "x";
        for (var i = 0; i < 6; i++) { raus += PARAGRAF + hex.charAt(i); }
        return raus;
    }

    var loeschen = document.getElementById("motd-clear");
    if (loeschen) {
        loeschen.addEventListener("click", function () {
            eingaben.forEach(function (feld) {
                feld.value = ohneCodes(feld.value);
            });
            zeichnen();
        });
    }

    function ohneCodes(text) {
        return String(text || "").replace(/§x(?:§[0-9a-f]){6}/gi, "")
                                 .replace(/§[0-9a-fk-or]/gi, "");
    }

    /* -------------------------------------------------- Code an die Marke setzen */

    function einfuegen(code) {
        if (!code) { return; }
        var feld = letztesFeld || eingaben[0];
        if (!feld) { return; }
        var von = typeof feld.selectionStart === "number" ? feld.selectionStart : feld.value.length;
        var bis = typeof feld.selectionEnd === "number" ? feld.selectionEnd : von;
        feld.value = feld.value.slice(0, von) + code + feld.value.slice(bis);
        var danach = von + code.length;
        feld.focus();
        try { feld.setSelectionRange(danach, danach); } catch (e) { /* alte Browser */ }
        zeichnen();
    }

    /* ------------------------------------------------------------- Vorschau */

    function neuerStil() {
        return { farbe: "#aaaaaa", fett: false, kursiv: false,
                 unter: false, durch: false, wuerfel: false };
    }

    /** Ein Textstueck als span mit dem aktuellen Stil - rein ueber DOM. */
    function stueck(text, stil) {
        var s = document.createElement("span");
        s.textContent = text;
        s.style.color = stil.farbe;
        if (stil.fett) { s.style.fontWeight = "700"; }
        if (stil.kursiv) { s.style.fontStyle = "italic"; }
        var deko = [];
        if (stil.unter) { deko.push("underline"); }
        if (stil.durch) { deko.push("line-through"); }
        if (deko.length) { s.style.textDecoration = deko.join(" "); }
        if (stil.wuerfel) {
            s.classList.add("motd-wuerfel");
            s.dataset.laenge = String(text.length);
            verwuerfelt.push(s);
        }
        return s;
    }

    function zeichneZeile(ziel, text) {
        while (ziel.firstChild) { ziel.removeChild(ziel.firstChild); }
        var stil = neuerStil();
        var puffer = "";
        var i = 0;

        function spuelen() {
            if (puffer) { ziel.appendChild(stueck(puffer, stil)); puffer = ""; }
        }

        while (i < text.length) {
            if (text.charAt(i) !== PARAGRAF) {
                puffer += text.charAt(i);
                i += 1;
                continue;
            }
            if (i + 1 >= text.length) {
                i += 1;   // haengendes Trennzeichen am Ende - der Server sendet es auch nicht
                continue;
            }
            var code = text.charAt(i + 1).toLowerCase();

            // §x§r§r§g§g§b§b - eine eigene Farbe (Paper).
            if (code === "x" && hexErlaubt && /^(?:§[0-9a-f]){6}/i.test(text.slice(i + 2))) {
                spuelen();
                var hex = "";
                for (var k = 0; k < 6; k++) { hex += text.charAt(i + 3 + k * 2); }
                // Wie jede andere Farbe setzt auch diese die Formate zurueck - sonst
                // zeigt die Vorschau fett, wo der Server mager sendet.
                stil = neuerStil();
                stil.farbe = "#" + hex.toLowerCase();
                i += 14;
                continue;
            }

            var farbe = null;
            for (var f = 0; f < FARBEN.length; f++) {
                if (FARBEN[f][0] === code) { farbe = FARBEN[f][2]; break; }
            }
            if (farbe) {
                // Eine Farbe setzt in Minecraft auch alle Formate zurueck.
                spuelen();
                stil = neuerStil();
                stil.farbe = farbe;
                i += 2;
                continue;
            }
            if ("klmno".indexOf(code) >= 0) {
                spuelen();
                stil = Object.assign({}, stil);
                if (code === "l") { stil.fett = true; }
                if (code === "o") { stil.kursiv = true; }
                if (code === "n") { stil.unter = true; }
                if (code === "m") { stil.durch = true; }
                if (code === "k") { stil.wuerfel = true; }
                i += 2;
                continue;
            }
            if (code === "r") {
                spuelen();
                stil = neuerStil();
                i += 2;
                continue;
            }
            // Unbekannter Code: Minecraft verschluckt die ganze Folge. Wer ihn hier
            // stehen laesst, zeigt ab da alles um ein Zeichen versetzt an.
            i += 2;
        }
        spuelen();
    }

    function sichtbareLaenge(text) {
        return ohneCodes(text).length;
    }

    function zeichnen() {
        verwuerfelt = [];
        var warnungen = [];
        eingaben.forEach(function (feld, index) {
            var ziel = wurzel.querySelector('.motd-zeile[data-zeile="' + (index + 1) + '"]');
            if (ziel) { zeichneZeile(ziel, feld.value || ""); }

            var zaehler = wurzel.querySelector('.motd-zaehler[data-fuer="' + feld.id + '"]');
            var sichtbar = sichtbareLaenge(feld.value);
            if (zaehler) {
                zaehler.textContent = sichtbar + " sichtbare Zeichen";
                zaehler.classList.toggle("motd-zuviel", sichtbar > 45);
            }
            if (sichtbar > 45) {
                warnungen.push("Zeile " + (index + 1) + " ist laenger als rund 45 Zeichen - "
                    + "die Serverliste schneidet dort ab.");
            }
        });
        if (hinweis) { hinweis.textContent = warnungen.join(" "); }
    }

    /* §k heisst "verwuerfelt": im Spiel flackert der Text. Ohne Bewegung sieht die
     * Vorschau aus wie ein Fehler, deshalb laeuft hier ein ruhiger Takt. Wer
     * Bewegung abbestellt hat, bekommt stattdessen feste Platzhalter. */
    var ruhig = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    function wuerfeln() {
        for (var i = 0; i < verwuerfelt.length; i++) {
            var n = parseInt(verwuerfelt[i].dataset.laenge, 10) || 0;
            var raus = "";
            for (var k = 0; k < n; k++) {
                raus += VERWUERFELN.charAt(Math.floor(Math.random() * VERWUERFELN.length));
            }
            verwuerfelt[i].textContent = raus;
        }
    }
    if (ruhig) {
        // Ohne Bewegung stand bisher der lesbare Klartext da - Paragraf-k
        // heisst aber "verwuerfelt", und genau das soll man sehen.
        zeichnen();
        wuerfeln();
    } else {
        window.setInterval(wuerfeln, 80);
    }

    zeichnen();
})();
