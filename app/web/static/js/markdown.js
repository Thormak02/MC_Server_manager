/* Ein kleiner Markdown-Darsteller fuer KI-Antworten.
 *
 * Warum selbst gebaut und nicht eine Bibliothek: die Antwort kommt von einem Sprachmodell
 * und ist damit nicht vertrauenswuerdiger Text. Dieser Darsteller baut ausschliesslich
 * DOM-Knoten und setzt JEDEN Textinhalt ueber textContent. Es gibt keine Stelle, an der
 * innerHTML mit Modellausgabe gefuettert wird - damit kann daraus kein HTML und kein
 * Skript entstehen, egal was im Text steht. Eine Bibliothek, die HTML als Zeichenkette
 * zurueckgibt, braeuchte zusaetzlich einen Saeuberer, und den muesste man richtig
 * einstellen.
 *
 * Unterstuetzt wird, was Antworten tatsaechlich benutzen: Ueberschriften, Absaetze,
 * Aufzaehlungen (auch verschachtelt), numerierte Listen, Codebloecke, Zitate,
 * Trennlinien, Tabellen sowie **fett**, *kursiv*, `code` und Links. Alles andere bleibt
 * einfach als Text stehen - lieber ein Sternchen zu sehen als eine falsche Darstellung.
 */
(function (global) {
    "use strict";

    // --- Zeichen innerhalb einer Zeile -------------------------------------

    // Reihenfolge ist wichtig: `code` zuerst, damit Sternchen INNERHALB von Code nicht
    // als Auszeichnung gelesen werden.
    const INLINE = [
        { re: /`([^`]+)`/, bau: function (m) {
            const el = document.createElement("code");
            el.textContent = m[1];
            return el;
        } },
        { re: /\*\*([^*]+)\*\*/, bau: function (m) {
            const el = document.createElement("strong");
            el.textContent = m[1];
            return el;
        } },
        { re: /__([^_]+)__/, bau: function (m) {
            const el = document.createElement("strong");
            el.textContent = m[1];
            return el;
        } },
        { re: /(?<![*\w])\*([^*\n]+)\*(?!\*)/, bau: function (m) {
            const el = document.createElement("em");
            el.textContent = m[1];
            return el;
        } },
        { re: /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/, bau: function (m) {
            const el = document.createElement("a");
            el.textContent = m[1];
            el.href = m[2];
            el.target = "_blank";
            el.rel = "noopener noreferrer";
            return el;
        } },
    ];

    /* Eine Zeile in Textknoten und Auszeichnungen zerlegen.
     *
     * Sucht jeweils den FRUEHESTEN Treffer aller Muster und arbeitet sich von links nach
     * rechts durch. Dadurch ist die Reihenfolge der Muster nur bei gleichem Startpunkt
     * entscheidend, und verschachtelte Faelle brechen nicht die Zeile. */
    function inlineAnhaengen(ziel, text) {
        let rest = String(text == null ? "" : text);
        let schutz = 0;
        while (rest && schutz < 500) {
            schutz += 1;
            let bester = null;
            for (let i = 0; i < INLINE.length; i += 1) {
                const treffer = INLINE[i].re.exec(rest);
                if (!treffer) { continue; }
                if (!bester || treffer.index < bester.treffer.index) {
                    bester = { treffer: treffer, muster: INLINE[i] };
                }
            }
            if (!bester) { break; }
            const t = bester.treffer;
            if (t.index > 0) {
                ziel.appendChild(document.createTextNode(rest.slice(0, t.index)));
            }
            ziel.appendChild(bester.muster.bau(t));
            rest = rest.slice(t.index + t[0].length);
        }
        if (rest) {
            ziel.appendChild(document.createTextNode(rest));
        }
    }

    // --- Zeilenweise Struktur ----------------------------------------------

    const UEBERSCHRIFT = /^(#{1,6})\s+(.*)$/;
    const PUNKT = /^(\s*)[-*+]\s+(.*)$/;
    const NUMMER = /^(\s*)(\d{1,3})[.)]\s+(.*)$/;
    const ZITAT = /^>\s?(.*)$/;
    const TRENNER = /^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/;
    const CODE_ZAUN = /^\s*```(.*)$/;
    const TABELLE_TRENNER = /^\s*\|?[\s:|-]+\|[\s:|-]*$/;

    function zellen(zeile) {
        let roh = zeile.trim();
        if (roh.startsWith("|")) { roh = roh.slice(1); }
        if (roh.endsWith("|")) { roh = roh.slice(0, -1); }
        return roh.split("|").map(function (z) { return z.trim(); });
    }

    /* Markdown in ein DocumentFragment umsetzen. */
    function rendern(quelle) {
        const frag = document.createDocumentFragment();
        const zeilen = String(quelle == null ? "" : quelle).replace(/\r\n?/g, "\n")
            .split("\n");
        let i = 0;

        while (i < zeilen.length) {
            const zeile = zeilen[i];

            // Codeblock: alles dazwischen bleibt wortwoertlich stehen.
            const zaun = CODE_ZAUN.exec(zeile);
            if (zaun) {
                const inhalt = [];
                i += 1;
                while (i < zeilen.length && !CODE_ZAUN.test(zeilen[i])) {
                    inhalt.push(zeilen[i]);
                    i += 1;
                }
                i += 1;   // den schliessenden Zaun ueberspringen
                const pre = document.createElement("pre");
                pre.className = "md-code";
                const code = document.createElement("code");
                code.textContent = inhalt.join("\n");
                if (zaun[1] && zaun[1].trim()) {
                    code.dataset.sprache = zaun[1].trim();
                }
                pre.appendChild(code);
                frag.appendChild(pre);
                continue;
            }

            if (!zeile.trim()) { i += 1; continue; }

            if (TRENNER.test(zeile)) {
                frag.appendChild(document.createElement("hr"));
                i += 1;
                continue;
            }

            const ueber = UEBERSCHRIFT.exec(zeile);
            if (ueber) {
                // h1 im Fliesstext waere groesser als die Seitenueberschrift, deshalb um
                // zwei Stufen nach unten: # wird h3.
                const stufe = Math.min(6, ueber[1].length + 2);
                const el = document.createElement("h" + stufe);
                el.className = "md-h";
                inlineAnhaengen(el, ueber[2]);
                frag.appendChild(el);
                i += 1;
                continue;
            }

            if (ZITAT.test(zeile)) {
                const teile = [];
                while (i < zeilen.length && ZITAT.test(zeilen[i])) {
                    teile.push(ZITAT.exec(zeilen[i])[1]);
                    i += 1;
                }
                const bq = document.createElement("blockquote");
                bq.className = "md-zitat";
                inlineAnhaengen(bq, teile.join(" "));
                frag.appendChild(bq);
                continue;
            }

            // Tabelle: Kopfzeile, Trennzeile, dann Datenzeilen.
            if (zeile.indexOf("|") !== -1 && i + 1 < zeilen.length
                    && TABELLE_TRENNER.test(zeilen[i + 1])) {
                const tabelle = document.createElement("table");
                tabelle.className = "md-tabelle";
                const kopf = document.createElement("thead");
                const kopfzeile = document.createElement("tr");
                zellen(zeile).forEach(function (text) {
                    const th = document.createElement("th");
                    inlineAnhaengen(th, text);
                    kopfzeile.appendChild(th);
                });
                kopf.appendChild(kopfzeile);
                tabelle.appendChild(kopf);
                i += 2;
                const koerper = document.createElement("tbody");
                while (i < zeilen.length && zeilen[i].indexOf("|") !== -1
                        && zeilen[i].trim()) {
                    const tr = document.createElement("tr");
                    zellen(zeilen[i]).forEach(function (text) {
                        const td = document.createElement("td");
                        inlineAnhaengen(td, text);
                        tr.appendChild(td);
                    });
                    koerper.appendChild(tr);
                    i += 1;
                }
                tabelle.appendChild(koerper);
                frag.appendChild(tabelle);
                continue;
            }

            if (PUNKT.test(zeile) || NUMMER.test(zeile)) {
                const ergebnis = listeBauen(zeilen, i);
                frag.appendChild(ergebnis.el);
                i = ergebnis.naechste;
                continue;
            }

            // Absatz: zusammenhaengende Zeilen gehoeren zusammen.
            const absatzzeilen = [];
            while (i < zeilen.length && zeilen[i].trim()
                    && !UEBERSCHRIFT.test(zeilen[i]) && !PUNKT.test(zeilen[i])
                    && !NUMMER.test(zeilen[i]) && !ZITAT.test(zeilen[i])
                    && !TRENNER.test(zeilen[i]) && !CODE_ZAUN.test(zeilen[i])) {
                absatzzeilen.push(zeilen[i].trim());
                i += 1;
            }
            const p = document.createElement("p");
            p.className = "md-p";
            inlineAnhaengen(p, absatzzeilen.join(" "));
            frag.appendChild(p);
        }

        return frag;
    }

    /* Eine Liste ab Zeile `start` bauen, verschachtelt nach Einrueckung. */
    function listeBauen(zeilen, start) {
        const ersterPunkt = PUNKT.exec(zeilen[start]);
        const ersteNummer = NUMMER.exec(zeilen[start]);
        const numeriert = !ersterPunkt && !!ersteNummer;
        const basis = (ersterPunkt ? ersterPunkt[1] : ersteNummer[1]).length;

        const liste = document.createElement(numeriert ? "ol" : "ul");
        liste.className = "md-liste";
        let i = start;

        while (i < zeilen.length) {
            const punkt = PUNKT.exec(zeilen[i]);
            const nummer = NUMMER.exec(zeilen[i]);
            if (!punkt && !nummer) {
                // Eine eingerueckte Fortsetzungszeile gehoert zum letzten Punkt.
                if (zeilen[i].trim() && /^\s{2,}/.test(zeilen[i]) && liste.lastChild) {
                    liste.lastChild.appendChild(document.createTextNode(" "));
                    inlineAnhaengen(liste.lastChild, zeilen[i].trim());
                    i += 1;
                    continue;
                }
                break;
            }
            const tiefe = (punkt ? punkt[1] : nummer[1]).length;
            if (tiefe > basis) {
                const unter = listeBauen(zeilen, i);
                (liste.lastChild || liste).appendChild(unter.el);
                i = unter.naechste;
                continue;
            }
            if (tiefe < basis) { break; }

            const li = document.createElement("li");
            inlineAnhaengen(li, punkt ? punkt[2] : nummer[3]);
            liste.appendChild(li);
            i += 1;
        }

        return { el: liste, naechste: i };
    }

    /* Markdown in ein Element schreiben und dessen bisherigen Inhalt ersetzen. */
    function inElement(ziel, quelle) {
        while (ziel.firstChild) { ziel.removeChild(ziel.firstChild); }
        ziel.appendChild(rendern(quelle));
        return ziel;
    }

    global.MdDarsteller = { rendern: rendern, inElement: inElement };
})(window);
