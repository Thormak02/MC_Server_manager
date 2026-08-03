# Datapacks & Resource Packs (+ Vanilla Tweaks) — Umsetzungsplan & Prompts

Ziel: Datapacks und Resource/Texture Packs verwalten — über **Modrinth**
(primär) und **CurseForge** (sekundär), plus eine dedizierte **Vanilla
Tweaks**-Integration (Kategorie-Auswahl **und** Share-Code). Resource Packs
werden am Server über `server.properties` (`resource-pack`-URL + sha1) gesetzt.

Baut auf dem bestehenden, bereits generischen Content-System auf
(`content_service` ist über `content_type` parametrisiert; Suche/Install/
Versionslisten/Bulk-Update funktionieren typ-übergreifend).

## Verifizierte Fakten (Stand der Recherche)
- **Modrinth**: native Projekttypen `datapack` und `resourcepack` (unser
  `_modrinth_project_types_for_content_type` muss sie nur mappen).
- **CurseForge (gameId 432) Klassen** (live geprüft): Data Packs = **6945**,
  Resource Packs = **12**. Wie Plugins haben sie **keinen** `modLoaderType` →
  Loader-Filter muss `None` sein (sonst 0 Treffer, s. Plugin-Fix).
- **Ordner/Anwendung**:
  - Datapacks → `<server>/<level-name>/datapacks/` (Weltname aus
    `server.properties`, Default `world`). Aktiv nach `/reload` bzw. Neustart.
  - Resource Packs → **kein Ordner**; `server.properties`
    `resource-pack=<URL>` + `resource-pack-sha1=<sha1>` (+ optional
    `require-resource-pack`). Clients laden von der URL.
- **Vanilla Tweaks** (inoffizielle, stabile API, u.a. von itzg genutzt):
  - Kategorien: `GET vanillatweaks.net/assets/resources/json/{version}/dpcategories.json`
    (analog `rpcategories.json`, `ctcategories.json`).
  - Generieren: `POST vanillatweaks.net/assets/server/zipdatapacks.php`
    (Body `packs=<JSON {kategorie:[namen]}>&version=<x.y>`) → JSON mit `.link`
    (relativer Download-Pfad). Analog `zipresourcepacks.php`.
  - Share-Code auflösen (Selektion aus Code) — Referenz:
    github.com/OmerMakesStuff/vanillatweaks-stuff.
  - VT-Download-Links sind ggf. **temporär** → für Resource Packs muss der
    Manager das ZIP **selbst hosten** (stabile Client-URL).

## Externe Voraussetzungen
- **Resource Packs generell**: die `resource-pack`-URL muss von **Clients**
  erreichbar sein. Modrinth/CF liefern öffentliche CDN-URLs → direkt nutzbar.
- **Vanilla-Tweaks-Resource-Packs**: Manager-HTTP muss **öffentlich erreichbar**
  sein (z.B. via `thormakmc.de`/Reverse-Proxy), damit das selbst-gehostete Pack
  eine stabile URL hat (passt zum Domain-Plan).
- Hinweis-Risiko: die VT-API ist **inoffiziell** und kann sich ändern.

---

## Phase 0 — Grundlagen: Content-Typen erweitern
**Externe To-dos:** keine.

> Implementiere **Phase 0**: erweitere das Content-System um die Typen
> `datapack` und `resourcepack` (Backend + Basis-UI), ohne die
> Resource-Pack-Sonderlogik (kommt in Phase 2).
> - `content_service`: `_modrinth_project_types_for_content_type` → `datapack`
>   bzw. `resourcepack`. `_CURSEFORGE_CLASS_IDS` += `datapack:6945`,
>   `resourcepack:12`. `_curseforge_loader_type` → **None** für datapack/
>   resourcepack (kein `modLoaderType`, analog Plugin-Fix). Ggf. Sort-/
>   Filter-Helfer prüfen.
> - Zielordner: Helfer `_server_world_dir(server)` (liest `level-name` aus
>   `server.properties`, Default `world`); `_target_dir` → für `datapack`
>   `<welt>/datapacks`. (resourcepack-Ablage kommt in Phase 2.)
> - `_default_content_type`/UI: Typ-Dropdown in der Mods-Seite um „Datapack"
>   und „Resourcepack" erweitern (für alle Servertypen verfügbar).
> - Tests: Modrinth-/CF-Suche und Datapack-Zielordner.
> - Volle Suite grün.

**Akzeptanz:** Datapack-Suche liefert Treffer (Modrinth+CF); Datapack-Install
landet im Welt-`datapacks`-Ordner; Suite grün.

## Phase 1 — Datapacks vollständig
**Externe To-dos:** keine.

> Implementiere **Phase 1**: Datapacks über Modrinth/CurseForge vollständig
> (Suche, Versionsauswahl, Install, „Neueste installieren", Bulk-Update,
> Löschen). Ziel-/Ablage im Welt-`datapacks`-Ordner; nach Install Hinweis
> „/reload oder Neustart nötig". Abhängigkeiten sind bei Datapacks i.d.R. keine.
> Tests für Install/Update/Löschen. Volle Suite grün.

**Akzeptanz:** Datapack-Lebenszyklus komplett über die UI; Suite grün.

## Phase 2 — Resource Packs via `server.properties`
**Externe To-dos:** sicherstellen, dass Clients die Provider-URL erreichen
(bei Modrinth/CF automatisch gegeben).

> Implementiere **Phase 2**: Resource/Texture Packs für den Server.
> - **Install-Pfad resourcepack** (Sonderweg statt Datei-Ablage): setze in
>   `server.properties` `resource-pack=<Download-URL>` und
>   `resource-pack-sha1=<sha1>` (+ optional `require-resource-pack`).
> - **sha1**: Modrinth-Version liefert `files[].hashes.sha1` → ohne Download.
>   CurseForge → Datei laden + sha1 berechnen.
> - **Installierte Ansicht**: der aktuell gesetzte Server-Resource-Pack (genau
>   einer über server.properties); „Entfernen" leert die `resource-pack`-Zeile.
> - Optional-Toggle „require-resource-pack".
> - Tests (sha1-Weg gemockt; server.properties-Schreiben).
> - Volle Suite grün.

**Akzeptanz:** Resource-Pack aus Modrinth/CF setzt korrekt `resource-pack` +
sha1; Entfernen leert es; Suite grün.

## Phase 3 — Vanilla Tweaks (Generator + Share-Code)
**Externe To-dos:** für VT-**Resource-Packs** muss die Manager-URL öffentlich
sein (Selbst-Hosting des generierten ZIP). Für VT-**Datapacks** nicht nötig.

> Implementiere **Phase 3**: dedizierte Vanilla-Tweaks-Integration.
> - Neues `vanillatweaks_service`:
>   - Kategorien laden (`{dp,rp,ct}categories.json` je MC-Version).
>   - Generieren via `POST .../zip{datapacks,resourcepacks}.php`
>     (`packs`+`version`) → Download-Link; ZIP holen.
>   - **Share-Code** auflösen → Selektion → generieren (Referenz:
>     vanillatweaks-stuff).
> - **Datapacks**: generiertes ZIP → in `<welt>/datapacks` ablegen/entpacken.
> - **Resource Packs**: generiertes ZIP **selbst hosten** (Manager serviert es
>   unter stabiler URL) → `resource-pack`-URL + sha1 setzen (VT-Link temporär).
> - **UI**: (a) Share-Code-Feld (einfacher Weg) **und** (b) Kategorie-Picker
>   (Auswahl je Kategorie, Inkompatibilitäten beachten, Version). „Beides".
> - Tests (VT-Antworten gemockt: Kategorien, Generieren, Share-Code).
> - Volle Suite grün.

**Akzeptanz:** VT-Datapack per Share-Code UND per Kategorie-Auswahl landet im
Welt-`datapacks`-Ordner; VT-Resource-Pack wird gehostet + in server.properties
gesetzt; Suite grün.

## Phase 4 — UI-Feinschliff & Doku
**Externe To-dos:** keine.

> Implementiere **Phase 4**: UI-Feinschliff + Doku.
> - Klare Typ-Umschaltung (Mod/Plugin/Datapack/Resourcepack) bzw. eigener
>   Bereich; „Installierte"-Übersicht je Typ (Datapacks als Dateien,
>   Resource-Pack als aktuell gesetzter Server-Pack, VT-Auswahl merkbar).
> - VT-Picker aufräumen (Kategorien, Inkompatibilitäts-Hinweise, Version).
> - README/Doku: Ordner, server.properties, öffentliche URL für VT-RP,
>   /reload-Hinweis. Verifikations-Checkliste.
> - Volle Suite grün.

**Akzeptanz:** stimmige UI je Typ; Doku vollständig.

---

## Aufwandsschätzung (grob)
- Phase 0: **S–M** · Phase 1: **M** · Phase 2: **M** · Phase 3: **M–L** ·
  Phase 4: **M**. Gesamt ~mehrere fokussierte Sessions.

## Externe Kurz-Checkliste (Go-Live)
- [ ] Resource-Pack-URLs von Clients erreichbar (Modrinth/CF: automatisch)
- [ ] Für VT-Resource-Packs: Manager-HTTP öffentlich (Domain/Reverse-Proxy)
- [ ] `/reload` bzw. Neustart nach Datapack-Änderungen eingeplant
