~~super, bitte erstelle nun phase 2 des zweiten pflichtenhefts.~~

~~die API keys werde ich manuell in die .env eintragen, erstelle dort platzhalter, auf die der code dann zugreift. dies betrifft nur private APIs, öffentliche können weiterhin normal im code stehen.~~







~~das starten nach der Erstellung funktioniert nicht, aber das starten eines manuell erstellten Servers, der dann improtiert wurde funktioniert. überprüfe also bitte nochmal den servererstellungsprozess.~~





~~die jeweilige forge / fabric usw. Version soll unter Server details geändert werden können zb. um updates durchzuführen. außerdem soll es moglich sein zb. nach Releases / betas usw. zu filtern. selbes für mods und modversionen.~~





~~wenn kein verzeichnis bei der erstellung eines servers angegeben wird, soll ein standardverzeichnis genutzt werden. dieses soll beim erstmaligen starten der anwendung auf dem desktop erstellt werden. der pfad soll in der .env und den einstellungen im ui angepasst werden können. in dem server verzeichnis soll für jeden erstellten server jeweils ein unterverzeichnis erstellt werden. außerdem, wenn ich einen Server inklusive ordner lösche und dann wieder einen Server mit dem selben namen erstelle, werden die mods noch als installiert angezeigt, obwohl sie nicht mehr existieren, also die db einträge sind vermutlich nicht richtig gelöscht.~~







~~super, bitte erstelle nun Phase 3 (Uploads/Downloads, erweiterte Logs/Audit).~~





~~setze ich als Nächstes direkt Phase 6 (Modpack-Import + Komfortfunktionen) um~~





~~der modpackimport ist so falsch, es ist nicht wie modinstallation gedacht, sondern als neuer Server. man soll also einen Server aus einem modpack erstellen können.~~





~~die modpack suche funktioniert nur selektiv, manche werden korrekt gefunden, andere wie das atm10 Beispiel werden nicht gefunden, oder erst weiter unten. ähnlich ist es bei den mods.~~







~~die suche (mods sowie modpacks) soll jetzt noch smarter werden. sie soll aussehen / funktionieren wie die suche in der curseforge app. also mod Vorschau, wie bei curseforge, bild, kurze Beschreibung, download zahlen, usw. Hyperlink zur jeweiligen modseite bei curseforge / modrinth.~~

~~die suche soll funktionieren, wie in der curseforge app (siehe bilder) also mit Filter und sortierung zb. nach relevanz, downloads, beliebtheit usw. wenn man beispielsweise keinen suchbergiff eingibt, sollen die belibtesten bzw. mit den meisten downloads je nach sortierung und Kategorie angezeigt werden. man soll auch mehrere Kategorien auswählen können. Die suche soll sofort bei eingebe starten, nicht erst mit klick auf suche. das soll für modpacks sowie mods funktionieren.~~





~~bitte die Kategorien als Dropdown mit Checkbox Auswahl, Kategorien sollen auch wieder abwählbar sein, bei modrinth ist es falsch (siehe screenshot). die projektseite soll man mit klick auf den namen aufrufen, also per Hyperlink und nicht per Feld daneben. die seite soll sich immer im neuen tab öffnen. bei der suche soll es die Option beim runterscrollen mehr Inhalte zu laden, dafür muss die Inhalte liste mit den suchergebnissen separat / unabhängig von dem rest scrollen lassen. Import Preview reicht anzuzeigen, dass es keine Warnungen gibt, oder nur die Fehler sollte es welche geben, so ist es zu viel kryptischer bloat, wenn alle mods usw. aufgelistet werden (siehe bild).~~

~~MC Version (optional) nd Loader (optional) lieber auch als Dropdown Auswahl, wie bei Kategorien auch mit mehrfach Auswahl und Checkbox und abwählbar.~~





~~es sollte bei curseforge modpack import nur eines benötigt werden, also modpack code, zip oder url. eins sollte reichen, es tut nicht not, dass alle drei mandatory fields sind.~~



~~curseforge modpack Import über modpack code / Export zip (weiter ausarbeiten)?~~





~~curseforge soll als Standard ausgewählt sein, nicht modrinth~~





~~modpack Version soll änderbar sein (darauf achten, dass alte Inhalte korrekt gelöscht werden und korrekt durch neue ersetzt werden) um auf updates zu prüfen und diese durchzuführen~~





**<i>~~bitte überprüfe nochmal beide pflichtenhefte, ob alle anforderungen umgesetzt wurden.~~</i>**

**<i>~~(hier copy paste beide pflichtenhefte)~~</i>**





~~in Details sollen op, banns, whitelist oben als eigener reiter sein, nicht nur (aber auch) unter Dateien. außerdem mit Assistent, wo man nur den namen eingeben muss. soll auch funktionieren, wenn die jeweilige Datei leer ist (das jeweilige Schema muss bei einem eintrag korrekt angelegt werden). unter whitelist reiter soll diese auch aktivierbar und deaktivierbar sein, am besten mit einem switch / toggle button. in den config Dateien sollen wirklich alle Felder per Assistent eingegeben werden können (sofern in assistent ansicht), nicht nur einige wenige (wie aktuell) es sollen alle einstellungen automatisch erkannt werden.~~





~~es soll möglich sein, die minecraft Version eines bestehenden Servers zu ändern, um zb. updates durchzuführen.~~

~~dies soll nur für vanilla / plugin server funktionieren. nicht für modded / modpacks~~





~~da man den modloader nicht ändern kann, reicht es, wenn die modsuche ebenfalls auf den jeweiligen modloader beschränkt ist und nur zu dem Server passende mods angezeigt werden und installiert werden können.~~







~~wenn eine neue minecraft Version released wird soll diese automatisch im Manager verfügbar sein, ohne sie manuell hinzufügen zu müssen. ist dies eventuell bereits umgesetzt? selbes auch für modloader wie zb. forge.~~







~~bitte prüfe nochmal alle seiten im gui, es gibt noch einige visuelle Bugs / es sind noch einige Felder verrutscht.~~



~~Das UI bitte auch generell aufräumen, proportionen, positionen usw.~~

~~die suche soll als hauptfunktion präsenter sein und nich wie ein weiterer filter nur nebensächlich wirken.~~







~~für Plugins wird noch bukkti Unterstützung benötigt.~~



~~mod dependicies sollen automatisch mit installiert werden, wenn eine mod installiert wird~~





~~die ram einstellung soll nicht mehr über ein freitext eingabe feld funktionieren, sondern als min max slider mit sinnvollen inkrementen mit maximal 64 GB~~



~~es ist gestartet, aber bei klick auf update check folgender Fehler:~~

~~Fetch fehlgeschlagen: fatal: 'origin' does not appear to be a git repository fatal: Could not read from remote repository. Please make sure you have the correct access rights and the repository exists.~~





~~updates push auf dev pc zu auto (oder manuell) download / update auf server pc~~





~~wenn bei port nichts eingetragen wurde automatisch einen freien festlegen, nicht einfach nur Standard port, nicht 5000 da läuft der Manager, sondern ein port, der noch nicht belegt ist.~~


super, dann jetzt bitte die portale. nicht einfach so portale platzieren, sondern das system liefern um diese als operator / builder manuell zu setzen


templates nicht nur für server erstellung, auch für datein wie server.properties, standart template festlegen (evtl. nach servertyp)



~~neoforge Integration~~



localisation (englisch), später



~~Server löschbestätigung als popup~~



~~Lobby Server unter selben port wie manager, von hier aus soll man auch server starten können.~~
welt ist eine struktur, die sich automatisch pro server erweitert. ist das möglich? siehe docs/lobby_gateway_plan.md


SSH zugriff?


mehrere verwaltungsumgebungen, die voneinander getrennt sind, server 1, 2 und 3 in umgebung A und server 4, 5 und 6 in umgebung B


~~Server umbenennen können~~


~~ein button um alle mods auf aktuellste Version zu updaten (im mods bereich, auch für plugins usw.), außerdem beim download automatisch aktuellste version ausgewählt. abhängigkeiten werden automatisch mit installiert. nicht für modpacks, wo oft eine spezifische version gefordert ist.~~





~~alle Server so einstellbar sein, sodass sie mit start des Managers starten~~



modpack Version im Dashboard anzeigen, wie mc Version



~~spielerzahl auch bei Servereinstellungen anpassen können~~





~~auf Sicherheitslücken prüfen~~



~~backup auf nas als netzwerklaufwerk~~





~~Plugins funktionieren nicht ganz, nochmal schauen, was genau~~



~~schedule Kalender funktioniert nicht, nochmal prüfen, was genau nicht geht~~


~~Spielerkopf als icon bei namen in onlineliste, op, white und bannliste usw. anzeigen~~


~~Neustart geht nicht~~

~~Thormak2002 UUID: 69f230e1-af2c-4d0e-9885-93d66595a855 immer standardmäßig als level 4 operator festlegen, unter einstellungen managerweite whitelist, op list und bann list~~


~~es soll einstellbar sein, dass wen kein spieler auf dem server ist, das der server dann in einen standby geht, es vergeht keine zeit, keine ticks, keine geladenen chunks, der server soll also praktisch komplett heruntergefahren sein, bis sich wieder ein spieler verbinden möchte. also spieler login: Server startet - spieler disconnect und kein weiterer spieler auf server: server shutdown / sleep. das ganz muss wie gesagt togglebar sein und nur explizit auf wunsch eingestellt werden können. shutdown nach letztem disconnect soll je server verzögerbar sein, also zb. erst nach 5 minuten shutdown~~


 ~~Sleep-Verzögerung (Sek.) nicht nur in sek, sondern zb. auch minuten, stunden, tage auswählbar~~

~~manueller sleep button innerhalb des dashboards neben start, stopp und neustart, sowie in der detail ansicht~~

"City build" und minigames Server netzwerk, mit mehreren welten und selben invnetar usw. also zb. bau welt minenwelt usw. mit warp, shopsystem, jobs, geld usw. bungee cord, einfacher editor um server netzwerke zu bearbeiten, anderes system als lobby server

konsole überarbeiten, sodass sie praktisch wie ein cmd fenster funktioniert, in das man direkt schreiben und mit enter absenden kann, ohne das befehl senden feld darunter

bei manuellem backup: fortschrittsbalken, statt langes laden des tabs


~~die Einstellungen sind inzwischen zu unübersichtlich, bitte ebenfalls sinnvolle Tabs zur sortierung erstellen, wie bei der Server detailansicht~~


~~schlafender server wird in lobby nicht als solcher angezeigt und von einem beitrittsversuch über lobby nicht geweckt~~


~~es soll auch möglich sein, von einem server mit /lobby wieder zur lobby zu gelangen~~

~~alte plugins erkennen / updaten~~



die verwaltung des hub / lobby server soll wie die eines normalen servers mit untermenus usw. funktionieren und auch genauso aussehen, um mehr kontiniuität zu schaffen


~~logs und db live / deutlich öfter, als nur alle paar minuten plus alte löschen um speicher zu sparen~~



~~welten zwischen servern kopierbar zb. welt von david 1.21.11 spigot server nach neuen 26.1 server kopieren~~

KI Ideen:

Monitoring & Alarme

Verlaufs-Metriken: CPU/RAM/Spielerzahl/TPS als Zeitreihe speichern und als Graphen anzeigen (Trends statt nur Live-Werte).

TPS/MSPT-Überwachung (Tick-Performance) mit Lag-Warnung; automatischer Performance-Report bei anhaltendem Lag.

Crash-Analyse: neuesten crash-report automatisch erfassen, Kernfehler extrahieren und im UI anzeigen.

Benachrichtigungen (Discord/Webhook/E-Mail/eigene push nachrichgt auf handy) bei Crash, Start/Stopp, Fehler-Häufung, Backup-Ergebnis.


Spieler & Community

Spieler-Statistiken: Spielzeit, Sessions, erste/letzte Anmeldung, aktivste Spieler (Historie) (Server spezifisch, sowie übergreifend).

Schnellaktionen pro Spieler im UI: kicken, anschreiben, Gamemode/Teleport; Skin/Avatar in der Online-Liste.


Welten & Inhalte

Welten-Manager: mehrere Welten je Server, umschalten, einzelne Welt sichern/hoch-/runterladen, Seed anzeigen. später evtl.

~~Datapack-Verwaltung (installieren/aktivieren/deaktivieren), analog zu Mods.~~

~~Resourcepack-Hosting: Server-Resourcepack automatisch bereitstellen und resource-pack-URL setzen, ebenfalls über api, analog zu mods.~~

Chunk-Vorgenerierung (z.B. via Chunky) zur Lag-Reduktion eher nein, wenn nur on demand.


Backups (erweitert)

Inkrementelle Backups (nur geänderte Dateien) + Aufbewahrungsregeln (N täglich / M wöchentlich).

Off-site/Cloud-Backup (S3/Backblaze) zusätzlich zur lokalen Sicherung.

Integritätsprüfung (Checksummen) + Diff-Ansicht zwischen zwei Backups.


Automatisierung

Regel-Engine (event-getrieben): „wenn Log-Muster X → Befehl/Neustart/Benachrichtigung" (z.B. Auto-Neustart bei OutOfMemory, Auto-Kick bei Spam).

Ankündigungs-Scheduler: geplante Broadcasts / rotierende MOTD.

Aikar's-Flags-Preset: optimierte JVM/GC-Flags je RAM automatisch setzen.


Sicherheit & Zugriff

2FA (TOTP) für den Manager-Login. eher nein, wenn nur später

API-Tokens + dokumentierte REST-API für externe Automatisierung. sowieso nur über .env

RCON-Unterstützung (echtes Protokoll) als robuster Konsolenkanal, auch für extern gestartete Server.

Tunnel-Integration (playit.gg/ngrok) bzw. UPnP-Portöffnung, um ohne Portfreigabe erreichbar zu sein. lieber über eigene ionos domain "thormakmc.de"





Komfort/UX

Server-Tags/Gruppen + Favoriten, Sortierung/Filter im Dashboard.

Globale Suche über Server/Dateien/Logs.

PWA / mobil-optimierte Ansicht.

Onboarding-Assistent beim ersten Start.

eigene Domänen je Server und je Umgebung (Standard bleibt vorerst mc.friedrich-dietrich.de):

- Heute: EINE Domäne (network_domain), Server tragen nur ein Label (gateway_hostname), "Standard" gilt global.
- Zu bauen: Domäne als eigene Entität - Server UND Umgebung je eine, auch mit eigener Subdomain; längste passende Domäne abschneiden, Eindeutigkeit über (Domäne, Label), "Standard" je Domäne.
- DNS: alle Domänen auf dieselbe IP, A-Record genügt bei Gateway auf 25565, sonst SRV (_minecraft._tcp.<name>).
- Zieht mit: public_base_url muss aus DERSELBEN Domäne kommen (Resource-Pack-URL), je Domäne ein Zertifikat nur für Web (Minecraft spricht kein TLS), Velocity-forced-hosts sobald ein Unterproxy dazukommt.
- hängt an "getrennte Verwaltungsumgebungen" (Domäne je Orga) und "Server-Vermietung" (URL-Schema)


getrennte Verwaltungsumgebungen - zuerst für die eigenen Server mit Helfern, als Grundlage für späteres Hosting:

- Nutzer/Orga-Modell: ein Nutzer verwaltet und delegiert mehrere Orgas, eine Orga hat mehrere Nutzer; Rollen je Orga, Super-Admin sieht immer alles.
- Umgebung (Servergruppe) und Kunde (Vertrag, Rechnung, Kontingent) getrennt modellieren.
- Billig: server.umgebung_id + Mitglieder mit Rollen. Teuer: die ~30 globalen Einstellungen je Umgebung umhängen - erst nötig beim zweiten echten Netz.
- Auslöser für den Bau: der erste Helfer, der nicht alles sehen soll.
- Spieldaten: eine Datenbank je Netz, keine Mandantenspalten.
- Bei Fremdkunden: Auftragsverarbeiter, Zugriff nur auf Weisung und protokolliert.
- hängt an "rechte verwaltung" und "Server-Vermietung"

später: monetarisierung: server, orgas usw. vermieten, kosten nach kapazität, leistung und anzahl an orgas und server



Ideen aus dem Urlaub (09/2026), für viel später:

~~Graceful Rejection: Vorab-Prüfung vor dem Wechsel, eine Wahrheitsquelle für beide Lobbys, fail-open.~~

~~Modpack-Abgleich vor dem Wechsel.~~ Weiterhin offen: Resourcepack-Abgleich, der allererste Join und eine Ablehnung erst nach dem Transfer - dafür bräuchte es einen echten Proxy.

Mobile Verwaltung: MCSM auch als App-Version + mobile Web-App zur Verwaltung. Selbes Backend, immer selber Stand wie die Web-Version. Außerdem die bestehende Website für mobile Browser optimieren. Dünne native Hülle (PWA/Wrapper), die selbst nicht aktualisiert werden muss; erweitert "PWA / mobil-optimierte Ansicht".

Server-Vermietung: den Manager als Basis für Server-Vermietung schaffen - z.B. Bezahlmodelle, RAM- und Speicherzuweisung, Selbstverwaltung usw. URL-Schema: nutzername.servername.domain.de/... oder nutzername.domain.de/servername. Setzt die getrennten Umgebungen voraus - damit aber erst zu einem Zehntel: Kontingente, Selbstbedienung, Abrechnung, Verfügbarkeitszusagen und Support fehlen.


live übersicht über map, spieler position, gebäude, inventar usw. Dynmap? interaktiv aus der vogelperspektive, pro server zuschaltbar und nach auswahl über link öffentlich einsehbar


rechte verwaltung: nutzern und nutzergruppen je per checkbox berechtigungen zuweisen - frei anpassbar, je Rolle UND je Umgebung. Ersetzt die heutige feste Rollenliste und die Serverrechte durch EINE Stelle. Teil der getrennten Umgebungen.


push benachrichtigungen auf handy und pc zb. bei crash


ai analysen für crashes usw

ai modpack / datapack / pluginpack / server generator und ersteller / modpack ersteller (modpack und quests und custom rezepte usw.) mit claude API -> curseforge code / link für installation


Automatisches löschen alter backups. komprimieren? zu viel speicherplatz nötig.



~~mir ist außerdem grade aufgefallen: es funktionieren nur vanilla tweaks, aber vanilla tweaks crafting rezepte werden nicht erkannt und nicht zur auswahl verfügbar~~

~~/lobby / /server lobby geht nicht~~


manager kann auch server pc restarten

~~zb. spigot oder andere pluginloader configs bearbeitbar machen~~

KI-Assistent: ausführen soll automatisch die weiteraufforderung senden, wenn der befehl fertig ausgeführt ist

assistent sollte bilder und links einlesen können

assistent limit ist zu gering um wirklich praktisch zu sein, siehe 26.3 paper lag analyse chat und logs dazu

Deinem Assistenten fehlt eine wichtige Funktion: Die letzten Konsolenzeilen gezielt abrufen, insbesondere die Ausgabe eines gerade ausgeführten Befehls.


MOTD editor in Server einstellungen mit Farben, allen optionen usw. mit einer vorschau

eigener minecraft launcher mit manager server integriert mit curseforge und modrinth integration

citibuild: wenn server neustart oder offline warte raum mit automatischen reconnect und evtl. kleinen minigames wie jump and runs

~~KI-gestützte Funktionen nur ondemand, local ai ohne externe api?~~
~~Beantwortet: externe Claude-API gesetzt, lokale Modelle nur als Rückfall.~~
~~Rate Limit hinzufügen + missbrauch verhinden durch Themen lockin~~
~~API Anbieter einfach änderbar~~
~~API Keys nur in .env und die .env soll durch ein menufenster beschrieben werden, wie zb. bei der CurseForge API~~

~~Fundament: Schlüssel verschlüsselt im Benutzerprofil, NICHT in der .env - an Rechner und Konto gebunden. Monatsdeckel und Limits, ab Moderator.~~

~~KI-Log-/Crash-Analyse: Fehlerursache erklären + konkrete Lösungsvorschläge (welche Mod, welcher Fix).~~

~~KI-Konfigurationsassistent: server.properties/JVM anhand Ziel (RAM, Spielerzahl, Modpack) optimieren.~~
Offen: KI-Vorschläge für YAML.

~~KI-Chat-Moderation: In-Game-Chat auf Toxizität/Spam prüfen, automatische Warnungen. -> nicht gewünscht~~

~~KI-Changelog-Zusammenfassung vor einem Mod-Update („Was ändert sich, gibt es Breaking Changes?").~~

~~Natürlichsprachliche Steuerung: „starte alle Vanilla-Server", „sichere Server X" → Aktionen.~~
Offen: mehrere Server in einem Auftrag - bisher eine Aktion je Bestätigung.

~~eine KI integration, die fragen beantworten kann und auch cowork mößig aufgaben durchführen kann, nicht nur vorgefertigte commands, sondern freie bearbeitung und freies prompting wie claude code cowork auf dem rechner. aufgaben wie brarbeite diese config datei oder tue etwas in den einstellungen usw. rechte dürfen dadurch nicht umgehbar sein~~


~~die KI soll auch zugriff auf die ordnerstruktur, die datein und logs haben. allerdings nur innerhalb des managers, nicht darüber hinaus, allerdings muss hierbei die sicherheit beachtet werden, dass nutzer zb. keine schadsoftware einführen können oder informationen erhalten können, die sie nicht erhalten dürfen~~

~~Einrichtung, steht: bei online-mode=false hinter Velocity muss der Serverport von außen gesperrt sein; pause-when-empty-seconds schaltet der Manager bei jedem Start ab.~~

Offen: Testisolation - Tests fragen die echte Entwicklungs-DB ab; betroffen sind mindestens 15 Dienste, die dieselbe Modulreferenz halten.

~~Assistent für spigot.yml, bukkit.yml, paper-*.yml und Plugin-Configs.~~

Offen: NAS-Abgleich steht seit 6.10., Task mcsm-nas-sync löst nicht aus.

~~Zwei .bat-Dateien zum Starten/Stoppen des Managers (scripts/), fuer Desktop-Verknuepfungen.~~

Offen: Absturzschleife des Proxys gehoert aufs Dashboard, nicht nur ins Pruefprotokoll.

Offen: Via-Plugins bleiben ohne Hinweis alt, wenn es fuer die neue Version keinen Build gibt.

Offen: warum Velocity die "Connect"-Nachricht verwirft, ist ungeklaert; der Rueckfall traegt nur.

~~MOTD-Editor in den Servereinstellungen: Farben, Formate, Live-Vorschau.~~

~~Zweite Anmeldung desselben Spielers: ersetzen oder abweisen (Netzwerk & Lobby, Vorgabe ersetzen).~~

~~Offen: drei Lesefunktionen für den Weltnamen geben drei verschiedene Antworten, eine wirft bei Latin-1 - auf den einen Properties-Leser zusammenziehen.~~ 23.11. zusammengezogen: `lobby_service._level_name` und `content_service._server_level_name` rufen `weltname_aus_ordner`, und das liest über `properties_service`. Damit gilt auch der Riegel gegen `../woanders` - er fehlte an der Stelle, die löscht (`deploy_lobby_world` baut `base / level_name` und ruft darauf `shutil.rmtree`).

Offen: vierzehn eigene Zeilenzerleger für `key=value`-Dateien stehen noch - ai_agent_service, ai_config_service (zwei), file_service (drei), freimarken_einrichtung_service, hub_capture_service, modpack_service, process_service (zwei), server_import_service, server_service, `transfer_service._kopiere_properties`. Eine Probe zählt sie; ein fünfzehnter wird rot. (Hier stand am 23.11. "acht": Das Muster verlangte den Dateinamen im Rumpf derselben Funktion und sah die vier nicht, die ihren Text als Parameter bekommen.)

~~Offen: `_apply_lobby_properties` schreibt eine Latin-1-Datei als UTF-8 zurück und wechselt damit ihre Kodierung.~~ 23.11. berichtigt und behoben: Dazu kam es nie - das LESEN warf vorher (`read_text(encoding="utf-8")`, gefangen nur `OSError`, `UnicodeDecodeError` ist ein `ValueError`), und zwar bis in den Router. Weil `_level_name` am selben Tag geheilt wurde, kam der Wurf danach erst NACH `rmtree` + `copytree`: alte Welt weg, neue da, Lobby-Keys nicht gesetzt, HTTP 500 ohne Meldung. Jetzt geht auch dieser Leser über `properties_service`, und eine Warnung landet in der Meldung (`ok=False`, die Welt IST getauscht).

Offen: `file_service._parse_properties` kennt Javas `:` nicht, und `_dump_properties` hängt unbekannte Zeilen HINTER die sortierten - eine `server.properties` mit `white-list:true` wird von `set_whitelist_enabled(False)` als `white-list=false` plus `white-list:true` zurückgeschrieben, und Java nimmt den letzten: Die Whitelist bleibt an, obwohl der Manager sie abgeschaltet meldet. Gemessen am 23.11.2026 beim Gegenlesen. Vier Rufer und ein Schreibweg - eigener Schritt.

Offen: Die Zerleger-Tafel liest nur `app/**/*.py`. Ein eigener `key=value`-Leser in `skripte/` oder in einem Router fällt ihr nicht auf - die fünf Gestalten, die sie am 23.11. übersah (Dateiobjekt, `keepends`, Trenner als Konstante, `chr(61)`, `\r\n`), sind dagegen geschlossen und je mit einem roten Lauf belegt.

Offen: Die Geldwort-Tafel (`VorlageTest` 10e) sieht nur `\bMark\b` und ist für "Goldmark" blind - ein neues Goldmark-Literal in einem Spielertext fällt nicht auf. Eine Schwestertafel braucht dieselbe sorgfältige Aufzählung wie die erste.

Offen: ein Weltname MIT Umlaut fällt auf "world" zurück (die Positivliste ist ASCII) - einheitlich seit 23.11., vorher nur bei transfer und backup. Weiten braucht eine eigene Messung gegen Minecraft und Windows.

~~Der Assistent kann in Archive sehen: Jars, Datapack-Zips, Resource Packs. Nur fuer Admins.~~

~~op, deop, pardon und whitelist off nur fuer Admins - an allen vier Tueren (Konsole, Zeitplan, Zugriffsseite, Dateieditor).~~

~~Pfadzugriff für Super-Admins: überall auf dem Managerrechner, nicht nur im Serverordner.~~

~~Die Grenze dazu steht an EINER Stelle: Geheimnisse bleiben zu (.env, DB, .git, Schlüsselspeicher), kein Schreibweg in den Programmcode, nichts dicht unter einer Laufwerkswurzel, kein Serverordner, Abzweigungen werden aufgelöst UND danach geprüft.~~

- ~~Freimarken Schritt 5 im Spiel prüfen: zwei Spieler, fremder Grund.~~ 31.10. bestanden.
- ~~Auf dem Testserver plugins/Freimarken/config.yml mit den MariaDB-Zugangsdaten füllen.~~ Startprobe durch.
- **Einmal auf dem Datenbankrechner skripte/mariadb_einrichten.bat erneut ausführen.** Es legt freimarken_test an; ohne das schlägt :daten:test absichtlich fehl. Vorhandene Passwörter bleiben unverändert.
- ~~Weltgrenze einziehen, bevor zum ersten Mal echt gespielt wird.~~ 31.10.: Stadtgrenze 42 Parzellen je Achse (-21..20), WorldBorder 2.989 um -3,5/-3,5, Einstellung welten.marktstadt.kante. Wandert nur nach außen.
- Marktstadt vorgenerieren, bevor echt gespielt wird: Mitte -3,5/-3,5, Radius 1.495, 35.344 Chunks, 36 Regionen im Feld - auf der Platte liegen mehr Dateien, die Dateizahl taugt nicht als Pruefung. Reihenfolge ist Pflicht - erst Grenze, dann vorgenerieren.
- ~~Manager: Sicherung/Umzug muss world_border.dat mitnehmen. Sie liegt NICHT in der level.dat, sondern in dimensions/<namensraum>/<welt>/data/minecraft/.~~ 23.11.: Die Sicherung nahm sie schon mit (rglob ist rekursiv). Verloren ging sie nur beim Stil-Umbau Paper -> Vanilla - dort wird sie jetzt KOPIERT statt verschoben. Zusatzwelten wurden dabei gar nicht umsortiert und verloren auch Spielregeln, Wetter und Generator-Einstellungen; jetzt rufen beide Wege EINE Stelle.
- Offen, zu messen: wo ein Vanilla/Forge/Fabric 26.1 die Weltgrenze LIEST - je Dimension oder in data/minecraft. Testserver, /worldborder set 1000, dann `find <welt> -name world_border.dat`. Bis dahin liegt sie an beiden Orten; die Kopie ist eine Bruecke, kein Vorbild fuer die fuenf anderen Dateien. Dieselbe Messung fehlt fuer die GEGENRICHTUNG: Der Zweig `nach_vanilla=False` ist gebaut und ungerufen, und eine Probe haelt das fest (Dateien am Vanilla-Platz bleiben liegen).
- ~~Vor Schritt 6 entscheiden: Datenmodell aus §6.1?~~ 31.10. entschieden.
- ~~Schritt 6, Teil A: Kern, Ablage, Schutz.~~ 31.10.
- ~~Schritt 6, Teil B+C: Befehle und Weltarbeit.~~ 1.11.: /parzelle zusammenlegen <richtung>, /parzelle auftrennen, Recht freimarken.gruppe.<n>.
- ~~Entscheidung Auftrennen:~~ Weg kommt zurueck und raeumt, was darauf steht; die Warnung nennt die Zahl vorher, bestaetigt wird mit Nummer.
- ~~Spieltest Zusammenlegen/Auftrennen:~~ 1.11. bestanden.
- Hinweis: Nach vielen Gradle-Laeufen bricht die Freigabe weg ("Unerwarteter Netzwerkfehler", "Cannot create directory"); Gradle nimmt danach veraltete Klassen, Tests werden grundlos rot. Abhilfe: build/reports loeschen, fehlende build-Unterordner anlegen, neu bauen.
- ~~Spieltest mit dem neuen Plugin:~~ 1.11. bestanden.
- ~~Schritt 6, Teil D: Auftrennen einzeln.~~ 1.11.: /parzelle auftrennen, /parzelle auftrennen alles; ein tragendes Feld wird abgelehnt.
- ~~Spieltest Teil D:~~ 1.11. bestanden.
- ~~Spieltest Quadrat (Kreuzungsarm):~~ 1.11. bestanden.
- Warnung: Der Server verschluckt Konsolenzeilen, wenn zu viele auf einmal kommen - einzeln mit Pause fragen.
- Neu in der config.yml: hinweise.rang-werbung - eine Zeile unter jeder Absage, die ein RANG verursacht (Kauf, Zusammenlegen, freie Parzelle am Limit). Vorgabe 'Mehr erlaubt ein hoeherer Rang.'; leer heisst kein Hinweis.
- ~~Kaufbare Raenge geben einen Zuschlag~~ [1.11., entschieden]: Foerderer +2 Parzellen und +1 Feld je Gruppe, Stifter +4 und +2 (freimarken.extra.parzellen.<n>, freimarken.extra.gruppe.<n>).
- ~~Zuschlag nur fuer BEZAHLTE Raenge, nicht fuer Goldmark~~ 1.11. entschieden - ebenso zusaetzliche Heimatpunkte und Warps. Kosmetik bleibt erspielbar.
- Offen: Zahlen fuer zusaetzliche Warps je bezahltem Rang. Heimatpunkte stehen (Foerderer +2), Warps sind in der Rangtabelle noch nicht beziffert.
- Offen, niedrig: Ein Stifter mit 8 Parzellen bindet doppelt so viel Flaeche wie ein Buerger ohne Mitgliedschaft (1.764 Parzellen insgesamt). Nachzujustieren ueber das Recht, nicht im Programm.
- ~~Schritt 7 erfasst~~ 1.11.: Bauplan A-G (A Buchungsbuch, B Wirtschaftswerte, C Spielzeit, D Berufe waehlen, E Verdienen, F Bundesmarkt, G Messstelle). Verdienst 2.000-5.000 Mark je SPIELER, ein Block zahlt nur einmal.
- ~~Schritt 3, Teil A: Welten aus der Liste.~~ 1.11.: je Welt art (raster/gewoehnlich/leer), umgebung und kante in der config.yml.
- ~~Schritt 3, Teil A geprueft~~ 2.11.
- ~~Schritt 3, Teil B: Vorgenerierung.~~ 2.11. mit Chunky 1.5.3 von Modrinth (Paper 26.1-26.3, Java 25) ueber Konsolenbefehle. In plugins/Chunky/config.yml continue-on-restart: true setzen (ab Werk false). Fassung bei jedem Minecraft-Sprung von Hand nachziehen.
- /freimarken vorgenerieren gibt je Welt eine Zeile zum Kopieren; alle acht Welten 424.040 Chunks, hochgerechnet ~1 h und 5,6 GB - gemessen ist nur Altfurt, die obere Schranke im Plan sind 3-4 h und 15-20 GB.
- Chunky kennt keine Namensraeume: der Weltname traegt einen UNTERSTRICH (freimarken_marktstadt). Fertig ist eine Welt erst, wenn die Stichprobe aus 9 Punkten samt den 4 Ecken sitzt.
- Noch nicht gelaufen: die sieben grossen Welten auf dem echten Server. Reihenfolge klein nach gross, nacheinander, ohne Spieler - Chunky haelt je Welt nur eine gespeicherte Aufgabe.
- ~~Schritt 3, Teil C: Reisen.~~ 2.11.: EIN /warp und sieben Tore am Bundesmarkt in Altfurt. Nicht zu Pferd; selbstgebaute Nether- und Endportale gesperrt; Abenteuer in ganz Altfurt, Ausnahme freimarken.spielmodus.frei.
- Der Bundesmarkt steht bei 72/12 auf y 71 [entschieden] - Altfurt 0/0 liegt unter Wasser; das flache trockene Feld reicht von 56/-4 bis 87/27, Boden y 70-72. Torreihe auf z=0, sieben Durchgaenge je 3 breit, y 71..73.
- Noch von Hand zu tun: die zehn Gebaeude aus Plan 2.1 bauen (Programm setzt keine Bloecke in Altfurt) und /setworldspawn auf den Bundesmarkt setzen.
- Die Torkoordinaten sind an MEINEM Probe-Altfurt eingemessen - auf anderem Gelaende klagt das Plugin beim Start und nennt die passenden Zahlen.
- ~~Sterben, zurueckkehren, besuchen.~~ 2.11.: Wer stirbt, erscheint IMMER in Altfurt; Bett und Anker setzen keinen Spawnpunkt (Ausnahme freimarken.spawnpunkt.setzen), Schlafen geht weiter.
- /zurueck (/back) fuehrt an den letzten Ort: vor Reise, vor Teleport, Todesstelle. Nur im Speicher.
- /tpa <spieler> + /tpaccept + /tpdeny. Nur der FRAGENDE wird bewegt, kein /tpaall. Anfrage gilt eine Minute.
- Jeder Teleport laeuft jetzt durch dieselbe Reise, auch der an die eigene Parzelle.
- ~~Zweispurig deutsch/englisch~~ 2.11. [entschieden]: /parzelle auch /plot, /mark auch /balance und /bal, /zurueck auch /back, /tpaccept auch /tpaaccept und /annehmen, /tpdeny auch /tpdecline und /ablehnen.
- ~~/tpahere und Aufwaermzeit.~~ 2.11.: /tpahere laedt ein, der EINGELADENE wandert und stimmt zu. /tpaall gibt es nicht.
- Jeder Teleport hat 3 s Wartezeit [entschieden]; Bewegung UND Schaden brechen ab. freimarken.sofort (op) ueberspringt sie.
- Im Spiel noch zu pruefen: im Bett schlafen (Nacht uebersprungen, KEINE Meldung 'Respawn point set'), Respawn-Anker ohne Wirkung, als Betreiber /spawnpoint muss gehen.
- Im Spiel noch zu pruefen: in Kornmark sterben (Ankunft Altfurt), /zurueck an die Todesstelle, in Lava sterben + /zurueck (muss abgelehnt werden), aus dem Ende durch das Rueckportal (NICHT nach Altfurt), /tpa samt Ablauf, abgemeldetem Fragenden und zu Pferd.
- Offen in Schritt 3: wolkenwerder fehlt in der Liste (Leere ohne Startinsel, kommt mit Schritt 11).
- In der Marktstadt gibt es kein Holz, kein Wasser, keine Erze und keine Nutztiere - vier der sechs Grundberufe brauchen die anderen Welten.
- ~~Schritt 7, Teil A: Buchungsbuch.~~ 2.11.: jede Geldbewegung als Zeile, /mark verlauf zeigt zehn. Plan 8.6.
- ~~Testdaten zurueckgesetzt~~ 2.11.: Konto auf das Startguthaben 50.000. Die Bloecke in der Marktstadt stehen noch.
- Im Spiel noch zu pruefen: beitreten (Protokollzeile 'Konto eroeffnet'), /mark verlauf, erste Parzelle (gratis, Zeile mit 0 Mark), zweite (minus 25.000), zusammenlegen, und ein Kauf mit zu wenig Mark (keine neue Zeile).
- ~~Schritt 7, Teil B: Stammwerte in die config.yml und /freimarken neuladen.~~ 2.11. Plan 8.7.
- Neue Schluessel: wirtschaft.parzelle.grundpreis (0 bis 8.388.607), wirtschaft.parzelle.zusammenlegung-je-feld (0 bis 4.294.967.298), wirtschaft.sprung-bestaetigt.<name> - mehr als Faktor 2 gegen den ausgelieferten Wert nur mit Bestaetigung.
- Geldwerte in der config.yml ohne Tausenderpunkt und ohne Anfuehrungszeichen schreiben: aus 25.000 liest YAML 25, aus "25000" wird 0. Beides wird am Typ abgelehnt und beklagt.
- Was keine Schranke faengt, ist der falsche Wert im Bereich (2.500 statt 25.000). Dagegen steht bei jedem Start UND jedem Neuladen die Staffel im Klartext: "2. Parzelle 25000 - 3. 50000 - 4. 100000".
- Beim START gilt je Wert der Rueckfall samt Klage, beim NEULADEN alles-oder-nichts.
- kasse.startguthaben wird gelesen und gemeldet, aber NICHT uebernommen - ein neuer Wert aenderte rueckwirkend, was jeder noch nie buchende Spieler mitbringt.
- Kein eigenes Recht fuer /freimarken neuladen: freimarken.verwalten, derselbe Zweigschutz wie /freimarken welt und parzelle.
- Von den vier Punkten des Pruefauftrags sind 1 (Preisstaffel in die config.yml), 2 (/freimarken neuladen) und 3 (Startguthaben nicht mehr rueckwirkend) erledigt; offen bleibt 4 (parzellen.hoechstzahl 4 statt 1) bis Schritt 12.
- ~~Im Spiel geprueft~~ 2.11.: /freimarken neuladen als Spieler mit freimarken.verwalten, die Preisstaffel stimmt, und der Block nennt keine Zugangsdaten.
- Im Spiel noch zu pruefen: dass ein Kauf mitten im Neuladen den Preis EINES Standes nimmt.
- ~~Teil B gegengelesen~~ 2.11.
- Kaputte config.yml: Neuladen uebernimmt nichts und nennt die fehlerhafte Zeile, beim Start schaltet sich das Plugin ab.
- ~~Schritt 7, Teil C: Spielzeit und Tagesgrenze.~~ 3.11. Plan 8.8.
- Neue Schluessel: zeit.zone (Vorgabe Europe/Berlin), zeit.takt-sekunden (10 bis 600, Vorgabe 60), zeit.untaetig-minuten (0 bis 1440, Vorgabe 10; die 0 heisst "alles zaehlt"). Nur untaetig-minuten ist neuladbar.
- zeit.zone braucht einen Zonennamen - ein fester Versatz wie UTC+2 wird abgewiesen, er kennt keine Sommerzeit.
- Der Tag in der Tabelle spielzeit ist die einzige Spalte im Schema, die NICHT UTC ist, sondern gerechnetes Ortsdatum - nicht zurueckrechnen.
- Spielzeit: kein Setzen von Hand, kein Altbestand - jeder faengt bei 0 an.
- /freimarken spielzeit [<spieler>] mit zwei Rechten (freimarken.spielzeit true, freimarken.spielzeit.andere op); fremde Spielzeit nur fuer Anwesende.
- Schemaschritt 9 (Tabelle spielzeit, neu und leer). Ab dem ersten Start mit diesem Jar verweigert jedes aeltere den Start - ein Zurueckrollen der Jar-Fassung ist danach keine Option.
- Warnung: Der Schemastand ist MAX(schritt) - wer einen Schritt zum Pruefen zurueckdreht, muss alles ab ihm loeschen. Die Falle hat schon zweimal zugeschnappt.
- Jede Antwort auf /freimarken neuladen endet mit dem Block "Nur beim Start gelesen": Datenbank, Welten, Ankunftswelt, Startguthaben und die Zahlen, die im Programm bleiben (Parzelle 64, Weg 7, Boden 62, Deckel 40).
- Pruefen, Teil C: Startprotokoll nennt die Tagesgrenze in Ortszeit UND in UTC (zwei gleiche Uhrzeiten am Nachmittag heissen falsche Zone) und darunter den Spielzeittakt.
- Pruefen, Teil C: /freimarken spielzeit - heute und gesamt wachsen, die laufende Sitzung steigt im Sekundentakt; nach elf Minuten Stillstand waechst nichts ("wird gerade NICHT gezaehlt"), nach einem Schritt wieder. Nichts wird nachgezahlt.
- Pruefen, Teil C: /freimarken spielzeit <spieler> nur mit freimarken.spielzeit.andere; ein abgemeldeter Name bekommt die Absage; auf der Konsole ohne Namen "Die Konsole spielt nicht.".
- Pruefen, Teil C: /stop mit einem Spieler online - die Tabellenzeile ist um die Sekunden seit dem letzten Takt gewachsen (0 Verlust); Prozess hart beenden - Verlust unter einer Taktlaenge, nach dem Neustart laeuft nichts doppelt.
- Pruefen, Teil C: /freimarken neuladen - zeit.untaetig-minuten wirkt beim naechsten Takt, und es erscheint KEINE zweite "Spielzeittakt steht"-Zeile.
- Pruefen, Teil C: /mark verlauf zeigt dieselbe Ortszeit wie die Tagesgrenze - beweisbar nur auf einem Rechner, dessen Systemzone NICHT Europe/Berlin ist.
- Pruefen, Teil C: Plugin im Betrieb ab- und wieder einschalten - die Startzeile nennt "n Spieler schon anwesend", die Zeit der Anwesenden laeuft weiter.
- Pruefen, Teil C: Mitternacht in der eingestellten Zone - zwei Zeilen fuer denselben Spieler, Summe stimmt. Erzwingbar ueber zeit.zone (Neustart noetig).
- Offen fuer den Menschen: parzellen.hoechstzahl bleibt bei 4, ein Neuling hat weiter Buergerrechte (bis Schritt 12).
- Erst nach einem Neustart wirksam: zeit.zone, zeit.takt-sekunden, kasse.startguthaben, Datenbankadresse, Ankunftswelt.
- Offen, gewollt: Die Untaetigkeitsgrenze ist eine Hoeflichkeitsregel, keine Sperre - ein Makro umgeht sie. Dagegen hilft nur Teil G (Messstelle).
- ~~Teil C gegengelesen und nachgearbeitet.~~ 4.11.
- zeit.untaetig-minuten muss mindestens so gross sein wie zeit.takt-sekunden (ganze Minuten, aufgerundet) - oder 0. Sonst Klage mit Mindestzahl, beim Neuladen bleibt die alte Grenze.
- Nach einem Fehlschlag setzt der Takt einige Schlaege lang das SCHREIBEN aus; gerechnet wird weiter, der naechste erfolgreiche Zug nimmt die Zuschlaege mit.
- Preis der Sperre: Im Sperrfenster liegen gemessene Sekunden nur im Arbeitsspeicher - bei 60 s Takt bis zu 6 Minuten, bei 600 s bis zu 60, je Spieler, der im Fenster einmal da war (ein Abgang leert den Stapel nicht). Ein Absturz oder Waechterkill verliert sie.
- ~~Offen, klein: Leseweg des Ueberlaufs.~~ 4.11.
- Offen, klein: Die Zahl der ausgesetzten Schlaege haengt nicht am Takt - bei 10 s ein guter Tausch, bei 600 s auf den Kopf gestellt.
- Pruefen, Teil C: Netz zur Datenbank trennen und MITZAEHLEN - eine Verlustzeile, dann einige Taktlaengen lang KEINE weitere, dann wieder eine. Wie viele Schlaege aussetzen, nennt die Zeile selbst.
- Pruefen, Teil C: Datenbank zurueckholen, waehrend die Sperre steht, und einen Spieler abmelden - seine Sekunden stehen beim naechsten Taktschlag in der Tabelle, KEINE Verlustzeile zu ihm; danach duerfen heute und gesamt nicht stehenbleiben.
- Pruefen, Teil C: /stop bei toter Datenbank - Verlustzeile UND "0 von n offenen Sitzung(en) weggeschrieben" ("0 von 1" kann harmlos sein, wenn der letzte Takt untaetig war). Mit zurueckgeholter Datenbank bei stehender Sperre MUSS geschrieben werden.
- Pruefen, Teil C: Proxy-Neustart oder vierzig Abgaenge auf einmal mit toter Datenbank - der Server darf nicht vom Waechter abgeschossen werden, hoechstens EINE Verlustzeile.
- Pruefen, Teil C: Start mit unpassendem Paar (takt-sekunden 600, untaetig-minuten 1) - Klage mit "mindestens 10 Minuten", es bleibt bei 10. Dann beides gemeinsam auf 60/1 verkuerzen und neuladen: der Block nennt zeit.untaetig-minuten als nicht uebernommen.
- Pruefen, Teil C: /freimarken neuladen mit fehlendem zeit.zone - der Block sagt "es gilt weiter <laufende Zone>, neu gelesen wurde Europe/Berlin". Mit fehlendem parzellen.hoechstzahl "es gilt 4".
- Pruefen, Teil C: /freimarken spielzeit - der Hinweis zum Altbestand ist grau wie die Zeilen darueber. Eine eigene Farbe dafuer steht jetzt in der Sprachdatei (spielzeit.hinweis).
- Pruefen, Teil C: Nach einem Fehlschlag /freimarken spielzeit - die dritte Zeile muss auf "wartet auf die Datenbank" enden und NICHT auf "das geschieht alle 60 s".
- Pruefen, Teil C: Die Verlustzeile nennt jetzt den Absturz; eine NEU angelegte config.yml nennt die Stillstandsdauer nicht mehr als Zahl und den Absturzverlust mit Bedingung - eine schon ausgelieferte Datei bekommt den neuen Text nicht.
- Sprachdatei vorgezogen [entschieden]: Worte, Farben und Aufbau wie im MOTD-Editor. sprache-vorlage.yml wird bei jedem Start neu geschrieben, sprache.yml nimmt nur die eigenen Aenderungen. Alles oder nichts.
- ~~Sprachdatei Schritt 1: Fundament.~~ 4.11.: MiniMessage, erlaubt sind nur Farbe und Hervorhebung (<click> und <hover> koennten einen Befehl im Namen des Spielers ausloesen). 18 Schluessel sind umgezogen.
- ~~Sprachdatei Schritt 2: /parzelle.~~ 4.11.: 104 Schluessel dazu, die Datei traegt jetzt 122 Saetze. /parzelle nennt keine Farbe mehr selbst.
- ~~Sprachdatei Schritt 3 und 4: /tpa und /freimarken spielzeit.~~ 5.11.: 36 Schluessel dazu, die Datei traegt jetzt 158 Saetze. Einzug und Spaltenbreite stehen mit drin.
- ~~Sprachdatei Schritt 5: die Reisen.~~ 5.11.: 21 Schluessel dazu, die Datei traegt jetzt 179 Saetze. /warp fasst drei Chatzeilen in einem Schluessel zusammen, Umbruch und Trenner stehen mit drin.
- ~~Sprachdatei Schritt 6: die Wachen.~~ 5.11.: 7 Schluessel dazu, die Datei traegt jetzt 186 Saetze. Erster Handbalken, und ein Satz wechselt mitten drin die Farbe.
- ~~Sprachdatei Schritt 7 und 8: /freimarken selbst.~~ 5.11.: 9 Schluessel dazu, die Datei traegt jetzt 195 Saetze. Betraege ist geloescht, jeder Betrag kommt aus waehrung.betrag.
- Entscheidung, Sprachdatei: Sechs Saetze sagen bei der Anzahl 1 die Mehrzahl ('aus 1 Feldern') - so sieht es heute aus. Eine Einzahlform waere besser, aendert aber das Bild.
- ~~Warnung: waehrung.betrag steht an ZWEI Orten.~~ 5.11. behoben: Die zwei Preiszeilen von /freimarken holen das Wort jetzt aus der Datei, die Marke HALB IN KRAFT ist aus der Vorlage heraus.
- ~~Kein Probenlauf durchlaeuft einen Ereigniszweig.~~ 6.11.: SchutzwacheTest 36-36d laeuft die vier Lagen, die Drossel und den Namensrueckfall wirklich durch - ueber einen Server-Stellvertreter.
- ~~Ankunfts- und Portalwache durchlaufen.~~ 6.11.: AnkunftswacheTest 37-37e und PortalwacheTest 38-38d; damit ist die Gattung Ereigniszweig zu.
- ~~Befehlszweige durchlaufen, erster Teil.~~ 6.11.: Befehlsprobe mit eigenem CommandDispatcher; MarkBefehlTest 39-39e und ParzelleBefehlTest 40-40e pruefen die WEICHE vom getippten Wort zum Zweig.
- ~~/tpa, /warp und /zurueck getippt.~~ 6.11.: WarpBefehlTest 41-41d, ZurueckBefehlTest 42-42d, AnfragenBefehlTest 43-43g. 38 Durchlaufproben in acht Dateien.
- ~~/freimarken getippt.~~ 6.11.: Befehlsbaum nach BundBefehle ausgezogen, jetzt hat jede der sieben Befehlsgruppen ihre eigene Klasse. 47 Durchlaufproben in neun Dateien.
- Offen: Die Reise selbst bleibt aussen vor - Reise.fuehre fragt als Erstes die Aufwaermzeit, und die braucht eine JavaPlugin-Instanz. Erreichbar sind nur die Absagen davor.
- ~~Drei Stellvertreter standen doppelt.~~ 6.11.: AufrufstelleTest gibt nur noch weiter, Serverprobe und Spielerprobe sind der eine Ort. Alle 918 Proben gruen, fuenf Importe weg.
- ~~Schritt 7, Teil D: Berufe waehlen.~~ 6.11.: /beruf liste|stand|waehlen|verlassen; zwei Grundberufe und eine Spezialisierung, Aufgeben kostet 5.000 Mark und sperrt 24 h. Plan 9.1.
- ~~Blockmarken: ein Block zahlt nur einmal.~~ 6.11.: Ein Bit je Blockposition IM CHUNK, nie geloescht - eine markierte Stelle zahlt weder beim Abbauen noch beim zweiten Setzen.
- ~~Schritt 7, Teil E: Verdienen.~~ 6.11.: 84 Lohnsaetze in der config.yml, Pfennige je Block, Minderung in Stufen je Stunde, Tagesdeckel 20.000 Mark; gebucht wird gesammelt im Takt. Plan 9.1.
- ~~Verdienen, Teil 2: Fischen, Toeten, Zuechten, Ernten.~~ 6.11.: 154 Lohnsaetze; neu einstellbar sind eine harte Stundenmenge je Taetigkeit, die Weissliste der Spawngruende und das Saatgut.
- Die Stundenmenge ist der wirksame Hebel gegen Farmen - die Minderungsstufen daempfen nur. Saatgut bekommt keine Marke und zahlt nichts; bezahlt wird die Ernte.
- Offen, Verdienen: Seehandel, Bootsrennen, Vorratshaltung und Auftragsbau sind keine Ereignisse, sondern eigene Systeme - eigener Schritt.
- Pruefen, Teil E: Start - das Protokoll nennt "Lohnliste: 154 Saetze, Tagesdeckel 20000 Mark" und "Blockmarken und Verdienst aktiv in 8 Welten". Keine severe-Zeile.
- Pruefen, Teil E: Bergmann im Hochwald - gewachsener Stein gibt den Handbalken "+0,40 Mark", ein selbst gesetzter Stein gibt beim Abbauen NICHTS. Nach dem Neustart gilt die Marke weiter.
- Pruefen, Teil E: Bauer - gesaeten Weizen sofort umschlagen bringt nichts, reifer Weizen "+1,50 Mark". Saeen selbst zahlt nie.
- Pruefen, Teil E: Toeten - ein per summon gerufener Zombie zahlt NICHTS (Grund command), ein nachts selbst erschienener zahlt. Ein gezuechtetes Kalb zahlt beim Tod nichts.
- Pruefen, Teil E: Zucht - zwei Kuehe fuettern gibt "+35,00 Mark". Angeln im offenen Wasser zahlt, in einem Ein-Block-Loch nicht.
- Pruefen, Teil E: Stundenmenge - verdienst.minderung.stundenmenge.abbauen auf 5, neuladen, sechs Steine: der sechste bringt "Mit Graben ist fuer diese Stunde genug".
- Pruefen, Teil E: Tagesdeckel - tagesdeckel auf 1 samt Sprungbestaetigung, dann weiterarbeiten: "Heute ist genug verdient". Was darueber anfiel, ist am naechsten Tag NICHT nachgezahlt.
- Pruefen, Teil E: /freimarken neuladen nennt JEDE geaenderte Verdienstzeile - auch die sechs Stundenmengen und die zwei Listen mit "dazu:" und "weg:".
- Pruefen, Teil E: /mark verlauf zeigt "Verdienst aus Arbeit"; die Summe der Zeilen stimmt mit /mark.
- Pruefen, Teil E: eine kaputte Lohnzeile (toeten: stone: 100) wird beim Neuladen beklagt, der Rest der Liste gilt weiter. Saatgut unter abbauen wird VERWORFEN.
- ~~Schritt 7, Teil F: Bundesmarkt-Ankauf.~~ 7.11.: /markt zeigt 56 Materialien, /markt verkaufen nimmt die Hand. Eigener Tagesdeckel 5.000 Mark. Plan 8.2.
- Der Bund nimmt nur STUECKE, die eine ganze Mark ergeben: Von sieben Stein zu 20 Pfennigen nimmt er fuenf fuer eine Mark, zwei bleiben. Nichts verfaellt.
- Verkauft wird nur in der Ankunftswelt - der Markt ist ein Ort. Die LISTE darf man ueberall ansehen.
- Keine Marktgebuehr beim Bund: Die niedrigen Preise SIND die Senke. Die 5 % aus Plan 8.2 gehoeren zum Spielerhandel in der Markthalle (Schritt 8).
- Pruefen, Teil F: Start - das Protokoll nennt "Bundesmarkt: 56 Materialien im Sortiment." und darunter "Tagesdeckel 5000 Mark je Spieler". Keine severe-Zeile.
- Pruefen, Teil F: /markt in Altfurt - 56 Materialien in der Reihenfolge der config.yml, darunter "Heute kannst du noch 5.000 Mark aus Verkaeufen bekommen".
- Pruefen, Teil F: sieben Stein in der Hand, /markt verkaufen - fuenf weg, eine Mark, zwei bleiben. In Hochwald dieselbe Eingabe: Absage, Ware bleibt.
- Pruefen, Teil F: vier Stein (zu wenig fuer eine Mark) und Erde (nicht im Sortiment) haben VERSCHIEDENE Absagen. /mark verlauf zeigt "Verkauf an den Bundesmarkt".
- Pruefen, Teil F: bundesmarkt.tagesdeckel auf 1 samt Bestaetigung - nach einer Mark "Heute ist genug verkauft". Arbeit geht danach WEITER (eigener Deckel).
- ~~Schritt 7, Teil G: Messstelle.~~ 7.11.: zweite Zahl neben der Anwesenheit - TAETIGE Sekunden je Spieler und Tag. /playtime zeigt beide, /freimarken messstelle zeigt warum.
- Sie GREIFT IN NICHTS EIN: kein Verdienst gesperrt, keine Zeit verworfen, kein Spieler gemeldet. Erkennung statt Verhinderung - eine Sperre traefe auch eintoenige ehrliche Arbeit.
- Gemessen wird VIELFALT und nicht Menge: Ein Makro gibt immer DASSELBE Zeichen. Zwei von sechs Arten je Taktschlag machen ihn taetig (messstelle.verlangte-arten).
- Sieben Arten: bewegen, abbauen, setzen, oeffnen, benutzen, kaempfen, sprechen. benutzen ist ab Werk AUS - es ist die halbe Art zum Setzen (derselbe rechte Knopf).
- Der Linksklick zaehlt NIE als benutzen: Er feuert bei jedem Schlag, und ein Makro am Generator bekaeme sonst abbauen UND benutzen und waere taetig.
- Das Bewegen wird aus dem ABSTAND gemessen, nicht aus Ereignissen - eine gewackelte Maus traegt niemanden acht Bloecke weit. Im Kreis laufen ist keine Strecke, Weltwechsel zaehlt immer.
- Kein Ausschalter in der config.yml - das waere ein Schalter, mit dem man sich stumm die eigene Messung wegwirft. Milde geht ueber verlangte-arten: 1.
- Untermessen ist die sichere Richtung: Wer einen Takt lang nur segelt, zaehlt nicht als taetig. Zu viel Spielzeit gibt einen Rang zu frueh, zu wenig nur zu spaet.
- Schemaschritt 11: spielzeit bekommt taetig_sekunden (DEFAULT 0), gleiche Zeile, gleicher Schreibweg. Alte Zeilen bleiben 0 - geraten wird nichts.
- Offen, gewollt: Mit benutzen IN der Liste gibt ein Setzmakro zwei Zeichen aus einer Taste. Darum ab Werk aus; wer es dazunimmt, kennt das Paar (steht in der config.yml).
- Noch ohne Probe: der Chatzweig. Paper liefert Chat ausserhalb des Hauptfadens, die Meldung geht ueber den Zeitgeber - ohne Server nicht pruefbar, nur im Spiel.
- Pruefen, Teil G: Start - das Protokoll nennt "Messstelle: 2 von 6 Arten je Taktschlag machen ihn taetig (bewegen, abbauen, setzen, oeffnen, kaempfen, sprechen), Bewegen ab 8 Bloecken".
- Pruefen, Teil G: Stein abbauen ohne zu laufen, eine Taktlaenge warten - /playtime zeigt "taetig 0 min". Dann abbauen UND zwanzig Bloecke laufen: die taetige Zeit waechst.
- Pruefen, Teil G: /freimarken messstelle nennt je anwesendem Spieler, wie viele von zwei Arten er im laufenden Schlag gegeben hat - und sagt, dass das Bewegen fehlt.
- Pruefen, Teil G: messstelle.arten um benutzen erweitern, neuladen - die Antwort nennt "messstelle.arten: 6 -> 7 Arten, dazu: benutzen". Eine Zaehlung allein saehe das nicht.
- Pruefen, Teil G: verlangte-arten 7 bei sechs Arten, neuladen - Klage mit BEIDEN Zahlen, es gilt 6. Eine leere arten-Liste wird ebenso verworfen.
- Pruefen, Teil G: Chat schreiben (nicht Befehl) und etwas abbauen, eine Taktlaenge warten - taetig. Der Umweg ueber den Zeitgeber ist nur hier pruefbar.
- ~~Entschieden: Tagesbonus kommt als Teil H nach G~~ 7.11.: Die Schritt-7-Uebersicht verlangt ihn, und kein Teil A-G deckt ihn. Betrag und Rhythmus in die config.yml.
- ~~Schritt 7, Teil H: Tagesbonus.~~ 7.11.: taeglich Einloggen bringt 500 Mark, jeder Tag der SERIE 100 mehr, hoechstens 2.000 (ab Tag 16). Damit ist Schritt 7 durch.
- Zwei Dinge belohnt: das EINLOGGEN mit dem Grundbetrag, das REGELMAESSIGE Spielen mit der Serie. Die Serie reisst, wer einen Tag verpasst (serie-reisst-ab-luecke: 2).
- Drei Nachrichten fuer eine Zahlung: erster Tag (nennt, was morgen kaeme), Serientag, und ein EIGENER Satz fuer die eingestellte Bestmarke. /mark zeigt die Serie.
- Der Waechter ueber "heute schon bezahlt" ist das BUCHUNGSBUCH und nicht die Serienzeile: kein Absturz, kein Neustart und keine kaputte Zeile zahlt zweimal.
- Gezahlt wird ZUERST, die Serie danach. Bricht es dazwischen ab, hat der Spieler sein Geld und verliert einen Serientag - das steht laut im Protokoll.
- Die Bestmarke bleibt stehen, wenn die Serie reisst. Aus einem Datum und einer Zahl ist nicht herauszulesen, wie lang eine frueher gerissene Serie war.
- tagesbonus.mindest-minuten (Vorgabe 0 = beim Einloggen): Wer mehr verlangt, zaehlt TAETIGE Minuten aus Teil G - Anwesenheit ist im Stehen zu haben.
- Schemaschritt 12: Tabelle tagesbonus (spieler, letzter_tag DATE, serie, laengste_serie). Kein Geld darin, kein Fremdschluessel, letzter_tag darf NULL sein.
- Gemessen: Der Sprungschutz von wirtschaft.sprung-bestaetigt greift in BEIDE Richtungen - 2000 auf 800 braucht dieselbe Bestaetigung wie 500 auf 5000.
- Pruefen, Teil H: Start - das Protokoll nennt "Tagesbonus: Tag 1 gibt 500 Mark, Tag 2 600 Mark, ab Tag 16 dann 2000 Mark. Die Serie reisst, wer 1 Tag verpasst".
- Pruefen, Teil H: einloggen - "Tagesbonus: +500 Mark - komm morgen wieder, dann sind es 600 Mark." /mark zeigt die Serie, /mark verlauf die Zeile "Tagesbonus".
- Pruefen, Teil H: ab- und wieder anmelden - KEINE zweite Zahlung. Auch nicht nach /freimarken neuladen oder einem Neustart des Servers.
- Pruefen, Teil H: in der Datenbank letzter_tag auf vorgestern setzen, neu einloggen - die Serie faengt bei 1 an, laengste_serie bleibt stehen.
- Pruefen, Teil H: mindest-minuten auf 2, neuladen, einloggen - erst nichts, /mark sagt "noch 2 taetige Minuten". Dann zwei Minuten ABBAUEN UND LAUFEN: der Bonus faellt.
- Pruefen, Teil H: grundbetrag 0 und serie-reisst-ab-luecke 1 eintragen, neuladen - beide werden mit Grund beklagt, die Vorgaben gelten weiter.
- ~~Schritt 8 erfasst~~ 7.11.: Bauplan A-F (A Ladeneinheiten, B Mietvertrag, C taeglicher Lauf samt Abholfach, D Freie Truhe, E der einfache Laden, F Marktgebuehr).
- ~~Abnahme laut Plan 15: "ein Laden laeuft ab und wird sauber zurueckgegeben".~~ 11.11. GELAUFEN, im Spiel und in der Datenbank gegengeprueft: Der Vertrag ueber stand-1 wurde um einen Tag zurueckdatiert (ein Ablauf braucht sonst einen Tageswechsel), der erste Takt nach dem Neustart hat die ganze Kette gezogen.
- Die Zeile im Protokoll: 'Laden "stand-1" ... ist abgelaufen (Kaution 5000 Mark zurueck) - 3 Stapel im Abholfach.' Die Tabelle ladenmiete ist leer, im Abholfach liegen 5x DIAMOND, 32x IRON_INGOT und 17x OAK_LOG mit Herkunft "Laden stand-1".
- NEBENBEI BESTAETIGT: abholfach.eingelegt steht auf 13:19:53 UTC, waehrend das Protokoll 14:19:53 Ortszeit zeigt - die Umstellung von heute wirkt, und zwar in der Spalte, die es bis heute frueh falsch hatte.
- Der Tagesmerker des Mietlaufs lebt nur im Arbeitsspeicher - ein Neustart laesst den Durchgang heute noch einmal laufen. Genau das macht diese Abnahme ohne Warten moeglich.
- Werkzeug dafuer: scratchpad/Mietvertrag.java (Einzeldatei-Java mit dem MariaDB-Treiber des Projekts, liest die Zugangsdaten selbst aus data/freimarken/datenbank.properties - so steht kein Kennwort in einem Befehl). Unterbefehle: zeigen, fach, zurueckdatieren <einheit> <tage>.
- ~~Entschieden: Mietdauer BEFRISTET mit Verlaengerung~~ 7.11. (Vorgabe 30 Tage). Der Plantext kennt keine Dauer; befristet ist die haertere Senke - wer nicht verlaengert, verliert.
- ~~Entschieden: Die Miete laeuft am vorhandenen Spielzeittakt~~ 7.11. Dreht sich der Tag, laeuft sie einmal ueber alle Vertraege - auch ohne Spieler. KEIN zweiter Zeitgeber (Plan 8.8).
- Ein Server, der ueber Nacht aus war, holt die Abbuchung beim naechsten Start nach - genau einmal je versaeumtem Tag, nicht alles auf einmal und nicht gar nicht.
- BEFUND: Altfurt ist vom Besitzschutz NICHT gedeckt - die Schutzwache haengt an EINER Welt (Marktstadt). Was dort heute das Bauen verhindert, ist nur der Abenteuermodus.
- ~~Der Abenteuermodus erlaubt aber das OEFFNEN von Truhen. Schritt 8 braucht darum einen Truhenschutz fuer Altfurt auf Quader-Grundlage.~~ 11.11. GEBAUT - und ausdruecklich NICHT auf Quader-Grundlage: Die Einrichtungswache sperrt JEDEN Behaelter der Ladenwelt (eine Quaderliste waere eine Liste von Loechern).
- Daraus folgt: Ladeneinheiten werden von HAND vorgebaut (samt Truhe und Schild), der Mieter fuellt und preist nur. Minecraft kennt keinen Spielmodus je Region.
- ~~Die Ladeneinheit ist eine Zeile in parzellen~~ 7.11. ANDERS entschieden: Die EINHEIT steht in der config.yml (von Hand gebaute Einrichtung, kein gekaufter Besitz), der VERTRAG in der eigenen Tabelle ladenmiete. Eine Parzellenzeile daneben waere ein zweiter Ort fuer dieselbe Geometrie.
- ~~Zahlen aus dem Plan in die config.yml~~ 8.11.: laeden.miete-je-tag 500, kaution 5000, lizenz 50000, mietdauer-tage 30. Offen bleiben Marktgebuehr 5 % (Teil F) und Freie Truhe ~16 Gegenstaende je Spieler/Tag (Teil D).
- Die Kaution wird GEBUCHT und nicht gehalten: Das Buchungsbuch ist die Wahrheit ueber Geld, ein gehaltener Betrag waere eine zweite Quelle. Zurueck nur bei Kuendigung.
- Die Marktgebuehr 5 % gehoert hierher und nicht zum Bundesmarkt: Sie liegt auf dem SPIELERHANDEL, und den bringt Teil E. Die config.yml verschiebt sie heute ausdruecklich.
- BEFUND, Senken-Bilanz 7.11.: Einnahme 27.000/Tag (20.000 Arbeit + 5.000 Markt + 2.000 Bonus), dauerhafte Ausgabe 5.000/Tag (nur Berufswechsel). Sieben von neun Senken fehlen.
- Alles Kaufbare zusammen ist 265.000 Mark (Parzellen 2-4 und Zusammenlegen bis zur Vierergruppe) - nach zehn Tagen erreicht, mit Startguthaben nach acht.
- Danach wachsen 22.000 Mark je Tag ungebremst, rund 660.000 im Monat. Die Ladenmiete von 500 ist dagegen 2 % - Schritt 8 macht die Wirtschaft NICHT gesund.
- Offen, gewollt: Die Bilanz bleibt erst einmal so. Zu entscheiden, wenn die uebrigen Senken stehen (Freistadt 400.000, Meerinsel 75.000, Flags, Hochwald-Sicherung).
- ~~"Laden mieten" als Recht~~ 8.11.: freimarken.laden.mieten, Vorgabe fuer alle. Schritt 12 haengt den Rang Siedler daran - keine zweite Pruefung daneben.
- Wo die Markthalle in Altfurt steht, ist nicht eingemessen - nur der Bundesmarkt (72/12 auf y 71). Die Ladeneinheiten kommen als Quader in die config.yml.
- ~~Entschieden 8.11.: ZWEI PROBESTAENDE genuegen.~~ 9.11. EINGETRAGEN: stand-1 (60/71/6 bis 64/75/10), stand-2 (60/71/16 bis 64/75/20), stand-3 (80/71/16 bis 84/75/20), je 125 Bloecke, Truhe in der Mitte auf y=72. Zwischen stand-1 und stand-2 bleiben fuenf Bloecke frei - DIESE LUECKE ist die eigentliche Probe fuer den Ladenschutz: dort darf jeder bauen, in den Quadern nur der Mieter.
- ACHTUNG bei diesen Zahlen: Sie sind am 2.11. an den Regionsdateien gemessen, NICHT heute - der Probeserver von damals ist aufgeraeumt, seine Weltdaten gibt es nicht mehr. Belegt ist das Feld x 56..87 / z -4..27 (flachstes trockenes Feld in der Naehe von 0/0, Boden durchgehend y 70..72, alle 1.024 Deckbloecke red_sand). Die Hoehe JEDES Blockes darunter ist nicht gemessen, nur ein Raster von vier Bloecken.
- ~~Altfurt hat KEINEN festen Startwert.~~ 9.11. GEBAUT: welten.<name>.startwert, sieben verschiedene Werte (altfurt 110926001 und fortlaufend). Die Rasterwelt hat keinen - ihr Gelaende kommt aus dem Generator, ein Startwert waere dort eine Zahl, die nie wirkt.
- Und es gibt NIRGENDS mehr ein Altfurt: nicht auf dem NAS (dort liegen nur Logs, Configs und DB-Schnappschuesse), nicht im Scratchpad. Die Welt vom 2.11. ist mit ihrem Probeserver aufgeraeumt worden. Freimarken ist nie auf einem dauerhaften Server gelaufen.
- FOLGE, und die ist unangenehm: Die Quader der sieben TORE und der drei Staende passen zu KEINEM bekannten Startwert - das Gelaende vom 2.11. entstand aus einem gewuerfelten, der mit der Welt verschwand. Beim ersten Start mit dem festen Startwert entsteht ein ANDERES Altfurt, und beides muss neu eingemessen werden. Das ist einmalige Arbeit und danach dauerhaft.
- ~~Die Reihenfolge: Startwert -> Probeserver -> messen -> eintragen -> Spielprobe.~~ 9.11. GEMACHT, aber anders: Das neu erzeugte Altfurt war BERGLAND (flachstes 32x32-Feld hatte 29 Bloecke Spanne, am 2.11. waren es 2). Auf Vorschlag des Nutzers laeuft die Probe jetzt auf einer FLACHEN Welt - damit ist die Gelaendefrage gar nicht da.
- ~~Dafuer eine vierte Weltart: art: flach~~ 9.11. GEBAUT (Vanillas Superflach ueber WorldType.FLAT, kein eigener Generator - der waere eine Abschrift, die bei jedem Spielupdate nachzuziehen ist). Der Startwert wirkt dort nicht, und das steht an EINER Stelle: Weltangabe.startwertWirkt(), gemeinsam fuer RASTER, LEER und FLACH.
- Der erste Startlauf fand einen Fehler von mir: seed() war fuer FLACH ausgenommen, die ABWEICHUNGSKLAGE nicht - sie klagte dann ueber einen Unterschied, der Absicht ist. Eine Klage ueber etwas Gewolltes nimmt der Leser beim naechsten Mal nicht mehr ernst. Zwei Stellen fuer eine Frage, und keine Probe hielt die zweite.
- Im Probeserver gebaut (ueber die Konsole, also wiederholbar): Torreihe z=0, x 57..85, vier hoch, sieben Oeffnungen 3x3; drei Ladentruhen; Weltspawn 72/-60/12. Flach heisst Boden y=-61, erster freier Block y=-60. Die Quader des Probeservers sind darauf umgerechnet, das Repo behaelt seine Werte fuer die spaetere gewoehnliche Welt.

## Fund vom 18.11.2026: Blockpalette in Paper 26.3

- GEMESSEN an einer frischen Welt (freimarken:altfurt, Paper 26.3): Ein Paletteneintrag in
  `block_states.palette` kommt in DREI Formen vor, und zwar nebeneinander in EINER Sektion:
  `"minecraft:bedrock"` (nackter String), `{"": "minecraft:stone"}` (Name unter dem LEEREN Schluessel)
  und `{"id": "minecraft:kelp", "properties": {"age": "21"}}` - also **id/properties klein** statt
  Name/Properties.
- ~~FOLGE fuer app/services/world_bake_service.py: JEDER Block wird zum Ersatzblock, und der Bake sieht
  aus wie eine Welt aus einem Material.~~ 23.11. BEHOBEN, und zwei Korrekturen dazu - beide gemessen:
  Ein Compound ohne `Name` wurde LUFT (0) und nicht der Ersatzblock, weil die Vorgabe `minecraft:air`
  lautete; ein nackter String WARF einen AttributeError. Grundgestein liegt in jedem Chunk, also warf es
  immer. Der Wurf wurde in `bake_lobby_packets` zu `None`, und `None` sprach der Hub als
  "Kein /setworldspawn in der Lobby-Welt" aus - eine FALSCHE Ursache, die der Betreiber nicht beheben
  kann. Jetzt liest `palette_entry` alle vier Formen an EINER Stelle; `_palette_ids`,
  `diagnose_lobby_bake` und `torreihe_messen.palettenname` rufen sie. Der Bake-Abbruch druckt seinen
  Grund.
- ~~skripte/torreihe_messen.py liest darum alle vier Formen (Funktion `palettenname`). Der Bake ist NICHT
  mitgeaendert.~~ 23.11.: `palettenname` ist ein Einzeiler auf `palette_entry`; die Regel steht im
  Dienst, weil das Skript am Dienst haengt und nie umgekehrt.

## Schritt 14: Kleine Spiele in Altfurt

### Teil A: die Lotterie - 22.11.2026

- ENTSCHIEDEN (Chat): Das erste Los am Tag ist gratis, weitere kosten exponentiell mehr. Viele Gewinne mit Gewichten - Kosmetik, Mark, geringe Mengen Goldmark, besondere Gegenstaende.
- ENTSCHIEDEN (Chat): Parkour erst, wenn Altfurt steht. Angelwettbewerb als Teil B.
- KOSMETIK ist KEINE eigene Gewinnart. Ein kosmetischer Gewinn ist ein `gegenstand` mit eigenem `name` - der Fischerhut ist ein Lederhelm, der so heisst. Damit entscheidet die `config.yml`, was Kosmetik ist, und ein neuer kosmetischer Gewinn braucht keine Zeile Java. Eine vierte Art ohne eine Stelle, die etwas Kosmetisches vergeben KANN, waere eine Zusage ohne Bau.
- Die Steigerung ist EINGESTELLT (Prozent), nicht die Verdopplung wie bei `Preisstaffel`. Preis ist, dass multipliziert statt geschoben wird - darum klemmt `hoechstpreis` in JEDER Rechenrunde und nicht erst am Ende. Ohne das waere der Preis irgendwann negativ, und ein negativer Preis ist eine Gutschrift beim Kauf.
- DAS GRATIS-LOS SPRENGT DAS VORBILD von Tagesbonus und Wechseldeckel: Beide fragen "heute schon?" im Buchungsbuch, weil jede gedeckelte Tat eine Buchung schreibt. Das gratis Los bucht 0 Mark - `summeSeit` bleibt danach 0, und "noch keines gezogen" ist von "das gratis Los ist weg" nicht zu unterscheiden. Auch Betraege helfen nicht: Zwei Lose zu 500 sehen aus wie eines zu 1000.
- DARAUS FOLGT die Reihenfolge: Grenze, Ziehung, Platz, ZAEHLUNG, Geld, Auszahlung. Die Zaehlung steht vor dem Geld, weil `abbuchen(0)` immer gelingt - bei "erst zahlen" waere die Zaehlung beim gratis Los der einzige Riegel, und ihr Ausfall eine Geldquelle. So herum kostet ein Ausfall EIN Los: ein Verlust, keine Quelle.
- Die Losablage schreibt AUFSCHLAGEND (`anzahl = anzahl + 1`), anders als `Bonusablage`, die setzt. Die Zahl ist eine ZAEHLUNG und kein Zustand: Zwei Server, die beide ein Los melden, meinen zwei Lose.
- Schemaschritt 36 `lotterie_lose` (spieler, tag, anzahl), Hauptschluessel aus Spieler UND Tag. Ohne den Tag im Schluessel muesste jemand die Zahl beim Tageswechsel zuruecksetzen - ein Lauf, den es nicht gibt und der auf einem ueber Mitternacht abgeschalteten Server nie liefe.
- Gezogen wird aus der SORTIERTEN Tabelle. Begruendung diesmal eine andere als bei `Fangregel`: Die Liste kommt aus `ConfigurationSection.getKeys`, und dass Bukkit die Dateireihenfolge erhaelt, sagt Bukkit nirgends zu - es ist heute so, weil dahinter eine LinkedHashMap liegt.
- VIER Buchungsgruende, je an vier Stellen: `los_gezogen` (AUSGABE, auch bei 0 Mark - wie die erste Parzelle), `los_erstattet`, `lotterie_gewinn`, `lotterie_goldgewinn` (eigenes Buch - `pruefeWaehrung` der Goldkasse wirft fuer jeden Markgrund, auch beim Lesen).
- GOLDMARK hat damit eine DRITTE Quelle neben Kauf und Wechsel. Knapp gehalten: Gewicht 3 von 1000, drei Stueck. Wer sie gross macht, baut einen Weg um den Wechselkurs herum (Plan 9.2).
- Die ausgelieferte Tabelle ist eine SENKE: im Mittel 361 Mark je Los, und ab dem dritten kostet es 1.000. Eine Probe haelt genau das - sonst laeuft die Wirtschaft voll, und an keiner anderen Stelle faellt es auf. Die Zahl steht im Startbericht.
- Eigene Hilfegruppe "Kleine Spiele in Altfurt" statt eines Eintrags unter Geld: Die Lotterie kostet Geld und bringt Geld, aber sie ist kein Handel - neben dem Bundesmarkt saehe sie aus wie ein Weg zu verdienen.
- Im Manager eine neue Dashboard-Seite "Spiele". Die beiden Katalogproben waren rot, bevor sie dastand - ein neuer `config.yml`-Abschnitt kann nicht stumm durchfallen.
- FUND der Proben (Namenskollision): Meine private Hilfsmethode hiess `eineZeile`, und `VorlageTest` 10i sucht genau diese Zeichenfolge, um jede Holstelle eines Satzes zu finden. Umbenannt in `gewinnzeile` - die Probe hatte recht, ein zweiter `eineZeile` in einem Leser, der keine Saetze holt, verwirrt auch einen Menschen.
- FUND der Bildtafel: In der Gewinnzeile eines gratis Loses stand "(Los: 0)" - genau die Zeile, gegen die der ganze Abschnitt gebaut ist. Jetzt ein WORT aus der Sprachdatei (`lotterie.gratis-wort`).
- FUND der Bildtafel: Die Betraege standen ohne Einheit da ("kostet 1.000"). Jetzt ueber `sprache.betrag` und `sprache.goldmark` - von dort kommt jeder Betrag, den ein Spieler liest, und die Methode heisst nach der Waehrung, damit keine Zeile 3 Goldmark als 3 Mark zeigt.
- FUND der Bildtafel: Zwei Saetze bekamen eine Zaehlform (`hoechste`), die sie als `Art.SATZ` nicht haben. Jetzt `1L`, wie das Haus es ueberall tut.
- FUND der Befehlsprobe: `LotterieBefehle` rief `Vorratshand.fuer` direkt - eine harte Bukkit-Bindung mitten im Befehl. `new ItemStack(Material)` braucht die Registry des Servers und WIRFT auf dem Probenklassenpfad; der Gegenstandsweg, also der haeufigste Gewinn, waere der eine ungepruefte Zweig geblieben. Jetzt wird die Naht hereingegeben, wie bei `ParzellenBefehle`.
- FUND meines Einsetz-Skripts: Ein non-greedy `.*?` ueber Tafel-Eintraege griff von einem Haus-Eintrag mit leerem Bild ueber den naechsten hinweg und verschluckte eine Zeile. Gemessen 22 statt 23, der Treiber brach ab. Muster geschaerft statt Zahl angepasst.
- Proben: `LotteriewerteTest` (28), `LosregelTest` (14), `LotteriebuchTest` (22), `SpeicherLoseTest`/`MariaLoseTest` ueber den geteilten `LosVertrag` (je 9, dazu 2 beziehungsweise 3 eigene), `LotterielisteTest` (18), `LotterieBefehlTest` (13). Die MariaDB-Proben liefen wirklich gegen das Testschema, nicht uebersprungen. Voller Lauf 4.818 -> 4.964, 0 Fehler; Manager 2.171, 0 Fehler.
- ~~OFFEN: der Ort in Altfurt. Ein Losschalter braucht einen Platz, und die zehn Platzhalterbauten stehen noch nicht.~~ 23.11.: Die Bauten sind eingetragen und werden beim Start gesetzt. Der Losschalter kann jetzt einen Platz bekommen - naheliegend das Kaufmannskontor (x 86..94, z 14..22). Der Befehl läuft weiter von überall, bis das entschieden ist.

### Teil B: der Angelwettbewerb - 22.11.2026

- ENTSCHIEDEN (Chat): Taeglich zur festen Uhrzeit, aber alles frei einstellbar. Topf aus Startgeldern PLUS einem Zuschuss des Betreibers.
- DIE ERSTE UHRZEIT DES PROJEKTS. Bis hierher kannte das Plugin nur die Tagesgrenze und Dauern in ganzen Sekunden; weder `LocalTime` noch `DayOfWeek` kamen irgendwo vor. Daraus folgen drei Dinge, die in `Wettbewerbswerte` ausdruecklich stehen.
- EIN FENSTER KANN UEBER MITTERNACHT GEHEN (23:30 plus 60 Minuten). Es gehoert dann zum Tag seines BEGINNS, und `laufAm` prueft deshalb ZWEI Tage. Wer nur den heutigen prueft, haelt es um 00:10 fuer geschlossen - der Wettbewerb endete um Mitternacht, ohne dass es irgendwo steht.
- DIE ZEITUMSTELLUNG ist gemessen und nicht angenommen: Im Fruehjahr gibt es 02:30 nicht, im Herbst zweimal. `ZonedDateTime` entscheidet das selbst und verschiebt nach vorn; der Lauf faellt NICHT aus und ist genau so lang wie eingestellt. Zwei Proben halten beide Naechte.
- DER ZUSTAND LIEGT IN DER DATENBANK, anders als bei allen fuenf vorhandenen Tageslaeufen. Bei denen ist der fluechtige Merker richtig, weil die Wahrheit im Buchungsbuch steht; bei Wettbewerbspunkten gibt es keine, und ein Neustart mitten im Fenster loeschte Punkte UND Anmeldungen - die Startgelder waeren abgebucht und der Topf weg.
- ZWEI RIEGEL, beide in der Ablage und beide EIN Zug: Die Anmeldung gibt beim zweiten Mal `false` (sonst kostet ein Doppelklick doppelt), der Abschluss gibt beim zweiten Mal `false` (sonst zahlt JEDER Takt denselben Topf erneut aus - eine Geldquelle, die mit jedem Takt waechst). Dasselbe Muster wie `wolke_herausforderung` und `rundgang`.
- DAS EINGEZAHLTE STARTGELD STEHT IN DER ZEILE, und das ist die tragende Spalte: Der Topf ist die Summe dieser Betraege plus dem Zuschuss. Wer ihn aus "Teilnehmer mal Startgeld" rechnet, rechnet falsch, sobald der Betreiber das Startgeld mitten im Lauf aendert - `/freimarken neuladen` wirkt sofort.
- ANMELDEN IST EINE EIGENE TAT und kein Nebeneffekt des Angelns: Wer im Fenster an einem Teich steht, soll nicht ungefragt Startgeld zahlen. Nur wer eine Zeile hat, sammelt Punkte.
- DIE REIHENFOLGE IST SPIEGELBILDLICH ZUR LOTTERIE: Dort wird zuerst gezaehlt, dann gezahlt (ein ungezaehltes gratis Los waere eine Quelle). Hier zuerst gezahlt, dann die Zeile geschrieben (ein Teilnehmer ohne Einzahlung nimmt aus einem Topf, in den er nichts gab). Von zwei Fehlern nimmt man den, der nichts erzeugt.
- DIE WERTUNG HAENGT AUF MONITOR, wie die Verdienstwache - nicht auf HIGH wie die Angelwache. Jene TAUSCHT den Fang; zaehlte die Wertung davor, waere ein Meeresherz ein Kabeljau, und die 100 Punkte der Tabelle waeren Punkte, die niemand je bekommt. Tafel 31ax haelt jetzt alle drei Stufen.
- KEIN eigener Zeitgeber: Die Wache ist der achte Mitfahrer der Spielzeitwache. Sie tut zwei Dinge je Takt - ansagen, wenn ein Fenster aufgeht, und abrechnen, wenn eines zu ist.
- FUND, selbst gefunden, bevor eine Probe ihn fand: Das Feld `wettbewerbswache` stand im Plugin und wurde NIE gerufen. Ein null ist dort erlaubt, also waere der Mitfahrer stumm nie gelaufen - kein Fenster angesagt, kein Lauf abgerechnet, kein Preis gezahlt, und keine Startmeldung dazu. Tafel 31ax prueft jetzt auch den Aufruf.
- FUND im eigenen Entwurf: `faelligerLauf` hatte sich die Zone mit `ZoneId.systemDefault()` geholt - das ist die der JVM und nicht die eingestellte. Die Frage steht jetzt im Buch, wo der Wirtschaftsstand liegt.
- FUND der Probe (GrenzeTest 30): Die sieben Wochentagsworte galten als "nirgends benutzt". Sie wurden benutzt - aber nur innerhalb ihrer eigenen Aufzaehlung, und die zaehlt die Probe mit Begruendung nicht. Die Zuordnung `DayOfWeek` -> Satz gehoert zum VERBRAUCHER; verschoben, nicht ausgenommen.
- GEMESSEN, nachdem die Probe zweimal das Gegenteil behauptet hatte: `faelligerLauf` nennt IMMER den juengsten Lauf, dessen Fenster zu ist - vor und waehrend des heutigen also den von GESTERN. Das ist noetig: War der Server ueber Nacht aus, holt der erste Takt nach dem Start die Abrechnung nach.
- YAML LIEST `18:00` NICHT ALS TEXT. Ohne Anfuehrungszeichen ist der Doppelpunkt ein Trenner, und der Leser bekommt eine Abbildung. Die Klage nennt genau diesen Grund - sonst sucht der Betreiber den Fehler in seiner Uhrzeit und nicht in den Zeichen darum.
- EINE KAPUTTE ZAHL KOSTET DIE GANZE VERTEILUNG, anders als bei den Punkten: Die Plaetze haengen voneinander ab. Faellt der zweite weg, bekaeme der Dritte den Preis des Zweiten, und niemand haette es gelesen.
- DREI Buchungsgruende, je an vier Stellen: `wettbewerb_start`, `wettbewerb_preis`, `wettbewerb_erstattet`. Die Differenz der ersten zwei Summen IST der Zuschuss des Betreibers - das ist die Zahl, die er im Wirtschaftsbericht sehen will.
- Schemaschritte 37 (`wettbewerb_punkte`) und 38 (`wettbewerb_lauf`). ZWEI Tabellen, weil der Abschluss dem LAUF gehoert und nicht einem Spieler; als Spalte an der Punktzeile ginge die Frage "ist dieser Lauf abgerechnet" je Zeile anders aus.
- Im Manager auf der Seite "Spiele", neben der Lotterie.
- Proben: `WettbewerbswerteTest` (21, darunter beide Umstellungsnaechte), `WettbewerbsregelTest` (19), `WettbewerbsbuchTest` (22), `SpeicherWettbewerbTest`/`MariaWettbewerbTest` ueber den geteilten `WettbewerbVertrag` (je 16, dazu 2 beziehungsweise 5 eigene), `WettbewerbslisteTest` (17), `WettbewerbBefehlTest` (13), `WettbewerbswacheTest` (8). Voller Lauf 4.964 -> 5.123, 0 Fehler (core 1.932, daten 435, paper 2.756); Manager unveraendert 2.171, 0 Fehler.
- FUND der eigenen Gegenlese: `Wettbewerbsregel.alsText` rief NIEMAND ausser der Probe - auf Verdacht gebaut ("fuer den Bericht") und nirgends gelandet. Das Gegenstueck zu einer Zusage ohne Bau: Bau ohne Zusage. Entfernt.
- FUND der eigenen Gegenlese, dahinter: Die Abrechnung verteilt GELD und hinterliess im Serverprotokoll keine Zeile - anders als Mietlauf, Verfallslauf und Ladengebuehrlauf, die alle protokollieren. Die Tabelle haelt Topf und Teilnehmerzahl, aber nicht, WER was bekam. Jetzt EINE Zeile je Abrechnung mit jedem Sieger, seinen Punkten und seinem Preis.
- OFFEN: der Ort in Altfurt und ein Spieltest. Der Wettbewerb wertet in JEDER Welt - eine Beschraenkung waere eine Entscheidung ueber den Server, die der Betreiber nicht getroffen hat.

### Teil C: Altfurt wird gebaut - 23.11.2026

- ENTSCHIEDEN (Chat): Bauhand IM PLUGIN und nicht als Einmal-Skript. Ein von Hand gesetztes Haus überlebt keinen Weltverlust, und keine Probe hält, dass der Quader in der `config.yml` zu dem passt, was wirklich steht. Jetzt ist der Quader die Wahrheit, und das Haus folgt ihm.
- NEUN BAUTEN, nicht zehn: Die Markthalle steht seit dem 11.11.2026. `bauten.haeuser` nennt die vier Torhäuser (Hafen, Forsttor, Kornpforte, Wolkenturm), die vier Flankenhäuser (Bürgeramt, Zunfthaus, Münzhaus, Kaufmannskontor) und das Bundesratshaus.
- DIE TORHÄUSER STEHEN VOR DER WAND (z −5..−1), und das ist der tragende Zug: Drei der vier Tore liegen mitten in der Mittelachse x 65..79, die frei bleiben muss - dort läuft jeder Ankommende durch. Hinter der Wand hätte jedes Torhaus den Weg verbaut.
- GESETZT WIRD NUR, WAS FEHLT. Gefragt wird EIN Block: die Wandecke eine Ebene über dem Boden - nicht der Boden, denn in einer Ebene liegt der überall. Ein Haus mit abgetragener Rückwand gilt als stehend; der Start soll nicht über die Einrichtung eines Betreibers hinwegbauen.
- DER GANZE QUADER WIRD GESCHRIEBEN, Luft eingerechnet - wie bei `Wolkenaufbau`. Dadurch sind Aufsetzen und Zurücksetzen dieselbe Hand.
- Die Geometrie steht im KERN (`Bauplan`, ohne Bukkit), die Blöcke im Plugin (`Baustoffe`, aus der `config.yml`). Dieselbe Trennung wie `Wolkenplan`/`Wolkenboden` - darum ist die Regel ohne laufenden Server prüfbar.
- EIN BAU AUF EINEM TOR WIRD VERWORFEN, und diese Prüfung gibt es bei den Stationen nicht: Eine Station liest nur, ein Bau SETZT. Ein Quader über einem Tor mauerte den Durchgang zu.
- GEMESSEN vor dem Bauen: alle neun Quader gegen die acht Tore, die Torwand, die drei Ladeneinheiten, die Mittelachse und die Ladenschutz-Lücke - 0 Kollisionen. Die Messung ist jetzt ein Riegel (`BautenlisteTest` 11 liest die AUSGELIEFERTE Datei).
- Die vierte Abschrift der Zahlenprüfung ist nicht entstanden: `Zahlenzeile` hält sie an einer Stelle, und die 30-Millionen-Grenze (Paper stürzt beim Teleport darüber ab) gilt damit auch für Bauten, Ladeneinheiten und Stationen - vorher nur für Tore.
- Proben: `BauplanTest` (5), `PlatzhalterbauTest` (7), `BautenverzeichnisTest` (7), `AltfurtaufbauTest` (11), `BautenlisteTest` (11), Tafel 31dp (die zweite Naht ist verdrahtet und setzt ohne Physik - das ist der eine Teil, den keine Probe erreicht). Vierzehn Gegenläufe, alle rot. core 1932 -> 1951, paper 2766 -> 2789.
- FUND der eigenen Tafeln, zweimal: `GrenzeTest.keinDokublockOhneDeklaration` fing meinen Einschub zwischen fremder Doku und ihrer Deklaration (zum dritten Mal dieselbe Falle), und `jederWeltnameStehtInDerListe` verlangte `bauten.welt` in `NUR_BEIM_START_WELTEN`. Tafel 31da verlangt den Vermerk in GROSSBUCHSTABEN.
- Offen: der Rundgang bekommt seine Stationen. Vorschlag, geprüft (keine Überschneidung, alle unter 64 je Achse, die drei Ladeneinheiten liegen in der Markthalle):

```yaml
  stationen:
    buergeramt:   { von: [50, -61, 2],  bis: [58, -53, 10] }
    zunfthaus:    { von: [50, -61, 14], bis: [58, -53, 22] }
    markthalle:   { von: [59, -61, 4],  bis: [85, -53, 22] }
    muenzhaus:    { von: [86, -61, 2],  bis: [94, -53, 10] }
    bundesratshaus: { von: [64, -61, 26], bis: [80, -51, 38] }
```

- Offen: die sechste Station aus Plan 10.2 ist das Stadttor in die Marktstadt. Dort steht noch kein Bau - ein Torhaus vor dem Marktstadt-Tor (x 58..60) wäre der naheliegende zehnte.
- ~~Offen: ein Platzhalter hat keine Schilder und keine Einrichtung - wer echte Türen und Schilder will, braucht eine zweite Naht neben `Weltgriff.setze`.~~ 23.11. GEBAUT: `Altfurtaufbau.Feingriff`, in der Gestalt von `Wolkenaufbau.Truhengriff` und aus demselben Grund - `Material.createBlockData()` geht über `Bukkit.getServer()` und wirft ohne Server. Die Tür wird mit BEIDEN Hälften gesetzt, ohne Physik; das Schild hängt INNEN über der Tür (außen läge es jenseits des Quaders und damit außerhalb jeder Kollisionsprüfung) und trägt den Stationsnamen aus der sprache.yml - keine fünfte offene Satzfamilie. Ein Bau von drei Blöcken Höhe bekommt keine Tür: Sie braucht zwei Ebenen, und die zweite wäre dort das Dach.
- Offen: ein Platzhalter hat weiter keine EINRICHTUNG - keine Truhen, keine Werkbänke, keine Teleporter zu den Freistädten. Was in jedem Haus stehen soll, nennt Plan 2.1 je Gebäude; gebaut ist davon nichts.
- Offen: die vier Abschriften der Zahlenprüfung (`Ladenliste.drei`, `Rundgangsliste.drei`, `Weltenliste.ecke`, `Truhenliste`) gehören auf `Zahlenzeile` gezogen. Die Worte bleiben je Rufer - sie stehen in Proben. Eigener Schritt, damit die Altfurt-Änderung lesbar bleibt.

## Schritt 12a: Dashboard im Manager

### Teil A: die Einstellungsseiten - 22.11.2026

- ENTSCHIEDEN (Chat schlaegt Plan): Stammdaten bleiben in `plugins/Freimarken/config.yml`, nicht in der Datenbank. Plan 12a.2 beschreibt den Weg ueber MariaDB; gebaut ist er auf BEIDEN Seiten anders - die 21 Tabellen des Plugins halten ausschliesslich Spieldaten, und jede einstellbare Zahl liest das Plugin aus der Datei. Die drei Regeln aus 12a.2 gelten unveraendert, nur steht an der Stelle "Datenbank" jetzt "Datei".
- Dafuer spricht die Datei selbst: 2327 ihrer 3071 Zeilen sind Kommentar. Sie sind das Handbuch der Anlage, und der Manager hat mit `yaml_assistant_service` schon einen Schreiber, der sie byteweise erhaelt.
- GEBAUT: Bereich "Freimarken" mit neun Seiten (Wirtschaft, Waehrung, Raenge, Berufe, Laeden, Welten, Betrieb, Texte, Protokoll). Felder, Werte und ihre Erklaerung kommen aus der Datei; der Katalog in `app/assets/freimarken_felder.yml` nennt nur ABSCHNITTE. Damit sind alle 549 Felder erreichbar, und ein neues Feld des Plugins erscheint von selbst.
- Rechte zweidimensional: Rolle (lesen ab Moderator, aendern ab Admin) UND `can_edit_server_files` fuer diesen Server. Der Bereich schreibt eine Serverdatei und ist damit ein zweiter Weg zu einer Tat, die der Dateieditor schon kennt - ohne die zweite Frage waere er der Weg um das Dateirecht herum.
- Der SPRUNGSCHUTZ wird bedient, nicht nachgebaut. `Sprungregel`/`Grenzregel` im Plugin pruefen gegen die AUSGELIEFERTE Vorgabe; das Dashboard stellt die Rueckfrage und schreibt danach Wert UND `wirtschaft.sprung-bestaetigt.<name>`. Ein zweiter Riegel im Manager haette sich mit dem ersten gegenseitig unpruefbar gemacht.
- Die 19 Zuordnungen Wertpfad -> Sprungschluessel sind aus den Aufrufen von `Wirtschaftsliste.betrag` GEERNTET, nicht geraten: `wechsel.mark-je-goldmark` heisst dort `wechsel-hin`, und nur 7 der 19 stehen in einem Kommentar. Eine Probe haelt sie gegen die ausgelieferte Datei vollstaendig.
- FUND: `write_text_file` schreibt ohne `newline`-Angabe. Unter Windows waere aus einer Aenderung an einem Wert ein CRLF-Umbau aller 3071 Zeilen geworden. Der Freimarken-Dienst liest und schreibt mit `newline=""`; zwei Proben halten beide Richtungen.
- FUND: Der vorhandene Dateieditor sieht wegen `MAX_FELDER = 400` nur 401 der 549 Felder - 148 sind dort nur im Freitext erreichbar. Die neue Funktion `felder_nach_pfad` hat die Grenze nicht (sie zeichnet nichts).
- FUND: Der Dienst sammelte unbekannte Pfade in eine Liste, die der Router nie las. Ein Wert, dessen Schluessel ein Plugin-Update umbenennt, waere stumm verschluckt worden - der Nutzer haette "Es hat sich nichts geaendert" gelesen. Wird jetzt benannt.
- FUND beim Beheben: Mein erster Patch meldete bei einem unbekannten NEBEN einem gueltigen Pfad "nichts geschrieben" und uebersprang per `return` die Protokollzeile fuer das, was sehr wohl geschrieben war. Es gibt nur EINEN Flash je Durchlauf, darum wird der Satz jetzt zusammengesetzt und das Protokoll steht unbedingt davor.
- DREI RIEGEL FUER EINE TAT, zwei davon blind: Ein Formularfeld, das keine Einstellung ist, fing der `feld:`-Filter im Router, die Pfadpruefung im Dienst UND `_knoten_an_pfad` im YAML-Assistenten. Nur der dritte ist tragend und war laengst geprueft. Die Proben pruefen jetzt je Riegel den Fall, in dem NUR er greift.
- Dabei erneut in die eigene Notiz gelaufen: Die Probe nahm 99999 gegen eine Vorgabe von 25000 - ein grosser Sprung, also kam die Rueckfrage und geschrieben wurde ohnehin nichts. Gruen ohne den Riegel zu beruehren. Jetzt 30000, und geprueft wird der TEXT der Meldung, nicht ob der Pfad irgendwo vorkommt (der Assistentenfehler nennt ihn auch).
- Einspritzungsrunde: 8 Faelle, 8 gefangen, jeder von der Probe, deren Name den Fall nennt; danach derselbe gruene Stand. Dazu 4 Faelle gegen den Feldkatalog, alle gefangen.
- Proben 1892 -> 1951 (Katalog 18, Rechte 14, Schreiben 27). Voller Lauf gruen, keine fremde Probe beruehrt.
- ~~OFFEN: Texte und Protokoll sind als Seiten angelegt, aber noch nicht gebaut.~~ 22.11. PROTOKOLL GEBAUT: wer, wann, von was auf was, warum - je Server und nur `freimarken.*`. Einspritzungen 6/6; der wichtigste Fall ist der Aktionsfilter, denn diese Seite ist ab Moderator offen und das allgemeine Audit-Log ist Super-Admin-Sache. Faellt der Filter, zeigte sie einem Moderator Anmeldungen und Dateizugriffe.
- Der Trenner " | Grund: " steht als EINE Konstante da (Schreiben und Lesen brauchen ihn). Eine Quelltext-Zusage haelt, dass das Literal nur einmal vorkommt - sonst setzte eine Aenderung alle alten Zeilen stumm auf "kein Grund angegeben". Proben 1951 -> 1969.
- ~~OFFEN: die Texte-Seite (sprache.yml).~~ 22.11. GEBAUT (Commit edf2fc0): `sprachabschnitte`/`sprachfelder` im Dienst, POST `/freimarken/{id}/texte` und `/texte/zuruecknehmen`, Anzeige im Zweig `quelle == "sprache"`; neue Schluessel werden FLACH angehaengt, vorhandene ersetzt. `tests/test_freimarken_texte.py` (24 Proben). Die Messung darunter bleibt stehen - sie ist der Grund fuer die gebaute Form:
  - ZWEIDATEIFORM: `sprache-vorlage.yml` wird bei JEDEM Start neu geschrieben (`saveResource(VORLAGE, true)`) - dort zu schreiben waere beim naechsten Start weg. Die Betreiberdatei `sprache.yml` wird LEER angelegt und enthaelt nur Ueberschreibungen; der Rueckfall gilt JE SCHLUESSEL.
  - FOLGE: Die Seite muss die Vorlage ANZEIGEN (778 Werte samt Erklaerung) und in die Betreiberdatei SCHREIBEN - und dort einen Schluessel ANLEGEN, den es noch nicht gibt. `yaml_assistant_service.schreibe` kann das nicht, es ersetzt nur vorhandene Werte.
  - GEMESSEN 22.11. an zwei Wegwerfservern (V25_sprachform.py): Ein FLACHER Schluessel `mark.verlauf.kopf: "..."` auf oberster Ebene wirkt. Startmeldung: "Sprache: 1 von 770 Saetzen kommen aus deiner sprache.yml" und "deine: mark.verlauf.kopf". Anhaengen genuegt also, ein Blockaufbau ist nicht noetig.
  - FALLE, ebenfalls GEMESSEN: Stehen BEIDE Formen fuer denselben Pfad in der Datei, meldet der Start wieder nur "1 von 770" und KEINE Klage - eine Fassung hat die andere stumm ueberschrieben (`hinein.put`, der spaeter gelesene gewinnt). Die Dublettenpruefung sieht es nicht, sie vergleicht Schluesselnamen je Ebene und nicht Pfade. FOLGE: Der Manager muss vor dem Anhaengen pruefen, ob der Pfad in IRGENDEINER Form schon dasteht - `felder_nach_pfad` tut das, es liefert Pfade unabhaengig von der Schreibform.
  - Nebenbei berichtigt: es sind 770 Saetze, nicht 231 (die Zahl im Javadoc von Sprachliste ist alt) und nicht 778 (das sind alle Werte der Vorlage samt Farben).
  - Die Platzhalter prueft das Plugin schon (`Sprachliste.fehlendeNamen`/`fremdeNamen`) - also bedienen, nicht nachbauen. Der Manager braucht die erwarteten Namen nicht zu kennen: Es genuegt die Differenz zum ALTEN Wert ("dieser Satz hatte {spieler}, dein neuer nicht mehr").
  - Gemessen: 371 Werte tragen `{platzhalter}` (131 Namen), 624 tragen `<tag>`. Von den 24 Tagnamen sind 16 die acht FARBROLLEN, exakt paarig (339/339, 215/215, ...); die anderen 8 sind escapte Angabeplaetze in Befehlshinweisen (`\<name>`), keine Tags. Nur die Rollen sind paarig pruefbar.
  - Die Kommentare nennen die Platzhalter NICHT verlaesslich: 39 von 638 vollstaendig, 332 gar nicht. Als Quelle unbrauchbar.
- ~~OFFEN: MariaDB-Treiber und die drei Spieldaten-Seiten. Der Manager hat KEINEN MySQL/MariaDB-Treiber in `requirements.txt`.~~ 22.11. BEIDES GEBAUT: Treiber `pymysql==1.1.1` (requirements.txt) in Teil C, die drei Seiten in Teil D.

### Teil B: Zugangsdaten, Besitzbefehl, Geldschranke - 22.11.2026

- FUND, behoben: `nicht-im-dashboard: datenbank` stand mit Begruendung im Katalog, und KEIN Programmpfad las die Abbildung. `gesperrt` kannte nur `welten.*` und `parzellen.*`. Ein untergeschobenes `feld:datenbank.passwort` wurde also geschrieben - und `protokoll_text` legte altes UND neues Kennwort im Klartext ins Protokoll, das ab Moderator zu lesen ist.
- Die Regel steht jetzt an EINER Stelle: `sperrgrund(pfad)` fragt beide Quellen - Muster unter `gesperrt` treffen eine Ebene, ein Abschnitt unter `nicht-im-dashboard` sperrt seinen Unterbaum. Einspritzungen 4/4, darunter "Zustand vorher".
- Nebenbei berichtigt: der Katalogtext nannte `plugins/Freimarken/datenbank.properties`. Die Zugangsdaten stehen in `plugins/Freimarken/config.yml` unter `datenbank`; das Einrichtungsskript legt sie unter `data/freimarken/datenbank.properties` ab.

#### Plugin: /freimarken besitz

- ENTSCHIEDEN (Chat): Die Besitzkorrektur wird im Plugin gebaut, nicht in der Datenbank. Es gab vorher KEINEN Befehl, der Besitz uebertraegt oder fremden Besitz freigibt; der einzige Besitzerwechsel war `Grundstuecksbuch.kaufe` und braucht ein Angebot.
- GEBAUT: `/freimarken besitz uebertragen <x> <z> <spieler>` und `freigeben <x> <z> bestaetigen`, eigenes Recht `freimarken.besitz` (nicht `freimarken.geld`, nicht `freimarken.verwalten`), konsolenfaehig ueber `getSender`. Regel in `Betreiberbesitz` - ohne Bukkit, ohne Text, wie `Betreibergeld`.
- `BesitzLager.eigentuemerSetzen(id, neu)` setzt den Eigentuemer UND leert die Mitbauer - EIN Vorgang. Zwei Schreibzuege liessen bei einem Abbruch dazwischen die Mitbauer des Vorbesitzers auf fremdem Grund weiterbauen; in MariaDB haelt es eine Transaktion.
- Die Regeln baut der Zweig nicht nach: Er setzt den HEUTIGEN Eigentuemer als Handelnden ein, damit `hindernis` vollstaendig gilt. Der Betreiber greift nicht an der Eigentuemerpruefung vorbei, er tritt an ihre Stelle.
- FUND aus der Probe: `zustaendigerBesitz` gibt bei einer Gruppe immer das KIND zurueck, auch an der Ankerzelle. Mit `AufGruppe.VERWEIGERN` waere ein zusammengelegtes Grundstueck ueber KEINEN Weg uebertragbar - und `auftrennen` kann nur sein Eigentuemer, der womoeglich weg ist. Uebertragen laeuft darum auf `ERLAUBT` + `schreibZiel` (ganzes Grundstueck), Freigeben bleibt bei `VERWEIGERN` (es loescht).
- FUND aus der Probe: `AUSSERHALB` kann `hindernis` gar nicht erzeugen - nur `nehmen` tut das. Der Ausgang war eine Zusage ohne Bau und ist heraus; jenseits der Grenze heisst die Antwort `KEIN_BESITZ`, und das Argument des Befehls haelt sie ohnehin ab.
- Die RANGOBERGRENZE wird bewusst NICHT geprueft. `Rangzahl.ausRecht` nimmt einen `Player`: Rechte sind nur fuer einen Anwesenden lesbar, und eine Korrektur betrifft meist den, der nicht da ist. Dazu ist die Grenze keine Zusicherung ueber die Daten - nach einer Herabstufung haelt ein Spieler ohnehin mehr. Die Antwort nennt stattdessen die neue Zahl.
- Proben: `BetreiberbesitzTest` (14) und `FreimarkenBefehlTest` 44m-44q (5). Kern und Paper zusammen 4813 Proben, 0 Fehler.
- Tafel nachgezogen: `GrenzeTest` 31c, `FreimarkenPlugin.java` 44 -> 50 Sendestellen. Zahl aus dem ROTEN Lauf.

#### Teil C: Treiber, Spieldaten, Mitgliedschaften - 22.11.2026

- ENTSCHIEDEN (Chat): Treiber jetzt dazunehmen, Besitz als Plugin-Erweiterung, Mitgliedschaftstabelle jetzt anlegen, Geldkorrekturen auf eine neue Super-Admin-Stufe.
- GEBAUT: `pymysql==1.1.1` - reines Python. `mariadb` braeuchte MariaDB Connector/C, `mysqlclient` eine C-Erweiterung; installiert wird mit `pip install -r requirements.txt` ins `.venv` (install_service.ps1), und dort gibt es keinen Compiler.
- Die zweite Engine entsteht HINTER einer Funktion mit Zwischenspeicher, nicht auf Modulebene. `app/db/session.py` liest seine Einstellungen einmal beim Import - genau deshalb muss `tests/conftest.py` fuenf Module neu laden. So gilt ein in der config.yml geaenderter Rechner beim naechsten Aufruf.
- KEINE ORM-Modelle fuer die 21 Plugin-Tabellen, nur `text()`-Abfragen. `init_db` ruft bei jedem Start `create_all`; ein Modell fuer `parzellen` waere der Weg, auf dem der Manager eines Tages beim Start in die FREMDE Datenbank schreibt.
- Nur lesend wird GEPRUEFT: Eine Abfrage, die nicht mit SELECT beginnt, wird abgewiesen, bevor die Verbindung gebaut wird. Haenge sie dahinter, waere der Riegel auf einem Rechner ohne MariaDB nie geprueft.
- Fristen aus dem Plugin uebernommen (3 s Aufbau, 5 s Antwort - `Datenbank.java`), dazu `pool_pre_ping` und `pool_recycle` 1800 s: `Datenbank.verbindung()` sagt selbst, dass MariaDB untaetige Verbindungen nach Stunden schliesst.
- Das Kennwort bleibt in der config.yml des Servers. `Zugang.__repr__` ist ueberschrieben, der Engine-Schluessel traegt nur eine Pruefsumme, und keine Fehlermeldung reicht die Treiberzeile durch.
- Zeitstempel: pymysql gibt DATETIME OHNE Zone zurueck, das Plugin schreibt in UTC. Werden gekennzeichnet; die sechs DATE-Spalten bleiben unberuehrt, sie stehen in Ortszeit.
- Namensaufloesung an EINER Stelle, aus `usercache.json` und den drei Zugangslisten. Kein Netz im Anfragepfad - ein Namensaufruf blockiert 5 s ohne Sammelabfrage, eine Liste mit fuenfzig Zeilen waere fuenfzig Anfragen hintereinander. Rueckfall sind die ersten acht Zeichen der Kennung (`Spielername.ZEICHEN_DER_KENNUNG`).
- Stadtkassen werden erkannt: `parzellen.kasse` steht in `kassen.spieler`, eine Freistadt saehe sonst als Spieler mit einem achtstelligen Kuerzel in der Kontenliste.
- Mitgliedschaften: Manager-Tabelle `freimarken_mitgliedschaften` (wer, seit wann, bis wann, welche Zahlung, Grund), je Server eine Zeile - der Zuschlag wirkt ueber ein Recht, und ein Recht wird auf EINEM Server vergeben.
- Der Manager VERGIBT das Recht nicht, er NENNT es. `/lp` waere ein Konsolenbefehl an ein fremdes Plugin, dessen Form sich mit jeder Fassung aendert; ein Knopf, der bei einer anderen Rechteverwaltung stumm nichts tut, waere schlimmer als eine Zeile zum Kopieren.
- FUND: `Rangzahl.mitZuschlag` rechnet mit `Math.max(zahl, 0)`. Eine Mitgliedschaft mit 0 oder negativer Zahl wuerde gespeichert, das Recht vergeben - und stumm nichts bewirken. Wird abgewiesen.
- `rechtezeilen` nennt je Spieler und Zuschlag nur die GROESSTE Zahl: `Rangzahl.hoechsteAus` addiert sie einmal, zwei laufende Mitgliedschaften geben nicht die Summe.
- DREI TAFELN LESEN DEN PLUGIN-QUELLTEXT statt sich selbst: die Artenliste gegen das ENUM in `Datenbank.java`, die fuenf Zuschlagsnamen gegen die `RECHT_EXTRA_*`-Konstanten, die Rechtevorsilbe gegen `ParzellenBefehle`. Kommt im Plugin eine sechste Art dazu, wird die Probe rot, die die Sache benennt.
- Proben: `tests/test_freimarken_spieldaten.py` (41). Einspritzungen 17 Faelle, 16 gefangen - der eine Fehlschlag war MEINE Einspritzung (falsche Zeile), nachgeholt und gefangen.
- ~~OFFEN: Die Seiten selbst (Besitz, Konten, Mitgliedschaften) samt Router und Vorlagen.~~ 22.11. GEBAUT - siehe Teil D unten.

#### Teil D: die drei Seiten - 22.11.2026

- GEBAUT: Besitz, Konten, Mitgliedschaften. Lesen ab Moderator, Korrigieren nur Super-Admin (Plan 12a.4) - eine DRITTE Stufe neben Lesen und Stammdaten.
- Die Korrektur laeuft als Konsolenbefehl durch `send_console_command`, also durch dieselbe Engstelle, die Rollenpruefung, Umbruchriegel und Protokoll traegt. Ein zweiter Sendeweg waere der Weg daran vorbei.
- Die Meldung sagt GESENDET und nicht gebucht: `send_console_command` kennt den Ausgang nicht. Sie nennt ausserdem, wo er zu lesen ist - in der Serverkonsole hinter dem Wort `Ausgang`.
- Vor dem Senden geprueft, was der Befehl nicht annimmt: ein Spielername mit Leerzeichen (Brigadier-Wort) und ein Betrag ueber einer Milliarde (LongArgumentType mit Grenzen). Beide fielen sonst in Brigadiers englische Fehlerzeile, und die liest hier niemand.
- Der GRUND steht im Manager-Audit, mit demselben Trenner wie die Stammdaten - das Plugin nimmt keinen Grund. Plan 12a.2 Regel 3 faellt damit auf zwei Buecher: das Was ins Serverlog, das Wer und Warum in den Manager.
- Die Mitgliedschaftsseite nennt je Spieler und Zuschlag nur die GROESSTE laufende Zahl und sagt, dass die Rechteverwaltung des Servers sie setzen muss - nicht der Manager.
- FUND beim Bauen: Fiel die Plugin-Datenbank aus, verschwand die GANZE Seite samt Korrekturformular. Zwei Stellen uebereinander - der Vorlagenzweig hing an `spieldaten.art` (im Fehlerfall nicht vorhanden), und die ganze Seite steckte im `{% else %}` des Lesefehlers. Fuer Einstellungen ist das richtig, fuer Spieldaten falsch: Die Korrektur laeuft als Befehl, nicht durch die Datenbank, die gerade nicht antwortet - und gerade dann will jemand eingreifen.
- FUND beim Bauen: `can_control_server` gibt fuer einen Super-Admin IMMER true zurueck. Meine zweite Frage in `_fuer_korrektur` konnte nie "nein" antworten - der doppelte Riegel, der den daneben unpruefbar macht. Heraus, mit dem Grund im Quelltext; die Probe haelt jetzt das Gegenteil fest.
- FUND in der WAECHTERPROBE des Hauses: `_erreichbarer_quelltext` verfolgte nur EINE Ebene. Sieben POST-Routen dieses Bereichs - drei davon schon vorher da - galten als ungeschuetzt, obwohl die Tore in `_fuer_schreiben` liegen, das erst `_require_stufe` ruft. Vor der Aenderung gemessen: Transitive Verfolgung aendert das Urteil fuer GENAU diese sieben und keine der ueber hundert anderen. Dazu eine Gegenprobe - eine Kette gleicher Tiefe ohne Pruefung muss weiter als ungeschuetzt gelten, und bei Tiefe 1 darf die echte Kette NICHT gefunden werden.
- FUND aus der Einspritzung: Meine Reihenfolgenprobe prueft die EXISTENZ der Route, nicht ihre STELLUNG - nach einem Tausch der Bloecke war sie weiter gruen. Sie vergleicht jetzt Stellungen. Eine Reihenfolge ist keine Zeile, sondern das Verhaeltnis zweier Bloecke.
- Proben: `tests/test_freimarken_seiten.py` (29). Einspritzungen 12/12, darunter der Blocktausch und zwei "Zustand vorher"-Faelle.
- Voller Manager-Lauf: 1969 -> 2104 Proben, 0 Fehler.

#### Teil E: eigener Bereich, eigene Erstellung - 22.11.2026

- ENTSCHIEDEN (Chat): Ein Freimarken-Server erscheint NICHT im Dashboard, wird vollstaendig im Freimarken-Bereich bedient und hat dort einen eigenen Erstellen-Knopf. In allen UEBRIGEN Listen (Backupplan, Zeitplan, Konsolenauswahl, Systemstatus) bleibt er.
- FUND, der die Loesung bestimmt: `hat_freimarken` erkennt an `plugins/Freimarken/config.yml`, und die legt das PLUGIN beim ersten Start an. Ein frisch erstellter Server haette sie nicht - aus dem Dashboard schon verschwunden, im Bereich noch nicht angekommen, also nirgends. Darum ein Feld am Server: `freimarken_enabled`, gesetzt beim Erstellen.
- `hat_freimarken` BLEIBT und beantwortet weiter die andere Frage: ob das Plugin schon einmal lief. Zwei Fragen, zwei Namen.
- NICHT nachgebaut: Die acht Serverseiten (Details, Konsole, Logs, Zeitplan, Backups, Inhalte, Zugang, Dateien) sind schon ein Tab-Set. "Alles im Bereich" heisst darum EINE Leiste fuer beide Gruppen - neue Datei `_bereich_nav.html`, eingebunden von `server_nav.html` UND `freimarken_nav.html`. Dieselben Seiten, zwei Wege hinein, keine zweite Wahrheit; jede kuenftige Aenderung an der Konsole wirkt dort mit.
- Die Dateinamen der beiden alten Leisten blieben: neun Vorlagen binden sie ein, und sie sehen unterschiedlich aus (`server_detail.html` erst nach einem 45-zeiligen Skript).
- Die Seitenliste wurde ein GLOBAL (`freimarken_seiten()` in `pages.py`). Als Kontextwert haette sie in acht Routen nachgereicht werden muessen - und die neunte vergisst sie. Dann iteriert Jinja ueber Undefined, und das ist ein Fehler, keine leere Schleife.
- Die zweite Reitergruppe haengt an ZWEI Bedingungen: am Feld und an der Rolle. Die Serverseiten verlangen nur `can_view`, der Freimarken-Bereich mindestens Moderator - ohne die Rollenfrage bekaeme ein `view_only`-Nutzer zwoelf Reiter, die alle 403 liefern.
- Der Filter sitzt in einem HELFER `list_dashboard_servers`, nicht im Tor `list_servers_for_user`: Jenes versorgt auch Systemstatus, KI-Uebersicht und die Auswahl "Daten uebernehmen" - dort soll der Server bleiben.
- FUND, der sonst erst im Browser aufgefallen waere: `app.js` setzt die Kacheln "Server gesamt" und "Server laufend" aus `/api/resources`, schon beim Laden. Ein Filter nur in `get_dashboard_summary` wirkt Bruchteile einer Sekunde - und KEINE Probe im Haus rief diese Route je auf. Der Helfer wird jetzt von beiden Verbrauchern gerufen, und eine Probe liest die Liste samt ihrer Laenge.
- FUND, schon vorher kaputt: Die Statusspalte der Freimarken-Uebersicht zeigte `ansicht.label` (gibt es nicht) und `style="background: {{ ansicht.color }}"` (color ist ein Schluessel wie `online`, kein Farbwert). Jetzt als Klasse `status-dot-{{ color }}`, wie im Dashboard.
- Die Ein-Server-Abkuerzung der Uebersicht faellt weg: Mit ihr waere der Erstellen-Knopf ab dem ersten Server unerreichbar und beim zweiten wieder da. Ihre Begruendung ("ein Klick zu viel") stimmte, solange die Uebersicht nichts bot.
- Die Erstellungskette fuehrt durch VIER Schichten, die jedes Feld einzeln abschreiben (Formular -> ProvisionServerRequest -> ServerCreate -> Server). Eine vergessene verliert die Markierung ohne Fehler. Alle vier gesetzt, je eine Einspritzung dagegen.
- Die Umleitung nach dem Erstellen ist BEDINGT: ein Freimarken-Server landet im Bereich, ein gewoehnlicher weiter auf `/servers/<id>`. Zwei Proben rechnen die Server-Id aus genau dieser Adresse.
- Was als "ja" gilt, steht an EINER Stelle (`_ist_ja`): Zwei Abschriften liefen auseinander, und dann belegte die Adresse das Haekchen vor, waehrend das Senden einen normalen Server erzeugte.
- Der Klon nimmt die Markierung MIT (anders als `gateway_*`): Das Duplizieren kopiert den ganzen Ordner samt `plugins/Freimarken`. Festgenagelt in der vorhandenen Klon-Tafel, nicht in einer zweiten.
- `update_server_settings` wurde NICHT angefasst. Sie setzt jedes Feld bedingungslos; ein neues Feld mit Vorgabe False loeschte die Zugehoerigkeit bei jedem Speichern der Serverseite. Die Markierung entsteht beim Erstellen und ist dort nicht aenderbar.
- NEU: eine Migrationsprobe fuer `servers` - die gab es gar nicht. In Proben legt immer `create_all` die Spalten an, der ALTER-Zweig laeuft nie; ein vergessener Eintrag waere erst beim Start der echten Datenbank aufgefallen, dann mit "no such column" bei JEDER Abfrage. Die Probe prueft die Spalte UND den Eintrag in der Liste.
- Proben: `tests/test_freimarken_bereich.py` (25), dazu 15 Probenvorrichtungen markiert und die Klon-Tafel erweitert. Einspritzungen 15/15, darunter zwei "Zustand vorher"-Faelle. Voller Lauf 2104 -> 2129, 0 Fehler.
- LEHRE wiederholt: Der Treiber meldete "15 von 15 gefangen (erwartet 14)" und brach ab - die Konstante war falsch gesetzt. Dass sie ueberhaupt da ist, hat den Zaehlfehler sichtbar gemacht.

#### Teil F: der Einrichtungsknopf - 22.11.2026

- ENTSCHIEDEN (Chat): Der Erstellen-Knopf richtet den Server fertig ein, MariaDB eingeschlossen. Das Bedenken (Administratorrechte, winget als Systeminstallation aus einer Webanfrage) war genannt und wurde bekraeftigt.
- GEMESSEN, bevor gebaut wurde: Das Plugin legt seine config.yml mit `saveDefaultConfig()` an, und das ueberschreibt eine vorhandene Datei NIE. Darum braucht es KEINEN Probestart - der Manager legt die ausgelieferte Fassung vorab hin, traegt die Zugangsdaten ein, und das Plugin findet sie beim ersten Start vor.
- GEMESSEN: Gradle laeuft ohne `cmd.exe`. `gradlew.bat` braucht cmd, und cmd nimmt keinen UNC-Pfad als Arbeitsverzeichnis - der Manager sieht das Repo aber genau so. Der Wrapper ist ein Jar mit Hauptklasse: `java -cp gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain`. Exit 0 in 26 s aus dem UNC-Pfad.
- GEBAUT: `freimarken_einrichtung_service` mit fuenf Schritten - Plugin bauen, ablegen, config.yml ablegen, MariaDB einrichten, Zugangsdaten eintragen. Eigener Faden (der Klick blockiert nicht), je Schritt eine Zeile im Stand, Fortschritt auf der Freimarken-Seite, Knopf "Einrichtung wiederholen".
- Jeder Schritt scheitert FUER SICH: Die Kette laeuft weiter, und jeder hinterlaesst seine Zeile - auch der uebersprungene. Ein Fortschritt als Zahl ("3 von 5") sagt nicht, welcher fehlt.
- Die Reihenfolge ist tragend: Die Datenbank kommt VOR dem Eintragen (das Skript schreibt die Properties, aus denen eingetragen wird), die config.yml vor der Datenbank (damit auch bei fehlgeschlagener Einrichtung eine vollstaendige Datei daliegt).

#### Nachlese: zwanzig Funde aus der zweiten Gegenlese - 22.11.2026

- 24 Funde, 20 bestaetigt. Die Rueckgabe war wieder ABGESCHNITTEN - die Liste kam aus dem Journal.
- FUND, der dringendste: Eine Probe von vorgestern schickt POST /servers/create mit `freimarken_enabled=1` und setzte `starte` nicht ab. Seit der Anschlusszeile stiess sie damit die ECHTE Kette an - ein Gradle-Lauf auf der Netzfreigabe und `winget install MariaDB.Server`. Der volle Lauf davor hat das getan. Behoben mit einem Sicherheitsnetz im conftest, nach dem Vorbild des Netzes gegen echte Serverstarts; wer den echten Weg will, markiert sich mit `echte_einrichtung`.
- FUND (Geheimnis): `yaml_assistant_service` nennt in zwei Meldungen den GESCHRIEBENEN Wert. Stand `datenbank.passwort` als unquotierte Ziffernfolge in der Datei, galt das Feld als `int`, der Assistent rief `int()` auf dem Kennwort und warf mit einer Meldung, die es im Klartext nennt - und die stand auf jeder Freimarken-Seite, ab Moderator lesbar. Behoben: kein Fremdtext wird mehr durchgereicht (nur die ART des Fehlers, der Grund ins Managerprotokoll), und `art` ist fuer diese drei Felder immer `text`.
- FUND (Datenverlust): `freimarken_service.schreib_datei` war ein nacktes `open(ziel, "w")` - die 157-KB-config.yml wurde ZUERST auf 0 Byte gekuerzt. Der Einrichtungsfaden schreibt bis zu 35 Minuten nach dem Klick und ist ein Daemon. Jetzt atomar ueber eine Zwischendatei plus `os.replace`, wie `central_storage_service` es schon tut. Das betrifft AUCH den Dashboard-Schreibweg.
- FUND (Datenverlust): `lege_config_ab` prueefte nur `is_file()`. Eine 0-Byte-Datei galt als "schon da", die Zeile behauptete, sie trage die Einstellungen, und die einzige Reparatur meldete gruen und tat nichts - auch `saveDefaultConfig()` half nicht, denn eine leere Datei fehlt nicht. Jetzt wird die Groesse gefragt, und die Zeile sagt, wenn sie eine leere ersetzt.
- FUND (Datenverlust): `trage_zugangsdaten_ein` setzte die drei Werte bedingungslos. Eine von Hand auf einen anderen Rechner gestellte `datenbank.url` wurde damit auf 127.0.0.1 zurueckgedreht (das Skript schreibt dort immer die lokale Adresse), und der Server hing danach an einem leeren Schema. Jetzt gilt: Was schon dasteht, bleibt - dieselbe Haltung wie bei der config.yml.
- NEBENEFFEKT, benannt statt verschwiegen: Dieser Riegel macht den int-Pfad des Kennwortaustritts unerreichbar. Tragend fuer das Geheimnis bleibt der Fremdtext-Riegel, denn der greift bei JEDER Ausnahme; `art="text"` ist die sachlich richtige Angabe, nicht ein zweiter Riegel.
- FUND (haengt): `richte_datenbank_ein` lief mit `capture_output`. Nach der Frist toetet `subprocess.run` nur powershell und wartet dann auf Windows OHNE Frist erneut auf EOF - das kommt erst, wenn der letzte winget-Enkel die Pipe schliesst. Der Stand blieb dann fuer immer auf "laeuft". Jetzt geht die Ausgabe in eine DATEI: kein EOF-Warten, die Frist greift am Prozess.
- FUND (falsche Antwort): Der Wiederhol-Knopf hing an `einrichtung.fertig`, und der Stand liegt im Speicher. Nach dem Neustart - also genau in dem Fall, den der Router als Reparaturweg nennt - war der Knopf weg. Jetzt haengt er am Server und daran, dass gerade keine Einrichtung laeuft; ohne Stand steht ein eigener Satz da.
- FUND (Datenverlust, im Skript): Das Skript erzeugte root- und app-Kennwort, uebergab root dem MSI und schrieb die Datei erst am ENDE. Ein Abbruch dazwischen liess den Installer als Waise den Dienst mit einem Kennwort anlegen, das danach niemand kennt - der naechste Lauf ueberspringt die Installation, erzeugt ein neues und kommt nicht herein. Jetzt werden die Kennwoerter VOR der Installation abgelegt, der Block ist eine Funktion und wird zweimal gerufen.
- FUND (Datenverlust, im Skript), SELBST NACHGEMESSEN: Bei einem UNC-Pfad gibt `Resolve-Path` die Form `Microsoft.PowerShell.Core\FileSystem::\\...` zurueck. `New-Item` und `Test-Path` verstehen sie, `[System.IO.File]::WriteAllText` nicht - es wirft, der Wurf landet im catch, das Skript endet mit Exit 0, und der Manager meldet Erfolg. Jetzt `.ProviderPath`, und die Ablage scheitert LAUT, wenn kein Ordner beschreibbar war.
- FUND (falsche Antwort): `starte` sperrt je SERVER. Zwei Freimarken-Server kurz nacheinander heisst zwei Gradle-Laeufe im selben Verzeichnis. Der Bau hat jetzt ein eigenes, globales Schloss; die uebrigen vier Schritte bleiben je Server parallel.
- Probenluecken geschlossen: `lege_jar_ab` hatte keine einzige Probe (in jeder Kettenprobe war Schritt 2 ein Fehlschlag), die Reihenfolgenprobe holte ihre Erwartung aus `e.SCHRITTE` selbst, und fuer `starte` gab es keine.
- Proben: `tests/test_freimarken_einrichtung.py` (42). Einspritzungen 13/13. Voller Lauf 2129 -> 2166, 0 Fehler.
- OFFEN, bewusst: Der Gradle-Aufruf selbst und die Skriptausgabe sind ungeprueft - ein echter Lauf gehoert nicht in eine Probe. Und ein Managerende laesst java bzw. powershell als Waisen stehen; dagegen hilft nur ein Job-Objekt, und das ist ein eigener Schritt.

#### Nachlese: zwoelf Funde aus der Gegenlese - 22.11.2026

- Fuenf Linsen plus ein Gegenleser je Fund. 17 Funde, 12 bestaetigt, 5 widerlegt. Die Rueckgabe des Laufs war ABGESCHNITTEN und zeigte 2 - die anderen 10 standen im Journal. Rohfunde immer selbst lesen.
- FUND, behoben (SCHWER, und AELTER als die Geldschranke): `_befehl_wortfolge` zerlegt mit `split()` ueber jeden Leerraum, also auch ueber einen Zeilenumbruch, und `stufe_fuer_befehl` beurteilt davon nur Wort 0 und 1. `send_console_command` schrieb die Zeichenkette samt Umbruch auf stdin, und der Server liest ZEILENWEISE. `list\nop Spieler` kam damit an der Admin-Sperre vorbei, `list\nfreimarken geld X 1000000` an der Super-Admin-Stufe. Ein Moderator mit `can_console` genuegte.
- Der Riegel sitzt an der Stelle, die die Zeile SCHREIBT, nicht in der Zerlegung: Wer die Zeile schreibt, entscheidet, was eine Zeile ist. Eine Zerlegung, die jede Zeile einzeln beurteilt, waere ein zweiter Riegel - und liesse die zweite Zeile trotzdem durch, wenn sie fuer sich erlaubt ist.
- FUND, behoben: Die Neustart-Warnung (`_send_server_message`) baut `say {message}` aus einem Formularfeld und sendet mit `user_id=None` und `system_fallback=True` - also OHNE Rollenpruefung. Bei 300 s Vorlauf zehnmal je Neustart. Wird GEGLAETTET statt abgewiesen: Eine Warnung soll ankommen.
- FUND, behoben: `_pfad_label` haengt einen Listenindex OHNE Punkt an, und `speichere` rechnete den Knotenpfad per `split(".")` zurueck. 145 der 549 Felder tragen einen Index - Rangrechte, Berufsspezialisierungen, Verdienstgruende, Torkoordinaten. Jede Aenderung daran meldete "Die Datei wurde zwischenzeitlich geaendert" und verwarf die gueltigen Aenderungen desselben Formulars mit. Neuladen half nie.
- Die Tafel, die das gefangen HAETTE, steht jetzt da: jedes Feld der AUSGELIEFERTEN config.yml muss auch schreibbar sein. Meine 27 Schreibproben waren gruen, weil ihre Probenfassung keine Liste enthielt.
- FUND, behoben: Beim Uebertragen wurde der Vorbesitzer nirgends aufgeschrieben - obwohl der Javadoc und plugin.yml es behaupteten. Gebaut war es nur fuer `freigeben`. Ein Betreiber mit einem Zahlendreher merkte am Satz nicht, dass er jemandem etwas weggenommen hat.
- FUND, behoben: Nach dem Schreiben wurde zweimal gelesen (`lager.finde` im Buch, `parzellenVon` fuer die Zahl). Brach die Verbindung in diesem Augenblick weg, las der Betreiber "es ist NICHTS geaendert" - fuer einen vollzogenen, dauerhaften Wechsel. Das erste Lesen entfaellt ganz (`mitNeuemBesitzer` kennt die neue Zeile), das zweite darf den Erfolg nicht umwerfen (`ZAHL_UNBEKANNT`).
- FUND, behoben: `MariaLager.eigentuemerSetzen` war die EINZIGE Setzmethode der Klasse ohne Daseinspruefung - sie committete bei 0 getroffenen Zeilen und kehrte ohne Klage zurueck, waehrend `SpeicherLager` dort wirft. Zwei Umsetzungen desselben Vertrags antworteten verschieden.
- FUND, behoben: `trifftDieMitte` hielt seine Zusage nicht und KONNTE sie nicht halten. `ursprungX(n)` und `mitteX(n)` liegen immer in derselben Rasterzelle - die Probe war gruen mit UND ohne die Zusage. Sie ist GESTRICHEN, nicht umgeschrieben; eine Grenze, die nie getroffen wird, ist keine.
- FUND, behoben: `eigentuemerSetzenAendertSonstNichts` verglich nur Felder, die ohnehin auf ihrem Vorgabewert standen. Jetzt steht eine FREISTADT mit Name, Kasse, Regeln, Buergerrechten, Zustand und Besuchstag vorher auf anderen Werten - und eine Freistadt ohne Namen wirft.
- FUND, behoben: Keine Probe fuellte den Zwischenspeicher VOR dem Besitzwechsel, darum war das `leeren()` ungedeckt. Jetzt in `schreibenLeertImmer`, wo das `finde` darueber die tragende Zeile ist. Ohne das Leeren laesst `darfBauen` bis zu 30 s den Vorbesitzer weiterbauen und weist den neuen auf seiner eigenen Parzelle ab.
- FUND, behoben: Mein Einschub in plugin.yml hat den Geld-Kommentar von seinem Schluessel getrennt - wer nachsah, was `freimarken.besitz` erlaubt, las die Saetze ueber Geldbuchungen.
- OFFEN (gemeldet, nicht behoben): Kistenshops wechseln NICHT mit. Die Tabelle `kistenshop` haengt an Welt und Schildkoordinate, nicht an der Parzelle. Ein Shop des Vorbesitzers liegt danach auf fremdem Grund und ist fuer BEIDE verschlossen - `Schutzwache` weist den Vorbesitzer ab, `Shopwache` den neuen. Nur `freimarken.ueberall` kommt heran. Hilfetext und Antwort sagen es jetzt; eine Loesung braucht einen eigenen Schritt.
- WIDERLEGT (drei Behauptungen): Der Dateieditor zeigt und schreibt `datenbank.passwort` weiter - das ist die aufgeschriebene Entscheidung, nicht eine Luecke, und er ist Admin-Sache. Und die Datapack-Funktion ist eine im Docstring benannte blinde Stelle.
- LEHRE: Der Einspritzungstreiber meldete "5 von 5 versuchten" und hatte genau die zwei Faelle des Sicherheitsriegels UEBERSPRUNGEN - der Anker trug einen echten Umbruch statt der zwei Zeichen. Die erwartete Fallzahl gehoert ins Programm.

#### Manager: die Geldschranke

- FUND, behoben: `can_control_server` genuegt schon mit `can_restart` ODER `can_console` ODER `can_manage`, und `braucht_admin` kannte nur `op/deop/pardon/pardon-ip` und `whitelist off`. `/freimarken geld` fiel durch - ein Neustart-Recht war ein Gelddruckrecht.
- GEBAUT: zweite Stufe `STUFE_SUPER_ADMIN` in `process_service`, an derselben Engstelle, durch die alle fuenf Sendewege gehen. `stufe_fuer_befehl` beantwortet WELCHE Stufe, `braucht_admin` bleibt die Frage "braucht das eine erhoehte Stufe" und behaelt seine vier Aufrufer.
- Das Kuerzel `fm` steht mit in der Liste. Ohne es waere `/fm geld` der offene Weg - derselbe Grund, aus dem `pardon-ip` neben `pardon` steht.
- OFFENE LUECKE, bewusst: Der Hauptname des Befehls kommt aus der Sprachdatei des Servers (`BundSatz.BEFEHL_FREIMARKEN`). Wer ihn dort umbenennt, steht nicht mehr in der Liste. Umbenennen kann nur, wer Serverdateien schreiben darf - dieselbe Vollmacht, gegen die hier nicht geschuetzt wird.
- OFFEN: Die Konsole gibt NIE eine Antwort zurueck. `send_console_command` liefert hart `(True, "Befehl gesendet.")`, auch bei `UNBEKANNTER_SPIELER`, `NICHTS_ZU_BUCHEN` oder `NICHT_GEDECKT`. Eine Konten-Seite, die Erfolg meldet, meldet ihn also auch fuer eine nicht erfolgte Korrektur. Der Ausgang steht nur als Textzeile im Serverlog, hinter dem Wort `Ausgang `.
- OFFEN, nur noch halb: Als Absender steht `CONSOLE` im Plugin-Protokoll, der Mensch nur im Manager-Audit. Das WARUM steht aber nicht mehr nirgends - `_sende` schreibt Befehl, Grund und "gesendet: ja/nein" ins Audit. Plan 12a.2 Regel 3 faellt auf zwei Buecher auseinander, aber keines fehlt.
- ~~OFFEN: Goldmark laesst sich nicht korrigieren.~~ 23.11. GEBAUT: `/freimarken goldgeld <spieler> <betrag>`, zwei neue Gruende (`betreiber_goldgutschrift`/`-goldabzug`) im Goldbuch, Schranke eine Million statt einer Milliarde. Der Riegel ist der PARAMETERTYP (`bucheGold(Goldkasse ...)`), nicht ein zweiter Wurf. Im Manager: Waehrungsfeld auf der Kontenseite und `goldgeld` in SUPER_ADMIN_ZWEIGE unter BEIDEN Schluesseln - ohne das Kuerzel `fm` waere es ein Gelddruckrecht zum 800fachen Wert. Nebenbefund zur alten Zeile: `pruefeWaehrung` warf nie, weil kein Aufrufer die Goldkasse hereingab - der Wurf war die Mauer, nicht der Fehler.
- Offen: die Geldwort-Tafel (VorlageTest 10e) sieht nur `\bMark\b` und ist fuer "Goldmark" blind. Eine Schwestertafel braucht dieselbe Aufzaehlung der getroffenen Texte wie die erste.
- Offen: `Muenzhaus` erstattet im catch, aber ein Wurf AUS der Erstattung ist nicht gefangen - dann ist die Goldmark weg. Der neue Befehl macht das Ersetzen moeglich; dass der Betreiber davon ERFAEHRT, tut er nicht. Es fehlt eine SEVERE-Zeile.

- ~~OFFEN, zu klaeren: Die Plugin-Tabellen fuehren Spieler nur als UUID (CHAR(36)); Namen stehen allein in der `usercache.json`.~~ 22.11. GELOEST in Teil C: `freimarken_spieldaten_service.namen` loest an EINER Stelle aus `usercache.json` und den drei Zugangslisten auf; `beschriftung` faellt auf die ersten acht Zeichen der Kennung zurueck.
- ~~OFFEN, zu klaeren: Plan 12a.2 Regel 2 verlangt Geld- und Besitzkorrekturen als KONSOLENBEFEHL. Ob das Plugin solche Befehle hat, ist noch nicht gemessen.~~ 22.11. GEMESSEN: beide gibt es - `BundBefehle.java` `literal("geld")` und `literal("besitz")`. Die drei Seiten korrigieren darum schreibend (Teil D).

## Schritt 13: Sprache, Farbwelt, Menues

### Teil C: die Buergeramt-Anmeldung - 21.11.2026

- GEBAUT: Wer die geltenden Regeln nicht angenommen hat, bekommt beim Beitritt ein FENSTER mit zwei Knoepfen - der erste tippt /regeln annehmen. Erst nach der Annahme handelt er am Bundesmarkt; alles andere kann er von Anfang an.
- DIE FASSUNG ist die tragende Entscheidung, und sie stand nicht im Plan: Eine Annahme ohne Fassung waere bei der ersten Regelaenderung stillschweigend wertlos - der Nachweis in der Datenbank zeigte auf einen Text, den es nicht mehr gibt. Jetzt traegt jede Annahme die Fassung, die damals galt (anmeldung.fassung in der config.yml). Wer sie erhoeht, verlangt von allen eine neue Zustimmung; das Programm zaehlt sie NICHT selbst hoch, sonst waere jedes verbesserte Komma eine neue Pflicht fuer alle.
- EINE Stelle beantwortet "muss er annehmen" (Anmeldung.verlangt) - gefragt von der Wache, von /regeln und vom Bundesmarkt. Drei Fassungen derselben Frage wichen eines Tages ab, und dann boete ein Weg etwas an, was ein anderer verweigert; genau das hat das Hauptmenue heute gekostet.
- DIE SPERRE sitzt an EINER Stelle (Shophandel.handle) und nur am BUNDstand: Ein Spielershop ist der Handel zwischen zwei Buergern - den zu sperren waere eine Entscheidung ueber das Geschaeft eines Dritten.
- ERSTER DIALOG DES PROJEKTS MIT EINER TAT: DialogType.confirmation mit zwei Knoepfen, der Ja-Knopf traegt DialogAction.commandTemplate. Beides im API-Jar nachgesehen und bis heute nirgends benutzt. Der Knopf TIPPT einen Befehl - er gewaehrt nichts selbst, sonst waere er eine Tuer ohne Rechtefrage.
- /regeln annehmen ist auch der Weg fuer einen alten Client: showDialog sendet und fragt nicht, ein verworfenes Paket merkt der Server nie. Die Chatfassung nennt den Befehl darum ausdruecklich.
- Tabelle anmeldungen (Schemaschritt 34): eine Zeile je Spieler, Fassung und Zeitpunkt, kein Fremdschluessel aufs Konto - angenommen hat ein Mensch, nicht ein Guthaben. Der Zeitpunkt der ERSTEN Zustimmung bleibt stehen; ein blindes Fortschreiben machte den Nachweis bei jedem Beitritt juenger, als er ist.
- Proben: AnmeldeVertrag (10, fuer beide Fassungen), MariaAnmeldungenTest (6 mit rohem SQL), AnmeldungTest (7), AnmeldewacheTest (6), RegelnBefehlTest 8-15, drei Sperrproben in ShophandelTest. Tafeln 31di (Wache verdrahtet, nur bei an()) und 31dj (der Knopfbefehl ist ein Zweig, den es gibt).

### Teil D: der Rundgang durch Altfurt - 21.11.2026

- GEBAUT, mit LEERER Stationsliste - ENTSCHIEDEN: Die Gebaeude in Altfurt sind Handarbeit, und ein Bereich auf einem leeren Platz waere eine Station, die niemand findet. Sobald sie stehen, traegt der Betreiber je Station zwei Ecken in die config.yml ein; die Mechanik wartet schon.
- WER EINE STATION BETRITT, liest beim ERSTEN Mal ihren Namen mit dem Fortschritt ("Station 2 von 6") und ihren eigenen Text. Beim zweiten Mal nichts - ein Stationstext bei jedem Schritt durch dieselbe Tuer waere eine Plage.
- DIE REIHENFOLGE IST DER WEG, aber kein ZWANG: Jede Station zaehlt, sobald sie betreten wird. Ein erzwungener Weg muesste den Spieler abweisen, und Plan 10.2 verlangt "ein Ort, kein Textfenster" - ein Rundgang, der einen zurueckschickt, ist ein Textfenster mit anderen Mitteln.
- EINE ZEILE JE SPIELER UND STATION (Schemaschritt 35), keine Zahl: Eine Zahl saehe nicht, WELCHE Station gesehen wurde - und der Betreiber darf seinen Weg aendern. Mit einer Zahl waere der Rundgang nach dem Hinzufuegen einer Station weiter abgeschlossen.
- Eine ENTFERNTE Station zaehlt nicht mehr mit (die Zeile bleibt stehen, sie ist kein Fehler), eine NEUE macht den Rundgang fuer alle wieder offen. Beides ist richtig: Ein Weg mit einer neuen Station ist ein neuer Weg.
- RUNDGANG IST DIE SECHSTE BEDINGUNGSART - ENTSCHIEDEN, denn Plan 9 verlangt sie fuer den Siedler. Gesetzt ist sie in der ausgelieferten Leiter NICHT: Ohne Stationen ist sie nicht erfuellbar, und eine Stufe, die niemand erreicht, waere schlimmer als keine. Wer Altfurt gebaut hat, traegt beides ein.
- DIE DRITTE BEWEGUNGSWACHE des Projekts (nach Torwache und Aufwaermzeit) - mit hasExplicitlyChangedBlock als erster Zeile und nur angemeldet, wenn es Stationen gibt. Ab Werk hoert niemand zu, und kein Schritt kostet etwas.
- Die Stationssaetze sind die VIERTE offene Satzfamilie (nach Beruf, Herausforderung, Katalogposten): Welche es gibt, bestimmt der Betreiber. Fehlt eine Zeile, bleibt die Station begehbar und zeigt ihren Pfad - und der START nennt sie. Jene Zusage stand in drei Kommentaren und ist jetzt gebaut (Tafel 31dl).
- Ein Art.WORT darf KEINEN Farbtag tragen, und das hat der Lader meiner eigenen Probe gesagt: Ein eingesetzter Wert ist ein Blatt, ein Farbtag darin stuende woertlich im Chat. Die Farbe kommt aus der Zeile, die ihn einsetzt.
- rundgang.welt gehoert zu den NICHT neuladbaren Welten - die Tafel 31cz hat es selbst gefunden. Stationen sind Orte: Verschoebe ein Neuladen einen Bereich, haette ein Spieler eine Station gesehen, an der er nie war.
- Proben: RundgangVertrag (9, fuer beide Fassungen), MariaRundgangTest (6), RundgangTest (12), RundgangslisteTest (10), RundgangswacheTest (5), zwei neue in AufstiegslaufTest. Tafeln 31dk (Wache verdrahtet, nur bei Stationen, und VOR dem Aufstiegslauf) und 31dl (der Start nennt fehlende Stationszeilen).
- EINSPRITZUNGSRUNDE (30 Faelle ueber Teil C und D, alle drei Module je Lauf): 29 auf Anhieb gefangen.
- DER EINE REST war der lehrreiche: Vertauscht man die zwei Knoepfe in der Formularhand (confirmation(nein, ja)), stimmt "Spaeter" zu und "Ich nehme an" schliesst nur - und alle 2620 Proben bleiben gruen. Die Reihenfolge ist an der NAHT geprueft, aber die Hand ist die einzige Klasse ohne Zeugen. Tafel 31dm haelt jetzt beides: confirmation(ja, nein) woertlich UND dass die Tat am JA-Knopf steht.
- FUND AN DER EIGENEN TAFEL: Fall 26 wurde DOPPELT gefangen - von meiner 31dl und von der vorhandenen 31ab ("jede Startpruefung wird auch gerufen"). Zwei Riegel fuer denselben Fall machen sich unpruefbar; 31dl haelt jetzt nur noch die EXISTENZ der Methode, und ein Paar von Einspritzungen beweist die Trennung.
- FUND DES STARTLAUFS, und nur er konnte ihn finden: Die Anmeldung wurde zugewiesen, NACHDEM richteShopsEin() den Shophandel gebaut hatte. Jener verlangt sie im Konstruktor, bekam null - und Paper schaltete das GANZE PLUGIN ab. Alle 4786 Proben waren gruen, und zwar zu Recht: Jede baut den Handel selbst und gibt ihm, was er braucht; die Reihenfolge im onEnable prueft kein Probenlauf. Tafel 31dn haelt sie jetzt (wie 31dk den Rundgang vor dem Aufstiegslauf).
- Proben 2567 -> 2622 (paper), 1747 -> 1768 (core), 382 -> 397 (daten) - zusammen 4787.
- ~~OFFEN, und zwar Handarbeit: die zehn Platzhalterbauten in Altfurt.~~ 23.11. GEBAUT, und nicht von Hand: Die neun fehlenden stehen als Quader in `bauten.haeuser`, und `Altfurtaufbau` setzt beim Start, was FEHLT (gefragt wird EIN Block je Bau - die Wandecke über dem Boden). Damit überlebt die Stadt einen Weltverlust. Nächster Schritt: die Stationen des Rundgangs aus denselben Zahlen eintragen (Vorschlag steht unter Schritt 14), dann die Siedlerstufe.
- OFFEN beim Spieltest: das Anmeldefenster mit zwei Knoepfen, ein Klick auf "Spaeter" und das Wiederkommen beim naechsten Beitritt, die Absage am Bundstand, /regeln annehmen auf einem alten Client - und der Rundgang selbst, sobald Altfurt steht.

### Teil A: die Farbwelt - 21.11.2026

- ENTSCHIEDEN: flaechig im ganzen Chat, frei anpassbar, thematisch gruppiert - ACHT Rollen statt eines Feldes je Tag.
- Die Rollen heissen nach ihrer AUFGABE (fliesstext, betont, kopf, geld, warnung, bestaetigung, hinweis, gedaempft) und nicht nach ihrer Farbe: Wer umfaerbt, muss keinen Satz anfassen.
- Die Palette steht im Block farben: der SPRACHDATEI, nicht in der config.yml - eine Datei, ein Leser, ein Stand, ein gepruefter alles-oder-nichts-Neuladeweg.
- betont heisst nicht name: <name> ist in dieser Datei der Angabeplatz der Befehlshinweise ("/laden mieten <name>"). Tafel 31t haette es abgewiesen.
- 791 Tags umgestellt, die 1272 vorhandenen Bildzeilen blieben unveraendert gruen - der hexColors-Schritt im Bildner war vorher getrennt belegt.
- Die Markenwerte sind fuer den Chat AUFGEHELLT (Schiefer, Markgold, Bundesrot, Forstgruen); die reinen Werte bleiben fuer Flaechen.
- Eine unbekannte Rolle ist ein BERICHT (sie wirkt nicht, und das steht da), ein kaputter Farbwert eine KLAGE mit dem Hinweis auf die YAML-Falle (eine Raute ohne Anfuehrungszeichen beginnt einen Kommentar).
- Offen, nicht gebaut: ein Betreiberbefehl, der die Palette zeigt. Heute sieht man sie nur in der Datei.

### Teil B: Menue, Regeln, Hilfe - 21.11.2026

- /MENU GEBAUT (auch /menue): Truhenmenue mit elf Eintraegen. Ein Klick fuehrt einen BEFEHL aus, das Menue gewaehrt nie selbst etwas - Recht, Geld und Ort prueft derselbe Zweig, den der Spieler tippt.
- Erkannt wird das eigene Menue am HALTER und nie am Titel (der kommt aus der Sprachdatei und laesst sich nachmachen). MenuType kann das nicht: ihr InventoryViewBuilder nimmt keinen Halter (im API-Jar nachgesehen).
- Jeder Klick wird abgesagt, auch der auf ein leeres Fach, und das ZIEHEN ist ein eigenes Ereignis - sonst holt sich jemand den Hilfetext als Diamanten.
- Ein offenes Pluginmenue zaehlt NICHT als taetige Zeit (die Messwache fragt den Halter) - sonst waere bezahlte Anwesenheit mit einem Klick zu haben.
- /REGELN GEBAUT (auch /rules): ein Spieler bekommt ein FENSTER (Paper-Dialog), die Konsole und /regeln chat den Chat. Geht das Fenster nicht auf - ein alter Client ueber ViaProxy -, kommen dieselben Zeilen in den Chat; der Grund steht einmal im Serverprotokoll.
- Der Regeltext ist EIN Satz mit Umbruchmarken (regeln.text) - der erste mehrzeilige Satz des Projekts. Eine Liste haette eine Zahl von Schluesseln, die der Betreiber bestimmt, und jeder Schluessel muss zu einem Satz gehoeren.
- Die Dialoghaelfte ist die EINZIGE Klasse ohne Zeugen: Die ganze API geht durch einen ServiceLoader, der auf dem Probenklassenpfad nichts findet (Bukkit.setServer hilft nicht). Darum nimmt sie FERTIGE Components und keine Satzschluessel - sonst laege das Holen genau dort, wo keine Probe hinkommt.
- /HILFE GEBAUT: zwanzig Befehle in sechs Gruppen, gefiltert mit dem Recht, das der Befehl SELBST fragt. Eine Gruppe, von der nichts uebrig bleibt, verschwindet mit ihrer Ueberschrift.
- Die Beschreibung steht genau EINMAL (befehl.beschreibung.*) - dieselbe Zeile zeigt Papers eigene Befehlsliste. HilfeplanTest haelt vier Richtungen: Beschreibung gegen Plan, die PAARUNG je Eintrag, Menuetafel gegen Plan, und den Plan gegen die echte Anmeldung im Quelltext.
- Kein Zweitname "help" fuer /hilfe: Den hat der Server selbst, ein zweiter Anbieter landet im Namensraum des Plugins - eine Verwechslung, die man nicht sieht, weil beides etwas anzeigt.
- Jede Zeile von /hilfe kommt ueber bau und nie ueber eineZeile: Eine Umbruchmarke in einer Beschreibung wird so eine zweite Zeile und kein Wurf mitten in der Liste.
- FUND beim Nachbauen der fehlenden Menueproben: Die Wache hatte ZWEI Riegel fuer denselben Fall (geklicktes Inventar UND rohes Fach). Der zweite ist heraus - mit ihm davor war getRawSlot gegen getSlot nicht unterscheidbar, ein Tausch blieb gruen. Jetzt haelt MenuewacheTest 3 den Fall wirklich (Fach 37 im Rucksack ist umgerechnet Fach 10 im Menue).
- Das Menue hatte bis heute KEINE eigene Probe, nur Tafeln. Neu: MenuetafelTest (9), MenueBefehlTest (4), MenuewacheTest (7). Dazu HilfeplanTest (9), HilfeBefehlTest (8), RegelnBefehlTest (7) und 13 Bildzeilen. Proben 2493 -> 2550.
- Spielerprobe schreibt jetzt EINE Liste mit, in der Schliessen und Tippen zusammen stehen: Die Zusage "erst schliessen, dann tippen" ist eine REIHENFOLGE, und zwei getrennte Mitschriften koennten sie nicht halten.
- Einspritzungsrunde (19 Faelle): 19/19 gefangen, jeder von der Probe, deren Name den Fall nennt. ZWEI Fehler steckten im Treiber, keiner im Code - der Rueckweg war eine Textersetzung (ein Fall, der etwas durch den Leerstring ersetzt, ist so nicht zurueckzunehmen), und die Einordnung suchte "error:" ohne Gross-Klein-Ruecksicht und traf damit "AssertionFailedError:". Jetzt: Sicherungskopie je Runde, Einordnung am javac-Muster.
- Startlauf gegen einen Wegwerfserver: Sprachdatei laedt OHNE Klage (der erste mehrzeilige Wert geht durch die Zeichenwache), /hilfe antwortet auf der Konsole mit genau 28 Zeilen, /regeln mit 7, /menu mit seiner eigenen Absage. Die Anmeldezeile nennt hilfe, regeln, rules, menu und menue. Fehlerzeilen und Warnungen sind zeichengleich die des vorigen Laufes (fehlende Truhen in der frischen Markthalle, Papers Datenpaketzeile, keine Datenbank).
- Offen, gehoert zum Buergeramt: die ANNAHME der Regeln als Formular mit zwei Knoepfen. Das Geruest dafuer steht - eine confirmation statt einer notice, und der zweite Knopf fuehrt einen BEFEHL aus, so wie das Menue es tut.
- Offen beim Spieltest: /menu mit einem Klick je Eintrag, /regeln als Fenster, /regeln auf einem alten Client ueber ViaProxy (dort muss der Chatweg kommen), und die Farben im Spiel statt auf der Konsole.

### Nachlese zu Teil A und B: 18 Funde einer Lesesuche - 21.11.2026

- Sechs Linsen ueber Teil A und B, jeder Fund von zwei Skeptikern geprueft: 18 bestaetigt, 3 strittig. Alle 21 selbst an der Quelle nachgelesen - alle echt, keiner verworfen.
- SCHWERSTER FUND: Ein Vorzeichen im Farbwert WARF, statt zu klagen. Integer.parseInt nimmt "#-00001" als -1; die Zahl lief an der Klage vorbei in den Riegel der Farbtafel - und der wirft. Beim Start steht das Lesen ausserhalb des try, also schaltete EIN Tippfehler in der sprache.yml das ganze Plugin ab. Jetzt prueft farbwert die sechs Stellen selbst; der Riegel in Farbtafel ist wieder die zweite Zusicherung.
- ZWEITSCHWERSTER: Das Hauptmenue bot elf Befehle UNGEFILTERT an. /hilfe filtert mit dem Recht des Befehls, das Menue nicht - wer freimarken.werder wegnimmt, sah den Prismarinblock weiter und bekam auf den Klick Brigadiers englische Zeile (requires VERBIRGT einen Knoten, es sagt nicht ab). Der Filter sitzt jetzt in der TAFEL und nicht in der Hand: Die Hand legt die gefilterte Tafel in den Halter, also geht auch der KLICK durch denselben Riegel - ein weggefallenes Fach ist unbekannt und nicht bloss leer.
- Das Recht hat EINEN Ort: Die Tafel schreibt es nicht ab, sie fragt beim Hilfeplan nach. Ein Eintrag mit einem Befehl, den der Plan nicht kennt, wird beim Bauen abgewiesen - ohne Plan gibt es kein Recht, nach dem zu fragen waere.
- ZUSAGE OHNE BAU, dreimal dieselbe: Der Rueckfall fuer alte Clients. showDialog SENDET und fragt nicht; ein Paket, das ViaProxy nach unten verwirft, merkt der Server nie - der Spieler las GAR NICHTS, und auch keine Protokollzeile. Eine Erkennung der Dialogfaehigkeit ist nicht gemessen, also wird sie nicht zugesagt: Die drei Texte sagen jetzt, was der Fang deckt (einen Fehler auf dem SERVER), und der Ausweg /regeln chat steht in der Beschreibung des Befehls - der einen Zeile, die jeder Client in seiner Befehlsliste liest.
- /shop, /tpa und /tpahere hatten an der Wurzel kein executes, waehrend hilfe.fuss jedem zusagt: "Jeder Befehl ohne Zusatz zeigt, was er kann." /shop zeigt jetzt die Liste, /tpa und /tpahere je einen Gebrauchssatz - zwei Saetze und nicht einer, weil die RICHTUNG verschieden ist.
- 34 Kommentarzeilen der Sprachdatei nannten noch feste Farben ("die Ueberschrift gold, die Liste aqua, der Hinweis grau"), und zwar mit Woertern, die jetzt ROLLENNAMEN sind: "der Hinweis grau" stand fuer fliesstext. Wer das liest, aendert folgerichtig die falsche Rolle. Jede Rolle ist am Satz darunter abgelesen; "aqua" gab es in der ganzen Datei nur in diesem einen Kommentar.
- Vier veraltete Zusagen im Quelltext: regeln.kopf durfte laut Javadoc mehrzeilig werden (der Riegel kostet die GANZE Datei, und zwar beim Laden), @param name nannte Art.WORT statt SATZ, der Kopf von VERDECKTE_HOLSTELLEN teilte fuenf Stellen in zwei Gruppen (es sind zehn in drei), und in plugin.yml hing der Erklaerungsblock von freimarken.rang ueber freimarken.menu - die neuen Rechte waren zwischen Block und Schluessel geraten.
- Das Neuladen nannte nur /help; seit es /hilfe gibt, liest ein ZWEITER Befehl dieselben eingefrorenen Beschreibungen aus demselben Buch.
- artname war gebaut und wurde nirgends gerufen. Jetzt normalisiert der Eintrag-Konstruktor damit - und die Methode steht auf der REINEN Seite der Naht, sonst laedt jede Tafelprobe die Bukkit-Haelfte.
- /regeln zeigte das Fenster dem Executor und schrieb den Rueckfall an den Sender: Bei /execute as las je nach Lage ein anderer. Jetzt EIN Leser - der, der getippt hat.
- FEHLENDE PROBEN (fuenf Funde waren keine Fehler, sondern ungehaltene Zusagen): Der Neuladeweg der PALETTE hatte keine (der Helfer kopierte immer dieselben Farben, der Farbzweig lief nie); die Anmeldung der Menuewache keine Tafel; das Recht im Hilfeplan war an nichts gebunden (die Probe holte ihre Erwartung AUS dem Plan); die Paarung Satz-zu-Befehl im Hauptmenue pruefte nur Spalten, nie Zeilen; und MenuetafelTest 8 war beim Fall "keine Zeile" mit UND ohne Riegel gruen, weil das Gegenbeispiel vorher im Fach-Riegel hing.
- Die Befehlsprobe kann jetzt ZWEI verschiedene Leser besetzen (tippeAls). Ihr eigenes Javadoc sagte seit Monaten, dass diese Probe fehlt - ohne sie war jedes Vertauschen von Sender und Executor gruen, in jedem Befehl.
- Neue Tafeln: 31de (Menuewache angemeldet) und 31df (kein Farbwort in einem Kommentar der Vorlage, erst hinter dem Block farben:, mit dem TREFFERTEXT). Fuer "weiss" trifft das Muster nur eine Farbwendung - "weiss der Spieler nicht" ist das Verb, und eine ausgenommene Zeile ist fuer immer blind.
- EINSPRITZUNGSRUNDE (20 Faelle) gegen die neuen Riegel und Proben: 18 auf Anhieb gefangen. Die zwei anderen waren die lehrreichen.
- FUND AN DER EIGENEN REPARATUR: Der Rechtefilter war dreifach gruen geprueft - dass die HAND ihn benutzt, hielt nichts. Ersetzt man dort fuerRechte durch die Tafel selbst, bleiben alle 2565 Proben gruen und das Menue bietet wieder jedem alles an. Der Variablenname bleibt ja gleich, also sieht auch keine Namenstafel es, und die Hand liegt hinter createInventory. Tafel 31dg haelt jetzt BEIDE Zeilen - den Filteraufruf und den Halter, denn die zweite entscheidet, ob auch der KLICK durch den Riegel geht.
- FUND AN DER EIGENEN PROBE: Eine neue Probe meldete eine zweite "Anna" in der STATISCHEN Anwesenheitstabelle an - und machte damit WolkeBefehlTest rot, eine Datei, die ich nicht angefasst hatte. /regeln loest keinen Namen auf, die Anmeldung war ueberfluessig. Tafel 31dh: Wer anmelden ruft, ruft niemandMehrDa. Dafuer liest Quellen jetzt auch den PROBENBAUM - fuer keine Tafel war er bisher lesbar.
- GEMESSEN, was keine Probe messen kann: Ein Wegwerfserver bekam eine sprache.yml mit farben: warnung: "#-00001". Jetzt kommt die KLAGE mit Zeilennummer, das Plugin laeuft weiter und /hilfe antwortet; der gute Wert in derselben Datei wirkt nicht (alles oder nichts). Vorher hatte Paper hier das ganze Plugin abgeschaltet. Zweiter Lauf mit nur dem guten Wert: "deine Farben: kopf: #c6a15b -> #00ff00" - Rolle und beide Werte in der Richtung alt -> neu.
- Startlauf: Sprachdatei laedt ohne Klage, /hilfe 28 Zeilen (die Zeile zu /regeln nennt jetzt den Chatweg), /regeln und /regeln chat je 7, /menu seine Absage. Fehlerzeilen zeichengleich die des vorigen Laufes.
- NICHT belegt durch den Startlauf: Die neuen Wurzeln von /shop, /tpa und /tpahere sind spielerpflichtig, also fuer die Konsole VERBORGEN - dort kommt Brigadiers englische Zeile, und das ist richtig. Nur die Proben halten sie; gesehen hat sie noch niemand.
- Proben 2550 -> 2567. Einspritzungen insgesamt 24, alle gefangen.

## Schritt 12: Raenge, Goldmark, Wechsel, Kosmetik

- ENTSCHIEDEN 18.11.2026 (vier Fragen): Rang kommt in eine EIGENE Tabelle plus
  PermissionAttachment (nicht LuckPerms - app/services/player_data_service.py fasst
  .db/.sqlite beim UUID-Umbau bewusst nicht an, der Rang waere still weg); Goldmark als
  ZWEITE Kasse-Instanz mit eigener Buchungstabelle (nicht als Waehrungsspalte - zwei
  Waehrungen in einer Tabelle addieren sich zu einem Stand, den die tragende Zusage aus
  Kasse.java:24 dann falsch bestaetigt).
- ENTSCHIEDEN 18.11.2026 (Nutzer, zwei eigene Antworten): (1) "weiter nach dem grundsatz
  immer frei einstellbar, zb. auch welcher rank wie viele gibt" - also auch der
  Widerspruch im Plan um die Heimatpunkte (5 Goldmark vs. "allein den bezahlten Raengen")
  wird nicht entschieden, sondern ein Schalter. (2) "man soll nicht fuer die reine
  gespielte zeit belohnt werden, die gespielte zeit gilt als richtwert fuer kosten" - der
  Aufstieg haengt an TAETIGER Zeit und an Taten, die Stundenzahlen aus Plan 9 sind damit
  ein Richtwert fuer Preise und nicht der Ausloeser.
- BAUPLAN in acht Teilen: A Rangleiter im Kern, B Leser und Bericht, C Ablage und
  Vergabe, D Aufstieg zur Laufzeit, E Goldmark, F Wechsel mit Deckel, G Muenzhaus,
  H Grenzen an den Rang.
- ~~Teil A: die Rangleiter im Kern.~~ 19.11. GEBAUT. Bedingungsart (taetige-stunden,
  anwesenheit-stunden, parzellen, beruf, guthaben - eine AUFZAEHLUNG, weil jede Art eine
  Stelle braucht, die sie misst), Rangwerte (Leiter aus der Datei, je Stufe bedingungen/
  zahlen/rechte), Rangregel (zustehend, aufstieg, fehlend, gebraucht).
- EIN RANG IST EIN BUENDEL RECHTE: Was eine Stufe gibt, wird zu freimarken.<grenze>.<zahl>
  und freimarken.<recht>, und Rangzahl bleibt die EINE Auswertestelle. Damit steht keine
  Zahl aus Plan 9 im Quelltext.
- Drei Regeln, die keine Probe erraten kann und die darum im Quelltext begruendet stehen:
  kein UEBERSPRINGEN (es gilt der hoechste Rang, fuer den diese Stufe UND jede darunter
  erfuellt ist), kein ABSTIEG (sonst verliert ein Buerger seinen Rang, weil er seine
  Parzelle verkauft hat), und die unterste Stufe darf KEINE Bedingung haben (sonst stuende
  ein neuer Spieler auf keiner Stufe).
- Die Umrechnung Stunden -> Sekunden steht an EINER Stelle (Bedingungsart.inMesseinheit).
  Am Rand haette sie jeder Messer selbst gemacht - einer schnitte die angefangene Stunde
  ab, der naechste nicht, und zwei Spieler mit derselben Zeit haetten verschiedene Raenge.
- ~~Teil B: Leser und Bericht.~~ 19.11. GEBAUT. Rangliste (eigener Leser wie
  Aufgabenliste), Abschnitt raenge: in der config.yml, neunzehnter Bestandteil der
  Wirtschaftswerte, Startzeile nenneRaenge().
- EINE KAPUTTE STUFE FAELLT GANZ WEG, anders als bei jedem anderen Leser: Eine Stufe, der
  eine Bedingung fehlt, ist eine LEICHTERE - dann bekaeme jemand Rechte, die er nicht
  verdient hat. Eine weggefallene Stufe nimmt dagegen niemandem etwas, was er schon hat.
- DIE SCHRANKE, DIE KEINE PROBE FANGEN KANN: parzellen.hoechstzahl ist eine UNTERGRENZE
  fuer JEDEN (Rangzahl nimmt die hoechste Zahl aus Recht UND Datei). Solange dort 4 steht,
  hebt die ganze Leiter nichts. Zwei Zeilen in zwei Abschnitten, jede fuer sich richtig -
  darum sagt es der Startlauf in einer eigenen Warnung je Grenze
  (Rangliste.wirkungslose). Das Senken kommt mit Teil H samt Einmallauf.
- Tafel 31ab deckt jetzt pruefe* UND nenne*: nenneRaenge waere durch das alte Muster
  gefallen, dabei ist es dieselbe Sache - eine Methode, die nur meldet, meldet gar nichts,
  wenn niemand sie ruft.
- Proben 4054 -> 4101 (core 1583, daten 313, paper 2205), 0 rot, 0 uebersprungen. Neu:
  RangwerteTest (19), RangregelTest (15), RanglisteTest (13). Nachgezogen:
  WirtschaftsstandTest (die eine Stelle, die den vollen Satz von Hand baut).
- ~~Teil C: Ablage und Vergabe.~~ 19.11. GEBAUT. Rangstand und Rangablage im Kern,
  SpeicherRaenge, RangVertrag, Schemaschritt 31 (Tabelle raenge), MariaRaenge, Rangwache
  mit Rechtegriff/AnhangRechte, verdrahtet in richteSpielzeitEin.
- EIN RANG GIBT KUMULATIV, was die Stufen darunter geben - die Entscheidung, die Teil C
  erzwungen hat: buerger nennt laden.mieten nicht noch einmal, er nennt nur das NEUE.
  Zaehlte allein die erreichte Stufe, naehme der Aufstieg zum Buerger dem Spieler seinen
  Laden weg - der Abstieg durch die Hintertuer, und die Absage im Spiel ist dieselbe wie
  bei einem, der das Recht nie hatte. Bei Zahlen gilt die hoechste.
- DARUM BERICHTIGT: Rangwerte.ungereimtheiten sagte "ein Aufstieg nimmt hier etwas weg".
  Seit buendel() kumuliert, nimmt er nichts weg, er hebt nur nichts - der Satz haette
  einen Schaden behauptet, den es nicht gibt, und den echten (eine Stufe ohne Wirkung)
  verdeckt. Zwei Proben nachgezogen.
- KEINE ZEILE HEISST "UNTERSTE STUFE", nicht "kein Rang": Geschrieben wird erst beim
  ersten Aufstieg. Eine Zeile je Besucher waere eine Tabelle, die nur Platz braucht - und
  wer eine Stufe aus der config.yml nimmt, strandet sonst jeden, der auf ihr stand. Der
  Rueckfall steht an EINER Stelle (Rangstand.stufeIn), und ein Rang, den die Leiter nicht
  mehr kennt, faellt mit zurueck UND wird einmal gemeldet.
- EINE ZEILE JE SPIELER, kein Verlauf: PRIMARY KEY (spieler). Auf "welchen Rang hat er"
  muss es genau EINE Antwort geben; mit einer Zeile je erreichter Stufe waere es "die
  juengste", und die ist nach einer umsortierten Leiter die falsche.
- KEIN LUCKPERMS, sondern PermissionAttachment: app/services/player_data_service.py fasst
  .db/.sqlite beim UUID-Umbau bewusst nicht an - der Rang waere nach einem Umbau still
  weg. Das Anhaengsel wird ERSETZT und nicht ergaenzt, sonst wirkte eine Leiter, die je
  eine Zahl senkt, nie.
- Rangzahl haelt jetzt BEIDE Richtungen: knoten() schreibt freimarken.<name>.<zahl>,
  ausRecht() liest es. Ein Schreiber und ein Leser derselben Gestalt an zwei Stellen
  laufen auseinander, und rot wird dabei nichts - darum die Rundreiseprobe.
- Drei neue Quelltexttafeln, jede gegen eine Luecke, die kein Verhaltenstest sieht:
  31cq (jede Grenze aus BEKANNTE_ZAHLEN hat im Spielmodul einen LESER, und umgekehrt),
  31cr (die Rangwache wird gebaut, angemeldet und NACH der Ablage), 31cs (jede Methode,
  die wie ein Ereignisempfaenger aussieht, traegt @EventHandler - angemeldet wird eine
  Wache auch ohne Marke, und dann ruft sie niemand).
- GEMESSEN, vier Einspritzungen, alle gefangen mit dem richtigen TREFFERTEXT: Ruf von
  registerEvents weg -> 31cr; Grenze ohne Leser -> 31cq; @EventHandler weg -> 31cs nennt
  Rangwache.beimBeitritt; buendel nur die erreichte Stufe -> die Ladenprobe. Startlauf
  ohne Datenbank: keine Ausnahme, keine Klage beim Anmelden.
- MERKEN: --rerun-tasks ist auf der Freigabe ein clean ("Failed to clean up stale
  outputs"). Je Aufgabe einzeln mit --rerun, wie bei clean.
- Proben 4101 -> 4160 (core 1607, daten 328, paper 2225), 0 rot, 0 uebersprungen. Neu:
  RangstandTest (9), SpeicherRaengeTest/RangVertrag (10), MariaRaengeTest (15 inkl.
  Vertrag, gegen die ECHTE Datenbank gelaufen), RangwacheTest (11), RangzahlTest (+6),
  RangwerteTest (+5), GrenzeTest (+3).
- ~~Teil D: Aufstieg zur Laufzeit.~~ 19.11. GEBAUT. Aufstiegslauf (misst, urteilt,
  traegt ein), RangSatz/Rangtext samt Abschnitt raenge in sprache-vorlage.yml, Rangwache
  als Mitfahrer im Spielzeittakt UND beim Beitritt, nenneNachstufung im Startbericht.
- ABNAHMEKRITIERUM VON SCHRITT 12 BEWIESEN, Ende zu Ende gegen das PROBENSCHEMA: Ein
  Spieler mit 6 h taetiger Zeit und einer Parzelle, echter Server, echte MariaDB ->
  "Nachstufung: 1 Spieler geprueft, 1 nachgestuft" und "reisender -> siedler", Zeile in
  raenge mit UTC-Zeitpunkt, 0 Ausnahmen. Der erste Lauf OHNE Parzelle meldete "1
  geprueft, 0 nachgestuft" - die Regel "kein Ueberspringen" also auch gemessen.
- NEUER LESER IM KERN, weil es keinen gab: Spielzeitablage.alleStaende(heute). Es gibt in
  diesem Projekt keinen Weg, alle Spieler aufzuzaehlen - getOfflinePlayers ist an zwei
  Stellen ausdruecklich verboten, und jede Ablagenmethode nimmt eine UUID. Die Tabelle
  spielzeit ist die richtige Quelle: Wer gemessene Zeit hat, hat gespielt. EINE Abfrage
  mit GROUP BY, nicht eine je Spieler.
- DER STARTLAUF IST KEIN EINMALLAUF, sondern ein Lauf bei JEDEM Start. Er ist folgenlos,
  wenn alles stimmt (aufstieg geht nur nach oben), ein Merker waere Aufwand ohne
  Gegenwert - und es gibt keinen fachlichen Merker, der einen Neustart ueberlebt. Dass er
  jedes Mal laeuft, ist der Gewinn: Er faengt den, der waehrend seiner Abwesenheit
  aufstiegsreif wurde, und zieht nach, wenn die Leiter sich aendert.
- GEMESSEN WIRD NUR, WAS DIE LEITER VERLANGT (Rangregel.gebraucht), und fuer NIEMANDEN,
  der schon oben steht. Jede Messung ausser dem Beruf ist eine Datenbankabfrage im
  Hauptfaden, und der Takt schlaegt jede Minute je anwesendem Spieler. Steht die ganze
  Mannschaft oben, kostet der Mitfahrer eine Abfrage je Spieler und Minute.
- BEIM BEITRITT ERST HEBEN, DANN VERGEBEN. Andersherum bekaeme der Spieler die Rechte des
  ALTEN Ranges und die Nachricht ueber den neuen - und in genau dieser Minute versucht er,
  was die Nachricht ihm versprochen hat. Geprueft wird auch beim Beitritt und nicht nur im
  Takt: Die eine Minute ist die, in der ein Rueckkehrer sofort /parzelle nehmen tippt.
- ZWEI DER DREI SAETZE DUERFEN LEER BLEIBEN (naechster, hoechste) - die leere Zeile IST
  der Schalter, an der Stelle, an der auch die Worte stehen. Der Aufstieg selbst darf es
  nicht: Wer Rechte bekommt und nicht liest, warum, haelt sie fuer einen Zufall. Dafuer
  neu: Sprache.leerGelassen und Sprachprobe.mitZeilen.
- Der RANGNAME ist ein Platzhalter und kein Satz: Welche Stufen es gibt, entscheidet die
  config.yml, eine Aufzaehlung in Java koennte sie nicht kennen.
- GEMESSEN, fuenf Einspritzungen, alle mit ihrem TREFFERTEXT gefangen: Ruf imTakt weg ->
  31aj nennt [Rangwache]; taetige Zeit -> Anwesenheit; stehtSchonOben immer false;
  vergibNeu weg -> acht rote Proben; nenneNachstufung weg -> 31ab nennt die tote Methode.
- EIGENER FUND, und er bleibt offen: ORDER BY spieler in MariaSpielzeit.ALLE_LESEN ist
  NICHT pruefbar - entfernt man es, bleibt jede Probe gruen, weil MariaDB fuer die
  Gruppierung ohnehin sortiert. Die Zusage haengt allein an SpeicherSpielzeit, wo
  dieselbe Einspritzung rot wird. Steht als Warnung im Javadoc der Konstante.
- EIGENER FUND zu Teil C: Die Behauptung "keine Uebersetzungswarnung" war falsch -
  :paper:test --rerun fuehrt compileTestJava NICHT erneut aus, und RangwacheTest trug
  eine Verfallswarnung (PlayerQuitEvent mit zwei Angaben). Behoben; der Abschlusslauf
  uebersetzt die Proben jetzt ausdruecklich.
- KEINE ZWEITE TAFEL: Fuer "Mitfahrer wird nie gerufen" gibt es 31aj schon, und sie
  erkennt den neuen von selbst (Feld + imTakt im Quelltext). Die eigene Tafel 31ct wurde
  wieder herausgenommen - zwei Pruefungen fuer denselben Fall machen sich unpruefbar.
- Proben 4160 -> 4198 (core 1613, daten 334, paper 2251), 0 rot, 0 uebersprungen, 0
  Warnungen. Neu: AufstiegslaufTest (18), RangtextTest (5), SpielzeitVertrag (+6, gegen
  die ECHTE Datenbank), BildGleich (+3). Nachgezogen: 31b/31c (Rangwache sendet einmal),
  VorlageTest 9 (neunzehnter Bereich), sechs Spielzeit-Attrappen.
- ~~Teil E: Goldmark.~~ 19.11. GEBAUT. Kassentabellen (MARK/GOLDMARK), MariaKasse auf
  eine zweite Tabellenmenge umstellbar, Schemaschritte 32/33 (goldkassen,
  goldbuchungen), Goldkasse mit dem Riegel, verdrahtet in beiden Betriebsarten,
  Startzeile nenneGoldmark.
- EIGENE TABELLEN UND KEINE WAEHRUNGSSPALTE: Zwei Waehrungen in kassen addieren sich zu
  einem Stand - und die tragende Zusage des Buchungsbuchs ("fuer ein eroeffnetes Konto
  ist die Summe aller Buchungen sein Stand") wuerde ihn BESTAETIGEN. Die Summenprobe
  waere gruen und das Geld falsch. Mit zwei Tabellenmengen gilt die Zusage je Waehrung,
  und MariaGoldkasseTest haelt den vollen KasseVertrag ein zweites Mal - gegen die echte
  Datenbank.
- UNTERSCHIED ZUR STAEDTEKASSE, damit das nicht verwechselt wird: Jene liegt ABSICHTLICH
  auf denselben Tabellen (dieselbe Waehrung, eine Stadtkennung ist nie eine
  Spielerkennung). Hier ist es eine andere Waehrung. 31ct haelt beides fest.
- EINE Klasse MariaKasse, keine Kopie: Die sieben SQL-Saetze haben Namen bekommen und
  entstehen EINMAL im Konstruktor; die Tabellennamen kommen aus Kassentabellen und nie
  aus einer Einstellung. Eine Kopie waere eine zweite Fassung der Transaktionsfuehrung.
- Die Betragsspalte heisst goldmark und nicht mark. Derselbe Name waere der kuerzere Weg
  und eine Luege, auf die jeder spaetere Leser hereinfaellt.
- EIGENER TYP Goldkasse und nicht ein zweites Kasse-Feld: Zwei Kassenfelder sehen an
  jeder Aufrufstelle gleich aus, und eines Tages buchte jemand Mark in die Goldmark.
  GEMESSEN: Den Umschlag wegzunehmen ergibt "Inkompatible Typen: MariaKasse kann nicht in
  Goldkasse konvertiert werden" - der UEBERSETZER ist hier der Riegel, staerker als jede
  Tafel. Die Zusicherung in 31ct, die dasselbe halten sollte, ist daraufhin wieder
  entfernt worden.
- KEIN GOLDMARK-STARTGUTHABEN, und das ist keine Einstellung: Plan 8.1 nennt genau zwei
  Quellen (Echtgeld, Tausch aus Mark). Der Riegel ist derselbe wie bei der Stadtkasse -
  eine Frage beim Start, laut. Wer verschenken will, verschenkt Mark.
- KEINE Einstellschluessel in dieser Runde. Kurs (1.000 Mark = 1 Goldmark), Rueckweg
  (1 Goldmark = 800 Mark) und Wechseldeckel (10.000 Mark/Tag) stehen in Plan 8.1 und
  kommen mit Teil F - Plan 8.7 sagt ausdruecklich, dass es diese Schluessel noch nicht
  geben soll.
- NOCH OHNE VERBRAUCHER, und der Start sagt das: "Noch gibt es keinen Weg, Goldmark zu
  bekommen - Wechsel und Muenzhaus kommen mit Teil F und G." Ohne diese Zeile waere
  "noch kein Verbraucher" von "falsch verdrahtet" nicht zu unterscheiden.
- GEMESSEN, fuenf Einspritzungen mit TREFFERTEXT plus eine am Uebersetzer: Tabellen weg
  -> 31ct nennt die MARKtabellen; fluechtiger Zweig weg -> 31ct; nenneGoldmark nicht
  gerufen -> 31ab; DELETE FROM goldbuchungen weg -> SchemaTest; Betragsspalte "mark"
  -> KassentabellenTest. Startlauf gegen das Probenschema: beide Tabellen entstehen,
  fk_goldbuchung_kasse zeigt auf goldkassen, 0 Ausnahmen.
- EIGENER FUND am Treiber: Ein Einspritzungslauf, der beim UEBERSETZEN abbricht, schreibt
  keinen Bericht - und der stehengebliebene des vorigen Falles sieht aus wie ein Fang.
  Der Treiber raeumt die Berichte jetzt vor jedem Lauf weg.
- Proben 4198 -> 4237 (core 1619, daten 366, paper 2252), 0 rot, 0 uebersprungen, 0
  Warnungen. Neu: MariaGoldkasseTest (28, voller Vertrag gegen die echte Datenbank),
  GoldkasseTest (6), KassentabellenTest (5), GrenzeTest 31ct.
- ~~Teil F: Wechsel mit Deckel.~~ 20.11. GEBAUT. Wechselwerte (zwei Kurse, Deckel),
  Wechselbuch mit beiden Richtungen, sechs Buchungsgruende, Buchungsgrund.buch(),
  Wechselliste, Abschnitt wechsel: in der config.yml, /mark tauschen mit Tafel,
  Tauschtext, GrenzeTest 31cu.
- GEWECHSELT WIRD IN GOLDMARK, in BEIDEN Richtungen - die erste Entscheidung: Wer nach
  dem Hinweg in MARK fragte, muesste mit 2.500 Mark zwei Goldmark geben und 500 Mark
  behalten. Dieser Rest wird irgendwann irgendwo verschluckt. Mit der Goldmark als
  Einheit gibt es keinen, und sie ist ohnehin die grobe Einheit des Plans.
- DER RUNDLAUFRIEGEL ist die tragende Zeile: Braechte der Rueckweg mehr, als der Hinweg
  kostet, waere jeder Rundlauf ein Gewinn - beliebig oft, ohne eine Minute Arbeit, und
  JEDE Buchung dabei richtig. Die Summenprobe des Buchungsbuchs bestaetigt dabei jeden
  Stand. Der Tagesdeckel hilft NICHT: Er begrenzt die Menge, nicht das Verhaeltnis.
  Darum weist Wechselwerte das Paar ab und der Leser verwirft BEIDE Kurse.
- DER DECKEL IST IN MARK BEMESSEN (Plan 8.1) und wird in Goldmark ABGERUNDET: 10.000
  Mark bei 800 je Goldmark sind zwoelf am Tag (9.600 Mark), nicht zwoelf Komma fuenf.
- ER KUERZT NICHT, ER SAGT AB - anders als Verdienst- und Marktdeckel. Dort wird
  gekuerzt, weil ein abgebauter Block sich nicht zuruecknehmen laesst; hier hat der
  Spieler nur eine Zahl genannt. Wer zwanzig will und zwoelf darf, soll es LESEN.
- EINE 0 HEISST HIER DAS GEGENTEIL von bundesmarkt.tagesdeckel: dort KEIN Deckel, hier
  RUECKWEG ZU. Begruendung: Bei der Arbeit waere "niemand darf verdienen" von einem
  Tippfehler nicht zu unterscheiden; ein geschlossener Rueckweg ist eine sinnvolle
  Einstellung, ein Rueckweg ohne Deckel bricht die Zusage des Plans. Der Leser nennt den
  Unterschied in seiner Berichtszeile, damit niemand den falschen Deckel auf 0 stellt.
- SECHS GRUENDE und nicht vier: Ein Wechsel bewegt Geld in ZWEI Buechern, und ein Konto
  traegt nur seine eigenen Zeilen. Dazu je Richtung eine ERSTATTUNG - und die ist der
  Grund, warum es sechs sind: Eine Erstattung mit WECHSEL_MARK_HER fuellte den
  Tagesdeckel eines Spielers, dem der Tausch misslungen ist.
- BUCHUNGSGRUND.BUCH() als Schalter OHNE default: Der Uebersetzer fragt jeden kuenftigen
  Grund, in welches Buch er gehoert. Eine Liste der Goldmarkgruende daneben waere die
  stille Vorgabe gewesen, die man zu ergaenzen vergisst.
- GEMESSEN, der Fund dabei: Die erste Fassung hiess Waehrung mit zwei Werten, und die
  Probe auf den Inhalt des Goldbuchs war ROT - dort stand eine EROEFFNUNG, und die
  schreibt die Kasse SELBST, in welchem Buch sie auch fuehrt. Daher der dritte Wert
  JEDES und gehoertIn() an EINER Stelle; zwei Vergleiche mit != haetten ihn zweimal
  uebersehen.
- ZWEI RIEGEL, keine Ueberdeckung: Die Goldkasse weist jeden Grund ab, der nicht in das
  Goldbuch gehoert (sie ist die einzige Tuer dorthin, in beiden Betriebsarten); das
  Wechselbuch prueft seine MARKbuchungen selbst (es ist die einzige Stelle, die beide
  Buecher anfasst). Fuer das Markbuch im Uebrigen gibt es keinen - dort buchen dreissig
  Stellen, und alle hatten bis heute nur Markgruende.
- GEMESSEN, Fund an Tafel 31am: Der Schluessel der Sprungbestaetigung war durch einen
  PARAMETER gereicht - und damit fuer die Tafel unsichtbar, die die Gestalt
  betrag(wurzel, ... "schluessel") liest. Die Probe meldete eine Zeile zu viel in der
  config.yml. Die Schluessel stehen jetzt am Aufruf; der Waechter sieht nur diese
  Gestalt.
- GEMESSEN, Fund im Startlauf: Die Rechenprobe stand ZWEIMAL im Protokoll - einmal aus
  dem Bericht des Lesers, einmal aus nenneWechsel. nenneWechsel ist weg. Geblieben ist
  der BERICHT, obwohl nenneRaenge es umgekehrt macht: Der Bericht erscheint auch bei
  /freimarken neuladen, und eine Zahl ist am ehesten falsch, kurz nachdem jemand sie
  geaendert hat.
- GEFUNDEN UND BEHOBEN, nicht von Teil F: raenge.an und raenge.leiter fehlten seit Teil A
  in Wirtschaftsstand.uebernimm - ihr Neuladen war voellig stumm. Jetzt werden sie
  genannt, mit eigener Probe.
- GEMESSEN, elf Einspritzungen mit TREFFERTEXT: Rundlaufriegel aus -> WechselwerteTest;
  Deckel zaehlt den falschen Grund -> WechselbuchTest; Erstattung mit dem Deckelgrund ->
  WechselbuchTest; EROEFFNUNG als Markgrund -> die Buchprobe; Abbuchen blockiert nicht
  -> ZU_WENIG_MARK; Waehrungsriegel der Goldkasse aus -> GoldkasseTest; falsche
  Markkasse in der Stube -> 31cu; die zwei Deckelsaetze vertauscht -> TauschtextTest;
  Tauschzweig nicht angemeldet -> MarkBefehlTest 39f; Rechenprobe aus dem Bericht ->
  WechsellisteTest.
- UND EINE IST ZUERST DURCHGEGANGEN: > zu >= an der Deckelgrenze blieb GRUEN. Grund ist
  Arithmetik - bei 800 Mark je Goldmark trifft kein Tausch den Deckel von 10.000 genau
  (zwoelf sind 9.600, dreizehn 10.400), also entscheidet das Zeichen dort nichts. Neue
  Probe mit Kurs 1.000: zehn Goldmark sind genau 10.000 Mark. Danach rot.
- GEMESSEN auf einem laufenden Paper 26.3 (Probenschema): Startzeile EINMAL,
  /freimarken neuladen nennt "wechsel.mark-je-goldmark: 1000 -> 2000", die
  Sprungbestaetigung fuer 10000 -> 0 wird erkannt, und der geschlossene Rueckweg wird
  gemeldet - samt dem Hinweis auf die andere Bedeutung der 0. 0 Ausnahmen.
- DER TAUSCH SELBST ist von der Konsole aus nicht pruefbar: /mark tauschen ist
  spielerpflichtig. Dafuer stehen MarkBefehlTest (durch den echten Befehlsbaum, mit
  Kontostaenden) und WechselbuchTest.
- Proben 4237 -> 4326, 0 rot, 0 uebersprungen, 0 Warnungen. Neu: WechselbuchTest (24),
  WechselwerteTest (14), WechsellisteTest (13), TauschtextTest (10), MarkBefehlTest +7,
  WirtschaftsstandTest +3, GoldkasseTest +3, GrenzeTest 31cu, BildGleich 13 Zeilen.
- NOCH NICHT DA, mit Absicht: Das Muenzhaus (Teil G) - der Verlauf des GOLDbuchs hat
  noch keine Anzeige. Die sechs Worte stehen schon in der Vorlage, weil Buchungsgrund zu
  jedem Grund ein Wort verlangt; eine Verlaufszeile in Goldmark braucht ihren eigenen
  Satz, denn waehrung.betrag sagt "Mark".
- ~~Teil G: Muenzhaus.~~ 20.11. GEBAUT. Muenzwerte (Katalog als freie Liste),
  Muenzhaus (pruefe/kaufe/tafel/konto), Vorrat als eigene Naht, Muenzliste, Muenztext,
  Muenzname, Abschnitt muenzhaus: in der config.yml, /mark muenzhaus mit Tafel, Kauf in
  zwei Stufen und Goldverlauf, GrenzeTest 31cv und 31cw.
- VERKAUFT WERDEN GEGENSTAENDE - die erste Entscheidung. Alles andere, was Plan 8.3 als
  Katalogware nennt (Partikel, Haustier, Rangfarbe, Namensanzeige, Heimatpunkt), hat
  heute KEINE Mechanik; ein Posten darauf waere echte Goldmark fuer ein Recht, das
  niemand liest. heimatpunkte ist sogar in zwei Proben als Gegenbeispiel festgenagelt.
  Ein Gegenstand wirkt am Tag des Kaufs, braucht keine Rechteverwaltung, kein
  PermissionAttachment (das ueberlebt keinen Neustart) und KEINE neue Tabelle - er liegt
  danach im Rucksack, und Minecraft speichert Inventare.
- PLATZ VOR GELD, und das ist die Reihenfolge des Kistenshops: "Erst der Vorrat, dann
  der Platz, dann das Geld." Mein erster Entwurf wollte zuerst zahlen - dann stuenden
  beim haeufigsten Fehlschlag (voller Rucksack) ZWEI Zeilen im Goldbuch fuer einen Kauf,
  der nie stattgefunden hat. Ausgerechnet in dem Verlauf, den dieser Teil baut.
- DAS HAUS NIMMT NICHTS ZURUECK. Die Naht vergleicht nur die ART eines Gegenstandes -
  Vorratshand sagt selbst, dass diese Milde fuer HANDEL nicht gilt. Ein Rueckweg per
  nimm koennte dem Spieler sein verzaubertes Schwert nehmen und ihm das gelieferte
  blanke lassen, und keine Probe saehe es. Darum wird vorher nach dem Platz gefragt; und
  wenn danach doch etwas schiefgeht, verliert der SERVER ein paar Blumen.
- ZWEI BUCHUNGSGRUENDE, beide im Goldbuch: der Kauf und eine Erstattung. Letztere ist
  nur noch der Programmfehlerpfad (ein Wurf nach dem Abbuchen) - Buchung wirft bei
  falscher Richtung, ein Grund allein geht also nicht.
- DIE VERLAUFSZEILE BEKOMMT DAS BUCH, nicht einen Formatierer. Ein
  LongFunction<String> haette genau den Riegel aufgehoben, den Sprache.goldmark dafuer
  gebaut hat ("jede Aufrufstelle nennt sie beim Namen"): Beide Zahlen sind long, beide
  Methoden passen auf dieselbe Signatur. Mit dem Buch ist es ein erschoepfender
  Schalter, und die Zeile prueft zusaetzlich, dass der GRUND in dieses Buch gehoert -
  eine Markzeile im Goldverlauf wirft jetzt, statt zu luegen. Die Zeile hatte DREI
  Geldstellen, nicht eine: zwei davon in mitVorzeichen.
- DIE BEDINGUNG IST DIE VORHANDENE BEDINGUNGSART, kein zweiter Typ - sonst gaebe es
  ausSchluessel, alleSchluessel und inMesseinheit zweimal. Rangregel hat dafuer zwei
  neue Tueren, die eine blosse Bedingungskarte nehmen; gemessen wird weiter an EINER
  Stelle (Aufstiegslauf.messeFuer), damit ein Kauf nicht anders urteilt als eine
  Befoerderung. Und Bedingungsart.inDerDatei ist der Rueckweg: Fehlend rechnet in
  SEKUNDEN, ungerechnet gezeigt stuende da "noch 18.000 Stunden".
- RUECKFRAGE VOR DEM KAUF (kaufen <posten>, dann bestaetigen), wie /beruf verlassen und
  /freistadt gruenden: Der Posten wird GETIPPT, er kostet Goldmark, und verkauft wird
  nichts. pruefe und kaufe gehen denselben Weg - wer eine Bedingung nicht erfuellt,
  erfaehrt es VOR der Bestaetigung.
- EINE ZEILE JE POSTEN, mit Name, Preis und Inhalt darin (technische Namen, wie bei den
  Aufgaben). Mit dem Inhalt in eigenen Zeilen waere die Tafel bei vier Posten zwischen
  sieben und siebenundzwanzig Zeilen lang - fehlend ist je Posten eine LISTE.
- KEIN SPRUNGSCHUTZ fuer die Preise: betrag() braucht je Wert einen festen Schluessel
  unter wirtschaft.sprung-bestaetigt, und den hat ein frei getippter Posten nicht;
  Tafel 31am prueft Mengengleichheit in beide Richtungen. Die Schranke ist die
  RECHENPROBE - sie nennt jeden Preis auch in Mark, und "ein Hut kostet 5.000.000 Mark"
  sieht jeder. Gerechnet wird mit dem HINWEGkurs, und ein Preis von 0 wird nicht
  gerechnet: kaufpreis(0) wirft und riss den Startlauf mit.
- GEMESSEN, Fund in Teil F: Mein eigener tauschZweig hatte kein eigenes .executes am
  Literal "kaufen" - "/mark tauschen kaufen" antwortete englisch (Brigadier verdeckt mit
  einem passenden Literal jedes Geschwister). Beide Literale sind nachgetragen, und die
  Probe, die den Wurf verlangte, verlangt jetzt die Tafel.
- GEMESSEN, Fund an Bukkit: Material.getMaxStackSize() WIRFT ohne laufenden Server (No
  RegistryAccess implementation found), derselbe Weg wie new ItemStack. Darum kommt der
  Rucksack als NAHT in die Befehle (Function<Player, Vorrat>, wie bei /wolke abgeben) -
  erst damit laeuft der ganze Kaufzweig durch den echten Befehlsbaum, auch der volle
  Rucksack.
- GEFUNDEN UND BEHOBEN, nicht von Teil G: SIEBENUNDZWANZIG Einstellpfade fehlten in
  Wirtschaftsstand.uebernimm - laeden.* (fuenf, davon drei GELD), handel.* (fuenf),
  freistaedte.buergerrechte/.erstattung/.ladengebuehr.*, freistaedte.verfall.* (vier),
  wolken.erze.* (sechs), wolken.starttruhe, wolken.biomwechsel, wolken.aufgaben.*,
  wolken.punkte.*. Ihr Neuladen war VOLLSTAENDIG stumm. Es ist derselbe Fund zum
  dritten Mal (Auflagenschalter 17.11., Rangleiter 19.11.), und darum haelt ihn ab jetzt
  Tafel 31cw: Jeder PFAD_* aus dem Kern muss genannt sein oder in einer BEGRUENDETEN
  Ausnahmeliste stehen, in beide Richtungen.
- DIE TAFEL FAND SOFORT IHREN ERSTEN FEHLER - in meiner Ausnahmeliste:
  Blockbuchwerte.PFAD_AN gibt es gar nicht. Genau dafuer ist sie da.
- GEMESSEN, elf Zeilen je Sendestelle: ParzellenBefehle sendet 47 -> 51 mal; zwei neue
  Zeilenmethoden in Tafel 31d; 33 neue Zeilen in der Bildtafel, davon 18 fuer die
  gezaehlten Saetze (1, 2 und 0 - die Null waehlt die viele-Zeile).
- NEUNTE MECHANISCHE FAMILIE: die fuenf Bedingungsworte, ueber MarkSatz.bedingungssatz
  gewaehlt. Art.GEZAEHLT und nicht ein Wort mit Zahl davor, denn "1 eigene Parzellen"
  ist falsches Deutsch - und genau diese Zahl steht in der ausgelieferten Datei.
- GEMESSEN, vierzehn Einspritzungen mit TREFFERTEXT: Posten ohne Gegenstaende erlaubt ->
  MuenzwerteTest; Platz erst nach der Zahlung -> MuenzhausTest; Erstattung weg (zweimal,
  Zweig und Faenger) -> MuenzhausTest; Bedingung nicht geprueft -> MuenzhausTest;
  abbuchen ohne Riegel -> MuenzhausTest; Goldverlauf in Mark -> VerlaufszeileTest;
  Buchpruefung weg -> VerlaufszeileTest; Bedingungszahl in Sekunden -> MuenztextTest;
  Materialpruefung weg -> MuenzlisteTest; Rechenprobe mit dem Rueckwegkurs ->
  MuenzlisteTest; kaufen ohne .executes -> MarkBefehlTest 39n; eigene Goldkasse ->
  31cv; eine Neuladezeile weg -> 31cw.
- Proben 4326 -> 4453, 0 rot, 0 uebersprungen, 0 Warnungen. Neu: MuenzhausTest (22),
  MuenzwerteTest (17), MuenzlisteTest (19), MuenztextTest (17), MarkBefehlTest +6,
  VerlaufszeileTest +3, WirtschaftsstandTest +4, RangregelTest +2, BildGleich +33,
  GrenzeTest 31cv und 31cw.
- PRUEFRUNDE ZU TEIL G, 20.11.: 27 Befunde gemeldet, 14 haben die Widerlegung
  ueberlebt, alle behoben. Die vier schweren:
- DIE PLATZRECHNUNG WAR ZU OPTIMISTISCH. Sie fuellte angefangene Stapel nach der ART
  auf, Bukkits addItem aber nur bei AEHNLICHKEIT (firstPartial fragt isSimilar). Ein im
  Amboss umbenannter Stapel war fuer die Rechnung ein Aufstockziel und fuer Bukkit
  keines: abgebucht, nichts angekommen, zwei Zeilen im Goldbuch fuer einen Kauf, der nie
  stattfand - und bei halber Lieferung GRATIS, denn was ankommt bleibt und erstattet wird
  der ganze Preis. Jetzt ist ein Platz mit eigener Gestalt belegt, aber kein
  Aufstockziel; die Richtung ist pessimistisch.
- UND SIE WAR UNPRUEFBAR. Die Schleife stand in der anonymen Klasse, hinter einem Player
  und hinter getMaxStackSize - keine der 4453 Proben konnte sie durchlaufen. Jetzt ist
  sie eine statische Methode, die Stapelgroesse kommt als ZAHL herein, und
  VorratshandTest (10 Proben) rechnet sie nach. Was aus Bukkit hereingeholt wird, haelt
  die Quelltextzusage 31cx.
- MATERIAL IST NICHT GEGENSTAND. "potatoes" ist der Feldblock, "potato" der Gegenstand -
  dasselbe bei carrots, beetroots, water. Der Leser kann das nicht fragen (isItem wirft
  ohne Server), also faellt die Frage an der NAHT: Vorratshand.art gibt null, der Posten
  sagt ab, bevor gezahlt wird. Dazu nennt nenneUnlieferbarePosten beim Start jeden
  solchen Posten - und jeden, der mehr als 36 Lagerplaetze braucht (40 Musikscheiben
  sind 40 Plaetze; die Stueckgrenze rechnet in Stueck).
- EIN ZUGESAGTER RUECKFALL WAR NICHT GEBAUT. Drei Javadocs, eine Startmeldung und die
  Vorlage sagen zu: Ein Posten ohne Namenszeile bleibt kaufbar und zeigt seinen Pfad. In
  Wahrheit warf JEDER Muenzhausbefehl - auch die vier richtigen Posten waren weg -, und
  ein Kauf buchte ab, lieferte, schrieb die Zeile und antwortete mit einer roten
  englischen Serverzeile; der Spieler haelt ihn fuer gescheitert und kauft zweimal.
  Behoben in Sprachbuch.wortlaut, NUR fuer die offenen Familien - dieselbe Zeile loest
  Muenzname und Aufgabenname (Teil E) zugleich ein.
- VOM GELD REDET JETZT NUR, WO GELD BEWEGT WURDE. Ein Satz fuer zwei Lagen: Wer nur die
  Tafel aufschlug und auf eine schweigende Datenbank traf, las "Dein Geld ist zurueck".
  Zwei Bescheide (GESTOERT, ERSTATTET) und zwei Saetze.
- GELD SIEHT UEBERALL GLEICH AUS. Die Bedingung guthaben stand nackt da, mit "Mark" fest
  in der Sprachdatei - derselbe Betrag hiess zwei Zeilen hoeher "50.000 Mark". Jetzt
  entscheidet Bedingungsart.istGeld (erschoepfender Schalter), und die Zahl kommt aus
  waehrung.betrag.
- DIE SUMME EINES POSTENS wird geprueft, nicht nur die Zeile: Zwei Zeilen von je 2.000
  Stueck bestanden jede fuer sich. stueckzahl() rief vorher niemand im Hauptcode - eine
  Zahl, die nirgends ankommt, ist keine Grenze.
- DREI BEFUNDE AN DEN TAFELN SELBST: 31cv prueffte nur, DASS der Messaufruf irgendwo in
  der Datei steht (jetzt ein Muster ueber die Bauzeile, wie 31cu); 31cw las
  Wirtschaftsstand MIT Kommentaren, eine Nennung im Kommentar galt als Neuladezeile
  (jetzt Quellen.text - derselbe Fehler wie damals bei jederSchluesselWirdGesendet); und
  31cw sah nur PFAD_*, nicht die UNTERSCHLUESSEL der Listenwerte.
- UND DAFUER WAR DAS FEHLERBILD SCHON DA, zum vierten Mal: gegenstaende und bedingungen
  je Posten, zahlen, bedingungen und rechte je RANGSTUFE, verlangt und belohnung je
  HERAUSFORDERUNG wechselten beim Neuladen vollstaendig stumm. Die Antwort ist je
  Tabelle eine Zeile "N -> N Zeilen (geaendert)" - kurz wie bisher, aber nicht stumm.
  31cw sieht jetzt PFAD_ und SCHLUESSEL_; die zwei Erzschluessel stehen mit Begruendung
  in der Ausnahmeliste (sie stecken in der Tabelle, die als Ganzes verglichen wird).
- DIE PREISGRENZE wurde nie GENAU getroffen: < und <= waren dasselbe.
- GEMESSEN, zwoelf neue Einspritzungen gegen die Behebungen: alle gefangen (mit den
  vierzehn von Teil G zusammen 26). Darunter die Probe der Probe - eine Neuladezeile, die
  nur noch im Kommentar steht, macht 31cw rot.
- GEMESSEN AM LAUFENDEN SERVER, was keine Probe sehen kann: Mit drei absichtlich kaputten
  Posten meldet der Start "kartoffelkiste: potatoes gibt es nur als Block" und
  "plattensammlung: braucht 40 Lagerplaetze, ein Spieler hat 36"; ein Neuladen nennt
  raenge.leiter.buerger.zahlen, muenzhaus.katalog.kuerbishut.gegenstaende und
  muenzhaus.katalog.blumenkiste.bedingungen - vorher lautete die Antwort "nichts
  geaendert". 0 Ausnahmen.
- Proben 4453 -> 4480, 0 rot, 0 uebersprungen, 0 Warnungen. Neu: VorratshandTest (10),
  WirtschaftsstandTest +4, MuenztextTest +4, MuenzwerteTest +4, SpracheTest +2,
  RangregelTest +1, GrenzeTest 31cx, BildGleich +1.
- NOCH NICHT DA, mit Absicht: eine Grenze je Spieler ("hoechstens einmal kaufen")
  braeuchte eine Ablage; ein Gegenstand ist ohnehin beliebig oft sinnvoll. Und eine
  Anzeige auf einem BLOCK gibt es im ganzen Plugin nicht - "der Wechseldeckel am Ort"
  ist die Chattafel des Hauses, und genau das sagt MarkSatz seit Teil F.
- Offen in Teil A, mit Absicht: Der RUNDGANG (Plan 9, Siedler) und "eigene Parzelle
  bebaut" (Plan 9, Buerger) sind keine Bedingungsart - den Rundgang gibt es erst mit
  Schritt 13, und was "bebaut" misst, steht im Plan nirgends. Beide kommen als Zeile dazu,
  ohne dass sich sonst etwas aendert.
- ~~Teil H: Grenzen an den Rang.~~ 20.11. GEBAUT. Damit ist Schritt 12 durch.
- DIE VIER WARNUNGEN SIND WEG, und zwar an der Ursache: parzellen.hoechstzahl und
  gruppe-hoechstzahl stehen auf 1 - der Zahl des REISENDEN. Die Staffel 1/2/4 steht in
  raenge.leiter, wo sie hingehoert. Vorher bekam jeder Reisende die Buergerzahl, denn es
  gilt immer die hoechste Zahl aus Recht UND Datei.
- FUENFTE GRENZE: freistaedte. /freistadt gruenden fragt jetzt Rangzahl
  (freimarken.freistaedte.<n> plus Zuschlag freimarken.extra.freistaedte.<n>) - bis dahin
  nahm der Riegel allein die Zahl aus der Datei, waehrend die Absage dem Spieler sagte
  "so viele, wie dein RANG erlaubt".
- hinweise.rang-werbung haengt jetzt an ALLEN Absagen: Meerinsel, Wolkeninsel und
  Freistadt haben ihren eigenen Farbschluessel (werder/wolke/freistadt.rang-werbung), die
  REGEL steht einmal in Rangwerbung.
- DIE WARNUNG UNTERSCHEIDET VIER LAGEN, vorher eine: ganze Leiter tot (alt), nur die
  unteren Stufen tot (Leiter 1/2/4 und Datei 2 - das verschwieg der alte Vergleich),
  die 0, die NICHT abschaltet, und richtig eingestellt = STILL. Ohne das Stille waere
  nach Teil H die Haelfte aller Zeilen eine Warnung, die niemand beheben kann.
- GEFUNDEN UND BEHOBEN, nicht von Teil H: Die ausgelieferte config.yml versprach bei den
  Inseln "eine 0 schaltet es ab, ohne das Recht anzufassen" - und ihre eigene Leiter
  vergab freimarken.wolken.1. Zwei Zeilen in zwei Abschnitten, jede fuer sich richtig.
  Jetzt sagt es der Startlauf.
- GEFUNDEN UND BEHOBEN, nicht von Teil H: Der Kommentar an inseln.hoechstzahl nannte
  freimarken.werder.anzahl.<n> - ein Recht, das es nicht gibt (gelesen wird inseln).
  Haelt jetzt Tafel 31cy: Jedes Recht, das eine ausgelieferte Datei NENNT, muss es geben.
- UND EINE LUECKE IN DEN PROBEN: Keine einzige las die Zahlen der ECHTEN config.yml. Die
  neue Probe faehrt die ausgelieferte Datei durch denselben Leser und dieselbe Warnung wie
  der Startlauf - sonst koennten Datei und Programmvorgabe stumm auseinanderlaufen.
- GEMESSEN, dreizehn Einspritzungen: alle gefangen. Darunter die Tafel selbst (31cq ohne
  FreistadtBefehle) und der leere Werbesatz, der doch eine Zeile sendet.
- GEMESSEN AM LAUFENDEN SERVER: Mit der ausgelieferten Datei kommt KEINE Grenzwarnung
  mehr (vorher vier). Mit drei absichtlichen Fehlern kommen genau drei - je Lage eine -,
  und freistaedte bleibt dabei still. 0 Ausnahmen.
- Proben 4480 -> 4491, 0 rot, 0 uebersprungen, 0 Warnungen. Neu: RanglisteTest +6,
  GrenzeTest 31cy, FreistadtBefehlTest +1, BildGleich +3.
- WER SCHON SPIELT, verliert nichts: Der Nachstufungslauf beim Start hebt jeden auf den
  Rang, der ihm nach seiner gemessenen Zeit zusteht, und Gebautes bleibt stehen - eine
  gesenkte Grenze verhindert nur die naechste Parzelle.
- GEFUNDEN UND BEHOBEN, nicht von Teil H: Zwei Kommentare der ausgelieferten config.yml
  nannten noch Hoehen aus dem Altfurt VOR der Ebene (Spawn 72/72/12, Beispieltruhe
  [72, 72, 20]). Wer das Beispiel abschrieb, setzte eine Truhe 133 Bloecke ueber den
  Boden. Jetzt -60, und daneben steht, warum eine Ebene keine zweite Gestalt hat.
- VON HAND auf dem Server: Die laufende plugins/Freimarken/config.yml behaelt ihre 4
  (saveDefaultConfig schreibt eine vorhandene Datei nie um). parzellen.hoechstzahl: 1,
  parzellen.gruppe-hoechstzahl: 1, dann /freimarken neuladen.

### Nachlese zu Schritt 12 - 20.11.2026

- /RANG GEBAUT: /rang (auch /rank) zeigt die eigene Stufe,
  die naechste mit JEDER Bedingung samt Stand, /rang leiter die ganze Leiter in drei
  Zustaenden. Rangschau im Kern stellt zusammen, Rangtext baut die Zeilen, RangBefehle
  ist der Baum. Das Recht freimarken.rang ist default true.
- Die Bedingungsworte kommen aus dem MUENZHAUS (mark.muenzhaus.bedingung.<art>) - dieselbe
  Frage, dieselbe Antwort. Neu ist nur "du hast {stand}", einmal gruen und einmal grau.
- GEMESSEN WIRD NUR, was die naechste Stufe verlangt: Auf der hoechsten loest /rang keine
  einzige Datenbankabfrage aus. Die Messnaht ist woertlich die des Muenzhauses
  (Bedingungsmessung, jetzt eine eigene Datei statt verschachtelt im Muenzhaus).
- Rangregel.Fehlend heisst jetzt Bedingungsstand und kann erfuellt(); stand() nennt JEDE
  Bedingung, fehlend() filtert sie. EIN Satz statt zweier gleich gebauter.
- ERREICHT kommt aus der NUMMER und nicht aus den Bedingungen: Wer befoerdert wurde,
  behaelt seinen Rang, auch wenn der Betreiber die Bedingungen danach heraufsetzt.
- Ein Eintrag, den die Leiter nicht mehr kennt, wird GENANNT (raenge.schau.unbekannt mit
  dem eingetragenen Schluessel). stufeIn faellt auf die unterste Stufe zurueck - richtig,
  aber als Auskunft eine stille Rueckstufung, die niemand melden kann.
- GELD VON HAND: /freimarken geld <spieler> <betrag>,
  ein Minus zieht ab, EIGENES Recht freimarken.geld (default op). Zwei neue
  Buchungsgruende, je Richtung einer; jeder Versuch steht im Serverprotokoll, die Buchung
  im Verlauf des Spielers ("Vom Betreiber gutgeschrieben").
- ABSICHTLICH OHNE Meldung an den Spieler: Was er bekam, steht in /mark verlauf. Eine
  Zeile dazu waere ein neuer Spielersatz; wenn sie gewollt ist, ist sie drei Zeilen.
- Die Schranke 1.000.000.000 Mark im Baum ist TECHNISCH und keine Spielzahl: Sie faengt
  den Tippfehler mit drei Nullen zu viel.
- DIE VIER WELTNAMEN sind nicht neuladbar und sagen es: fehltText und die
  Sammelzeile fragen jetzt EINE Frage (Wirtschaftsliste.nurBeimStart). Fuer eine fehlende
  Weltzeile steht nicht mehr "es gilt wolkenwerder" - eine Geltung, die erst der naechste
  Start herstellt.
- GEFUNDEN DABEI: Derselbe falsche Satz stand noch einmal daneben - "Dafuer gelten die
  Vorgaben des Programms." in acht Lesern, drei davon zaehlen einen Weltschluessel mit.
  Die Zeile entsteht jetzt an EINER Stelle und nimmt die nicht neuladbaren aus.
- GEFUNDEN DABEI: Der Kopfblock der Markthalle stand ohne Leerzeile ueber freistaedte: -
  in YAML dokumentierte er damit die Freistaedte, und laeden.welt war der einzige der
  vier ohne den Vermerk "NUR BEIM START GELESEN". Haelt jetzt Tafel 31da.
- DIE BUERGERRECHTS-NEULADEZEILE war am 20.11.
  schon gebaut (taten()); gefehlt hat die PROBE. 31cw prueft nur, dass der Name im
  Quelltext vorkommt - mit taten(alt...) zweimal waere sie gruen und das Neuladen stumm.
- /MARKT LISTE: Der Fang stand seit 10.11. Echt
  offen waren drei andere Dinge: EIN Satz fuer ZWEI Quellen (bei stummer Kasse las der
  Spieler, die Staende seien nicht lesbar), die geholte Liste wurde weggeworfen, und die
  Kassenrichtung hatte keine Probe. Jetzt zwei Faenge, ein eigener Satz
  (markt.deckel-unlesbar) - und ohne Tagesdeckel wird die Kasse gar nicht gefragt.
- DIE UMBRUCHMARKE in einem einzeilig geholten Satz klagt jetzt beim LADEN: Die
  Liste Einzeiler (103 Saetze, GERECHNET aus den eineZeile-Stellen) und die Klage beim
  LADEN. Tafel 10j haelt die Liste in beide Richtungen gegen den Quelltext.
- GEFUNDEN DABEI: kennungUeberBukkit stand ZWEIMAL wortgleich im Projekt (BlockBefehle,
  ShopBefehle), beide privat. Jetzt einmal in Spielername; Tafel 31dc haelt, dass
  hasPlayedBefore() in genau einer Datei steht - ohne die Schranke bucht ein Tippfehler
  auf ein Konto, das die Kasse selbst anlegt.
- Proben 4491 -> 4554, 0 rot, 0 uebersprungen, 0 Warnungen. Neu: RangschauTest,
  RangBefehlTest, BetreibergeldTest, RangregelTest +3, Grenze 31cz/31da/31db/31dc,
  Vorlage 10j, Sprachliste 21d/21e, BildGleich +15.
- GEMESSEN AM LAUFENDEN SERVER: freimarken:rang und freimarken:rank sind angemeldet,
  0 Grenzwarnungen, 0 Ausnahmen. Auf der Konsole antworten /rang und /freimarken geld mit
  ihren eigenen Saetzen - nicht mit Brigadiers englischer Zeile.
- GEMESSEN, einundzwanzig Einspritzungen: zwanzig von einer Probe gefangen, der
  einundzwanzigste vom STARTLAUF. Er ist ein Befund ueber die Tafel: Die Anmeldezeile von
  /rang in ein "if (sprache == null)" gehuellt laesst den Text stehen, und jedes contains
  bleibt gruen. Mit Einspritzung fehlt freimarken:rang in "Befehle angemeldet" und die
  Konsole bekommt Brigadiers englische Zeile; ohne sie ist beides richtig. Die Grenze
  steht jetzt in Tafel 31db selbst.

## Schritt 11: Wolkenwerder

- ENTSCHIEDEN 18.11.2026 (vier Fragen): Die Insel-Stufe kommt aus BEIDEM (Blockscan fuer die Punktzahl, Herausforderungen fuer die Stufe am Generator), der Befehl heisst /wolke als EIGENER Baum, und dazu gehoeren auch Zuruecksetzen, Biomwahl, Inselrangliste und Mitbauer. Der Kobbelsteingenerator ist ausdruecklich DER Reiz der Welt und gibt Erze mit gewichteter Wahrscheinlichkeit.
- EIGENE LESART beim Lohn, und sie steht hier, weil der Nutzer die Frage anders beantwortet hat als gestellt: Die Erze BLEIBEN der Reiz und bringen ueber den Bundesmarkt ihr Geld. Was nicht dazukommt, ist der LOHN des Bergmanns auf einen Block, den niemand gesetzt hat - das waere eine Lavaquelle, die ohne Zutun den Tagesdeckel fuellt, und der Quelltext nennt genau das selbst "die Gelddruckmaschine". Es wird eine EINSTELLUNG (wolken.erze.marken, ab Werk true) mit dem Grund daneben; umdrehen ist eine Zeile.
- BAUPLAN in sechs Teilen: A die Welt, B Insel nehmen/aufsetzen/schuetzen samt Mitbauern, C der Erzgenerator, D Herausforderungen und Stufe, E Punktzahl und Rangliste, F Biom und Zuruecksetzen.
- ~~Teil A: die Welt.~~ 18.11. GEBAUT. art: wolken ist die SIEBTE Weltart. Felder 128x128, dazwischen 384 Bloecke Leere - eine Rasterzelle ist damit 512 Bloecke und genau EINE Regionsdatei, und ein Feld beginnt an deren Ecke. Aus Kante 20000 werden 38 Felder je Achse, 1444 Felder, 1443 Inseln; Grenze 19840 um -192/-192.
- VIERMAL SO VIELE INSELN WIE HAFENWERDER, und das ist gerechnet und nicht grosszuegig: Die Wolkeninsel ist gratis (Plan 6.2, der zweite der zwei gratis Einstiege), und von etwas Gratisem werden mehr Stuecke genommen. Kosten tut es nichts - eine leere Welt hat keine Chunks, solange niemand eine Insel nimmt.
- DER GENERATOR ERZEUGT GENAU EIN FELD: 0/0, die Ankunftsplattform aus Steinziegeln mit Gelaender auf Hoehe 64. Jede Startinsel setzt das PLUGIN beim Nehmen, und dafuer gibt es drei Gruende, von denen jeder reicht: (1) Ein ChunkData traegt keine Blockwesen - eine Truhe mit Inhalt ist im Generator nicht zu setzen, und sie ist der Anfang des Spiels. (2) Wer aufsetzen kann, kann zuruecksetzen; eine Insel ohne Lava waere sonst auf Lebenszeit festgefahren, und ihr Feld mit ihr. (3) Gebackene Inseln hiessen 1443 Inseln, die vorgeneriert werden muessten.
- DAS ANKUNFTSFELD MUSS der Generator aber backen: Ankunftspunkt sucht am Erscheinungspunkt den hoechsten FESTEN Block und braucht zwei freie darueber. Findet er in acht Ringen keinen, sagt /warp ab, das Tor tut nichts, und der Start klagt bei JEDEM Mal "kein Platz zum Stehen. Dorthin fuehrt keine Reise." In einer Welt aus Leere ist das der Normalfall.
- DAS GELAENDER STEHT NUR DORT, und das ist eine Entscheidung mit zwei Haelften: Auf dem oeffentlichen Platz kostet ein Sturz die Sachen, mit denen jemand gerade aus Altfurt angekommen ist - und niemand darf dort eine Mauer bauen, die Schutzwache verbietet es. Auf der eigenen Insel waere ein Ring genau dort im Weg, wo Lava und Wasser hingehoeren, und der Fall ins Nichts ist die Regel, mit der in einer Skyblockwelt jeder rechnet.
- KEINE BARRIERE zwischen den Feldern, anders als bei den Stadtfeldern. Gebaut wird in der Leere trotzdem nicht - das verbietet die Wache und nicht die Welt (Teil B).
- DIE STARTINSEL: 9x9 Wiese, zwei Schichten Erde darunter (243 Bloecke, und sie wachsen nicht nach), eine EICHE mit fuenf Stammbloecken und eine Truhe. Eiche und kein anderes Holz: Ihr Laub gibt Setzlinge UND Aepfel - der Setzling ist der zweite Baum, der Apfel das erste Essen. Der Baum ist von Hand nachgebaut und nicht von Minecraft erzeugt: generateTree wuerfelt die Gestalt, und dann waeren zwei Inseln verschieden und ein Zuruecksetzen etwas anderes als das Aufsetzen.
- HERAUSGEZOGEN STATT ABGESCHRIEBEN: Feldgrenze (Freistaedte) und Inselgrenze (Hafenwerder) waren bis auf drei Dinge dieselbe Klasse - Plantyp, ausgelieferte Kante und das Wort fuer das Gezaehlte. Wolkenwerder waere die DRITTE Abschrift geworden. Der Rumpf steht jetzt einmal in Feldfenster; die drei Klassen bleiben als NAME ihrer Welt. Beweis, dass sich nichts geaendert hat: FeldgrenzeTest und InselgrenzeTest sind unveraendert gruen - dieselbe Probe wie beim Herausziehen der Feldrechnung.
- FUND, der keiner Probe gehoert: Eine leere Welt MIT Grenze wird vom Start fuer immer zum Vorgenerieren gemahnt. Die neun Stichprobenchunks gelten erst nach einem Chunky-Lauf als fertig, und in einer Welt aus Luft gibt es nichts zu erzeugen - die Warnung kaeme bei jedem Start und verschwaende nie. Dagegen Weltangabe.vorzugenerieren(): ein Verzweig ueber ALLE Arten ohne default, LEER und WOLKEN sagen nein. Beide Stellen, die vom Vorgenerieren reden, fragen ihn.
- FUND in drei Proben: wolkenwerder war der Beispielname fuer eine Welt, die es NICHT gibt (WeltenlisteTest, LadenlisteTest, WarpverzeichnisTest). Seit es sie gibt, wurde EINE rot - und zwei blieben gruen und pruefen nichts mehr. Sie heissen jetzt nebelland. Genau die Falle aus der Erinnerung "Grenzmuster schaerfen": Ein Beispiel, das Wirklichkeit wird, macht seine Probe stumm.
- FUND der eigenen Probe: Weil der ausgelieferte Preis 0 ist, ist JEDE andere Zahl ein Sprung ueber das Doppelte - der Preis braucht also wolken-kauf unter sprung-bestaetigt. Das ist richtig (aus gratis etwas Kostenpflichtiges zu machen merkt jeder Spieler sofort) und steht so in der Datei; die Probe geht jetzt beide Wege, mit und ohne Bestaetigung.
- NEBENBEI BERICHTIGT: Der Abschnitt sprung-bestaetigt behauptete "NEUN Zeilen" - es waren vierzehn, jetzt fuenfzehn. Die Zahl haelt keine Probe (31am vergleicht nur die Schluesselmengen), und sie stand seit dem 10.11. falsch da.
- DIE BIOMTAFEL IST GETEILT: Die NAMEN stehen im Kern (Wolkenbiome), weil der Leser der config.yml jede Zeile ohne laufenden Server pruefen muss - Minecrafts Biomverzeichnis wirft dort, wie new ItemStack. Die Umwandlung in ein Biom steht im Plugin (Biomtafel) als VERZWEIG ueber Zeichenketten. Tafel 31ay haelt beide Richtungen: ein Name ohne Zweig waere ein Biom, das der Befehl anbietet und das Plugin nicht setzen kann.
- UND DIE FARBTAFEL IST VOLLSTAENDIG AB TAG EINS: getBiomes wird GENAU EINMAL beim Laden der Welt gefragt. Was dort fehlt, laesst sich in dieser Welt nie setzen - ein spaeter angebotenes Biom wirkte erst nach dem Loeschen des Weltordners. Darum gibt der Generator die ganze Tafel ab und nicht die Einstellung; eingestellt wird nur, welche davon ANGEBOTEN werden.
- ZEHN BIOME, und jedes mit einem Grund: swamp gibt SCHLEIM (und damit Kolben und Lasso), snowy_plains gibt Eis (und damit Wasser), mushroom_fields laesst KEIN Monster erscheinen, desert laesst es nie regnen. Nicht dabei: the_void (nichts waechst, nichts erscheint) und jedes Nether-Biom (dort verdampft Wasser).
- KEINE TIERE in Wolkenwerder, und das ist die Welt und keine Einstellung: Friedliche Tiere entstehen beim ERZEUGEN eines Chunks, und dann liegt dort kein Block - die Insel kommt erst danach aus der Hand des Plugins. Tiere muss man sich holen; Monster kommen im Dunkeln und zur Laufzeit wie ueberall.
- Proben 3630 -> 3692. Neu: FeldfensterTest (9), WolkenplanTest (11), WolkengrenzeTest (7), WolkenwerteTest (9), WolkenbiomeTest (5), WolkenbodenTest (6), WolkenlisteTest (13), Tafeln 31ay und 31az. Nachgezogen: WeltenlisteTest (neun Welten, acht Tore), WarpverzeichnisTest (NEUN statt ACHT), VorlageTest 10e (zwei Mark-Zeilen), GrenzeTest 31c (41 Sendestellen), WirtschaftsstandTest (18. Bestandteil).
- ~~Teil B: Insel nehmen, aufsetzen, schuetzen, Mitbauer.~~ 18.11. GEBAUT. /wolke mit fuenf Zweigen (liste, nehmen, heim, mitbauer dazu/weg), Wolkenbuch im Kern, Wolkenwache als vierte Ortsregel, Wolkenaufbau als Hand, die 810 Bloecke setzt.
- NEHMEN TUT DREI DINGE AUF EINMAL, und das ist der Unterschied zu jeder anderen Besitzart: Es SUCHT das Feld (ringweise von der Mitte, wie eine Freistadt), SETZT die Insel darauf und BRINGT den Spieler hin. Keines davon kann entfallen - in einer Welt aus Leere steht man vor dem Nehmen nirgends und nach dem Nehmen weiter auf der Ankunftsplattform.
- EIGENE BESITZART wolke, und sie kostet einen Schemaschritt (28): Nur mit eigener Art laesst sich NACH ihr fragen, und genau das braucht die Zuteilung. Mit art='werder' fuer beide Werderwelten liefe die Suche nach freien Feldern ueber Hafenwerder mit. Die Pruefsumme kommt aus dem roten Lauf von SchemaTest, nicht aus eigener Rechnung.
- EINE ABFRAGE JE ART STATT ZWEIER: staedteIn(welt) heisst jetzt besitzIn(welt, art). Zwei Methoden mit demselben SQL und zwei Sortierungen waeren der naechste Ort, an dem eine stehen bleibt - 15 Stellen in sechs Dateien umgestellt, Vertragsprobe mit.
- ORDER BY name, id, und die id ist die tragende Haelfte: Bei art='wolke' ist jeder name NULL, und ohne das zweite Feld gibt MariaDB die Zeilen in beliebiger Folge. Eine Zuteilung, die ihr folgt, naehme zweimal hintereinander ein anderes Feld. SpeicherLager sortiert darum mit nullsLast - ein blosses comparing(Parzelle::name) wirft bei der ersten Wolkeninsel.
- HERAUSGEZOGEN, zweites Mal: Die RINGSUCHE nach einem freien Feld stand im Freistadtbuch und waere hier die zweite Abschrift geworden. Sie steht jetzt in Feldfenster.freiesFeld; FreistadtbuchTest bleibt unveraendert gruen.
- HERAUSGEZOGEN, drittes Mal: Die Suche nach einem Spieler AM NAMEN stand in ParzellenBefehle, samt ihrer teuer erkauften Begruendung (nicht getOfflinePlayer(String) - das fragt bei Mojang nach; nicht getOfflinePlayers() - das baut die Liste aller je angemeldeten Spieler neu auf). Sie steht jetzt in Spielername.kennung.
- GEMESSEN UND ZUR NAHT GEMACHT, zwei Stellen: (1) new ItemStack wirft ohne Server, also kann der Befehl die Startinsel nicht selbst setzen - das Aufsetzen kommt als WolkeBefehle.Aufsetzer herein. (2) Material.isSolid() WIRFT ebenfalls ohne Server ("No RegistryAccess implementation found"), und es sitzt in Ankunftspunkt.taugt, also hinter Reise.fuehre. Ein erfolgreiches REISEN ist damit von keiner Probe durchlaufbar - und /wolke nehmen reist immer. Die Reise kommt darum als WolkeBefehle.Heimweg herein.
- OHNE DIESE ZWEI NAEHTE waere der Zweig, der EIGENTUM VERGIBT, von keiner Probe durchlaufen worden. Das ist derselbe Fund wie der Stapelbau in Schritt 10, nur an der teureren Stelle.
- FUND in Reise.fuehre: Es fragt die Aufwaermzeit als ERSTES und ohne null-Pruefung. WarpBefehlTest gibt dort null herein und ueberlebt es nur, weil jene Probe nie bis zum Ortszweig kommt (ihr Klassenkopf sagt es selbst). Mit null kam hier eine NullPointerException in JEDEM reisenden Zweig. Nicht geaendert, nur gewusst: Die Probe baut jetzt eine echte Aufwaermzeit (ohne Zeitplaner, mit RECHT_SOFORT).
- FUND in der Serverprobe: getOfflinePlayerIfCached war nicht beantwortet - jeder Mitbauer-Zweig flog. Ihre eigene Fehlermeldung sagt, was zu tun ist; die Antwort kommt jetzt aus der Tafel, die nenne() fuellt. Ein Name, den keine Probe genannt hat, ist NICHT bekannt - und das ist genau der Zweig "den Spieler kennt dieser Server nicht".
- DIE TRUHE WIRD SORTIERT GEFUELLT (TreeMap), und das ist kein Schmuck: Map.copyOf sagt keine Reihenfolge zu, und sie ist je JVM-Lauf eine andere. Ohne Sortierung laege das Eis heute auf Platz eins und morgen auf Platz sieben - "jede Insel ist dieselbe Insel" waere nach jedem Neustart ein wenig unwahrer.
- ZWEI BUCHUNGSGRUENDE, obwohl die Insel GRATIS ist: abbuchen(0) geht immer durch und schreibt trotzdem eine Buchung. Die Zeile mit 0 Mark ist die SPUR, wann die Insel genommen wurde - ohne sie fehlte der Vorgang im Kontobuch ganz. Derselbe Praezedenzfall wie bei der ersten Parzelle.
- EIN SATZ, DEN ES NIRGENDS SONST GIBT: wolke.aufbau-gestoert. In jeder anderen Welt macht der Weltgenerator das Gelaende; hier das Plugin. Faellt das Aufsetzen um (Welt nicht geladen), gehoert dem Spieler ein Feld aus LUFT - und er muss es erfahren, denn von selbst merkt er es erst, wenn er hinreist und faellt. Reparierbar ist es, weil Aufsetzen und Zuruecksetzen dieselbe Hand sind.
- DER START REPARIERT DIE ANKUNFTSPLATTFORM: Fehlt der Mittelblock auf Feld 0/0, setzt setzeAnkunftsplattform sie neu und klagt darueber. Gesetzt wird NUR, wenn er fehlt - sonst schriebe jeder Start 810 Bloecke in dieselbe Stelle, und ein Spieler, der dort steht, saehe seine Welt flackern.
- ZWEI BESCHEID-WORTE im Wolkenbuch (Bescheid fuer das Nehmen, Mitbescheid fuer die Mitbauer), und das ist eine Entscheidung gegen das naheliegende gemeinsame: Ein Wort fuer beides zwaenge jeden Verzweig, auch die Faelle des anderen zu behandeln - der Satz "die Welt ist voll" stuende in der Antwort auf "trag meinen Freund ein". So bleibt jeder Verzweig vollstaendig UND ohne default.
- MITBAUER AUF DER INSEL, AUF DER MAN STEHT (wie bei den Parzellen) und keine eigene Liste: Die Mitbauer stehen in der ZEILE ihrer Insel (wolke.zeile-mitbauer). Zwei Saetze und kein Platzhalter, der manchmal leer ist - ein "Mitbauer: " ohne Namen sieht aus wie ein Fehler.
- NEBENBEI BERICHTIGT: Art.java behauptete im Kopf "heute entsteht ausschliesslich PARZELLE" - das stand seit Schritt 5 falsch da, es sind vier Arten mit je eigenem Schreibweg. Und die Lagerattrappe sagte "alle zwoelf Methoden"; es waren siebzehn, jetzt achtzehn. Die Zahl steht nicht mehr im Satz.
- Proben 3692 -> 3812. Neu: WolkenbuchTest (22), WolkenaufbauTest (8), WolkenwacheTest (11), WolkeBefehlTest (19), Vertragsprobe +8 (zaehlt dreifach: 16 im Kern, 8 in daten). Nachgezogen: BildGleichTest (33 Bilder, alle aus dem ROTEN Lauf gelesen und nicht abgeschrieben), GrenzeTest 31b/31c/31u/31x/31al, VorlageTest 9, SprachstandTest 23 und 25, WolkenwerteTest, WolkenlisteTest.
- ~~Offen in Schritt 11: Teil C bis F.~~ 18.11. GEBAUT, alle vier.
- ~~Teil C: Erzgenerator.~~ Erzwache tauscht den gebildeten Block (BlockFormEvent, HIGH); Tabelle mit Gewicht und ab-stufe je Erz, Haeufigkeit steigt mit der Stufe bis zum Deckel. Getauscht nur AUF einer Insel; getauschtes Erz bekommt eine Blockmarke, also zahlt sein Abbau keinen Lohn.
- ~~Teil D: Herausforderungen und Stufe.~~ Zehn Aufgaben in der config.yml, Namen in der Sprachdatei (zweite Satzfamilie neben beruf.name). /wolke aufgaben, /wolke abgeben. Die STUFE ist die Zahl der erfuellten Zeilen - nie gespeichert.
- ~~Teil E: Punktzahl und Rangliste.~~ /wolke punkte zaehlt ein Feld in Happen (64 Chunks, ein Chunk je Takt), /wolke rangliste zeigt zehn Plaetze mit Punkten UND Stufe. Punkte entscheiden nichts - nur die Stufe wirkt auf die Erzausbeute.
- ~~Teil F: Biom und Zuruecksetzen.~~ /wolke biom kostet jedes Mal (auch auf dasselbe Biom), setzt die Biomzellen des Feldes in Happen um; /wolke zuruecksetzen stellt die Startinsel wieder her, mit Rueckfrage und ohne den Fortschritt zu loeschen.
- FUND, gemessen: INSERT IGNORE macht aus JEDEM Fehler eine Warnung - auch aus der Fremdschluesselverletzung 1452. Null geschriebene Zeilen hiessen damit entweder "stand schon drin" oder "diese Insel gibt es nicht". MariaWolken.erfuellen fragt im Null-Fall nach, ob die Zeile wirklich dasteht; ohne das haette eine verschwundene Insel lautlos "schon erfuellt" bekommen.
- FUND: Die Familienregel der Sprachsaetze stand als instanceof Berufsname an einer Stelle im Leser. Die zweite Familie (Aufgabenname) fehlte dort - das Wort landete nicht im Buch, und /wolke aufgaben lief in einen Wurf. Jetzt fragt der Leser Sprachschluessel.gehoertZuFamilie, und die Liste steht an EINER Stelle.
- Eine Chunkfolge fuer zwei Arbeiten: Zaehlung und Biomwechsel holen ihre Happen aus derselben Klasse. Beide fassen jeden Takt in try und melden ihren Zeitgeber ab - eine Ausnahme in einem getakteten Auftrag nimmt Bukkit nicht zurueck.
- Proben 3812 -> 4046 (core 1549, daten 313, paper 2184), 0 rot, 0 uebersprungen, jede Aufgabe mit --rerun erzwungen. Neu: Erzwerte/Erzregel/Erzwache, Aufgabenwerte/Aufgabenbuch/Aufgabenliste, Punktwerte/Punktebuch/Zaehlauftrag/Chunkfolge/Punkteliste/Inselzaehlung, Biombuch, ZwischenspeicherWolken, Tafeln 31ba/31bb/31bc. Nachgezogen: BildGleichTest (30 neue Bilder aus dem ROTEN Lauf), VorlageTest 10e, WolkenwerteTest, WolkenlisteTest, WolkeBefehlTest.
- ENTSCHIEDEN 18.11.2026 (Nachtrag zu Teil F): Das Biom zu WECHSELN kostet jedes Mal Geld. Nicht einmalig und nicht gratis - ein Wechsel setzt das Biom eines ganzen Feldes neu und ist damit die einzige Mechanik dieser Welt, die wiederholbar etwas umbaut. Der Preis ist eine Einstellung (wolken.biomwechsel) mit eigenem Sprungschluessel.
- MERKPOSTEN fuer SPAETER, ausdruecklich nicht jetzt (Nutzer am 18.11.2026): BESONDERE MOBS in Wolkenwerder - Giants, Minibosse. Gehoert nicht in Schritt 11; es ist eine eigene Mechanik mit eigenen Fragen (Erscheinungsregel, Beute, Belohnung, ob sie an die Insel-Stufe haengt). Beim naechsten Durchgang durch die offenen Punkte wieder vorlegen.
- ~~Offen beim Spieltest von Teil A: Der Quader des ACHTEN Tores ist noch nicht eingemessen.~~ 18.11. ERLEDIGT, aber anders als gedacht - siehe die Entscheidung unten.
- ENTSCHIEDEN 18.11.2026 (Nutzer, auf Rueckfrage): **Altfurt ist art: flach.** Die Weltart gibt es seit dem 09.11. genau dafuer, und der Probeserver faehrt Altfurt seitdem flach - nur die ausgelieferte config.yml blieb gewoehnlich mit startwert 110926001, mit der Begruendung, das Repo behalte seine Werte "fuer die spaetere gewoehnliche Welt". Diese eine Zeile war die ganze Ursache: Altfurt ist eine GEBAUTE Stadt, und jeder Quader darin ist eine Zahl in einer Datei.
- Damit faellt die Ortsfrage weg: Boden ueberall y -61, erster freier Block y -60. Alle ACHT Torquader stehen auf z=0 mit y -60..-58 (x 58/62/66/70/74/78/82/86, Wand x 57..89); die drei Ladeneinheiten auf Boden y -61, Truhe y -60, Quader bis -57. Die Umrechnung ist nicht gerechnet, sondern aus dem Probeserver ABGELESEN, der sie seit dem 09.11. traegt. Der Startwert ist WEG - auf einer Ebene waere er eine Zahl, die nie wirkt (Weltangabe.startwertWirkt).
- GEMESSEN im Startlauf mit der neuen Datei (frisches Jar, geloeschte Plugin-config, geloeschte Welt): "Welt freimarken:altfurt bereit (FLACH, NORMAL, neu angelegt)", "8 Tore in freimarken:altfurt." - und KEINE Torklage. Die drei schweren Zeilen des Laufs sind die fehlenden Standtruhen, die von Hand zu setzen sind. Gegengemessen aus der Weltdatei: x 55..92 bei z=0 durchgehend Boden y -61, grass_block, Spanne 0, trocken.
- Was die Umstellung ueberfluessig gemacht hat, bleibt als Wissen: Im Altfurt des Startwerts 110926001 sind 73 Prozent der Umgebung Meer; die beste Stelle fuer eine Torreihe LAG bei z=161 (Spanne 0), der billigste 33x26-PLATZ bei z=98 mit 1.128 Bloecken Planierarbeit, und am Nullpunkt zu bleiben haette 17.856 Bloecke Fuellung gekostet. Von acht geprueften Startwerten gab 110926012 den besten Platz (z=8, 231 Bloecke). Alles das ist jetzt ohne Bedeutung - es ist die Begruendung dafuer, gebaute Orte NICHT auf gewachsenes Gelaende zu setzen.
- MERKPOSTEN: Spaeter soll man der Ebene nicht ansehen, dass sie eine ist (Nutzer am 18.11.) - Stadtbild, Kulisse, Horizont. Nach aussen haelt die kante: 800 den Spieler; das Aussehen ist Bauarbeit und keine Einstellung.
- Noch zu tun am Bundesmarkt, und zwar von Hand im Spiel: die Wand x 57..89 auf z=0 mit acht Oeffnungen bauen, die drei Standtruhen setzen, den Weltspawn in die Halle legen. Der Start nennt jede fehlende Truhe mit Koordinate.
- GEMESSEN 18.11.2026 am achten Tor: Es ist nicht EIN Tor zu wenig, sondern die ganze Reihe. Acht Durchgaenge brauchen 1 + 8*4 = 33 Bloecke; das am 02.11. gemessene flache Feld war 32 breit. Und das heutige Altfurt (Startwert 110926001) hat die ganze Reihe UNTER WASSER - Boden y 50..53, Wasser in 45 von 46 Spalten. Der Start klagt ueber alle acht Tore, nicht ueber eines.
- Die Welt vom 02.11. gibt es nirgends mehr: Freimarken ist noch nie auf einem dauerhaften Server gelaufen. Jeder von Hand eingemessene Quader in Altfurt (Tore, Ladeneinheiten) haengt an einer Welt, die niemand hat.
- FUND am Plugin, behoben: Der Vorschlag im Startbericht suchte nur den ersten FESTEN Block unter der Fusshoehe, in der einen aufgefallenen Saeule. Auf einem Meeresboden riet er damit in die Wassersaeule (gemessen: "Passend waere: von [86, 52, 0] bis [88, 54, 0]") - und der naechste Start klagte "steht WATER im Tor". Ein Vorschlag, der in die naechste Klage fuehrt, sieht aus wie eine Antwort.
- Dazu verlegt: torklage und vorschlag lagen PRIVAT in FreimarkenPlugin und waren ohne laufenden Server zu keiner Probe zu bringen (GrenzeTest 31ab sagt das selbst). Jetzt stehen sie in Ankunftspunkt, wo der Weltgriff eine Attrappe aus drei Fragen ist. Die Hoehe gilt nur, wenn JEDE Saeule der Oeffnung sie traegt - dieselbe Regel (taugt), an der die Pruefung nachher misst. Und der Boden darf nicht schaden: Ein Tor auf einem Magmablock war vorher klaglos.
- Messwerkzeug skripte/torreihe_messen.py (liest Regionsdateien, OHNE Server): Flaechensuche je z-Linie mit Guete (Spanne, Hindernisse, Naehe zum Ursprung), Bestenliste, fertige tor:-Zeilen und Baubefehle. Eine Spalte taugt nur trocken; Eis und Wasser bis drei Bloecke ueber dem Boden zaehlen als nass.
- GEMESSEN im heutigen Altfurt (1.024 Chunks um den Ursprung erzeugt, x/z -200..200): 297 von 401 Linien tragen ueberhaupt ein 33er Fenster. BESTE STELLE z=161, x 7..39, Boden y 79, Spanne 0, nur Grasbueschel - eine Wand ohne Planierung. Auf der alten Linie z=0 liegt das naechste trockene Fenster bei x 140..172, Spanne 5.
- Der Ankunftsort haengt NICHT an der config.yml: Ankunftswache nimmt ankunftswelt.getSpawnLocation(). Die Reihe darf also ueberall stehen, der Weltspawn wandert mit (setworldspawn).
- Proben 4046 -> 4054 (core 1549, daten 313, paper 2192), 0 rot, 0 uebersprungen. Neu: acht Torproben in AnkunftspunktTest (gebaute Wand vom 02.11., Tor in der Luft, Meeresboden, Pfuetze unter einer von drei Saeulen, zugemauert, Magma, und eine, die jede der neun Stellen einer Oeffnung einzeln kaputtmacht). Nachgezogen: WeltenlisteTest um die Reihentafel (eine Wand, ein z, EINE Hoehe, Tuer 3x3, Schritt 4, kein geteilter Block) - die Hoehe stand vorher nur im Kommentar.
- ~~Offen, NICHT neu und nicht Teil dieses Schrittes: Vier Weltnamen-Schluessel ohne Neulade-Riegel.~~ 20.11. GEBAUT, siehe Nachlese zu Schritt 12. Die vier Pfadkonstanten liegen in den LESERN und nicht im Kern; darum fragen fehltText und die Sammelzeile jetzt eine gemeinsame Frage in Wirtschaftsliste. Vorher: Fehlt so eine Zeile, schreibt fehltText dazu "es gilt wolkenwerder" - eine Geltung, die ein Neuladen nicht herstellt. Die Pfadkonstanten liegen in den Lesern (paper) und nicht im Kern; der Riegel braucht alle vier zusammen.

## Schritt 10: Hafenwerder

- ENTSCHIEDEN 18.11.2026 (Chat schlaegt Plan, drei Fragen): **gebackene Inseln auf gerasteter Welt**, **Hoechstzahl je Rang** wie bei den Parzellen, und beim Angelgut **beides** - eine eigene Fangtabelle UND ein Bonus fuer den Fischer.
- Der Plan widersprach sich an genau der Stelle, die am 11.11. schon einmal entschieden wurde: 4.1 sagt "Gelaende: natuerlich gewachsen", 6.2 einen FESTPREIS von 75.000 Mark. Beides zusammen geht nicht - die eine Insel laege im Flachwasser, die naechste halb im Berg. Jede Insel ist darum DIESELBE Insel, nur anderswo.
- Das FELD ist das Eigentum und nicht die Insel: 128x128 Bloecke, darin die Insel mit neun Bloecken eigenem Wasser rundherum - Hafen, Bucht, Platz fuer einen Steg. Nur so bleibt die Zugehoerigkeit eine Punktabfrage auf (welt, x, z); eine Kuestenlinie als Eigentumsgrenze waere bei jedem Block eine Formel und bei jedem aufgeschuetteten Block eine andere.
- KEINE BARRIERE, und das ist der eine Unterschied zu den Stadtfeldern: Dort trennt Leere, und Leere traegt nur, solange niemand hineinfaellt. Hier trennt Wasser. Hinueberschwimmen soll man duerfen (Plan 4.1 nennt Bootsrennen und Handelsrouten), nur bauen nicht - das verbietet die Schutzwache und nicht die Welt.
- BAUPLAN in vier Teilen:
  - A: DIE WELT. Rasterrechnung, Inselplan, Weltgrenze, Generator, neue Weltart.
  - B: DIE INSEL NEHMEN. Dritte art werder in parzellen, ein drittes Buch, Festpreis 75.000, Hoechstzahl je Rang ueber Rechte, /werder hafen nehmen.
  - C: DER SCHUTZ. Eine Wache in der Kette, Mitbauer und Gaeste wie bei der Parzelle.
  - D: DAS ANGELGUT. Eigene Fangtabelle in der config.yml am PlayerFishEvent, dazu der Weltbonus fuer den Fischer.
- ~~Teil A: die Inselwelt.~~ 18.11. GEBAUT. Inselplan im Kern (INSELFELD gegen OFFENES_MEER fuer das Eigentum, LAND/STRAND/WASSER fuer das Gelaende), Inselgrenze als Geschwister der Feldgrenze, InselnGenerator und Inselboden im Paper-Modul, Art inseln, Abschnitt inseln: mit welt.
- EINE GEMEINSAME RASTERRECHNUNG statt einer Abschrift: Feldrechnung im Kern haelt Feldkante und Zwischenraum und beantwortet Feldnummer, Anfang, Mitte, Mindestkante, Weltkante und Weltmitte. Stadtfeldplan und Feldgrenze rechnen jetzt darueber - ihre oeffentliche Form ist Wort fuer Wort dieselbe, und dass die Freistaedteproben gruen bleiben, IST der Beweis, dass die Auslagerung nichts geaendert hat. Zwei Zeilen durften nicht zweimal dastehen: feldMitte nimmt bei gerader Kante den KLEINEREN der beiden mittleren Bloecke, und die Weltgrenze rundet auf eine GERADE Feldzahl ab - an beiden haengt, wo ein Warp den Spieler absetzt.
- DIE GESTALT DER INSEL haengt an EINER Zahl, dem Kuestenradius 56: Es bleiben acht Bloecke bis zum Feldrand, und weil der Hang um einen Block faellt, sooft er einen Block nach aussen geht, ist der Meeresgrund genau dort - acht Bloecke tief. Darum ist das Wasser am Feldrand genauso tief wie im offenen Meer: keine Stufe, keine Riffwand, kein Fuss im fremden Wasser. meerTiefe ist abgeleitet und keine zweite Zahl.
- Gemessen und nicht geschaetzt (die Texte nennen jetzt die gemessenen Zahlen): 110 Bloecke an der breitesten Stelle, 11.364 Bloecke Land, davon 1.600 Strand. Die Kuestenlinie ist |dx|^4 + |dz|^4 <= r^4, also ein gerundetes Quadrat; ein Kreis gaebe 9.660 Bloecke, die achte Potenz 11.916 und waere von einem Quadrat kaum zu unterscheiden.
- Gemessen in HALBEN Bloecken vom wahren Feldmittelpunkt: Bei gerader Feldkante gibt es keinen Mittelblock. Ganze Bloecke von Block 63 aus gezaehlt machten die Insel um einen Block unsymmetrisch und ihren Fuss einen Block lang in fremdem Wasser.
- ANDERS ALS BEI DEN STADTFELDERN BLEIBEN BEWUCHS UND TIERE AN: Ein Stadtfeld soll sauberes Bauland sein, eine Insel das Gegenteil. Die Tiere sind dabei kein Beiwerk - friedliche Tiere entstehen beim ERZEUGEN eines Chunks und spaeter so gut wie nie; ohne diesen Durchgang blieben die Inseln fuer immer ohne Kuehe. Bauwerke bleiben aus: ein Ozeanmonument auf einer bezahlten Insel bestellt niemand. Das Meer ist das WARME Biom - mit ihm kommen Seegras, Korallen und tropische Fische, und darauf baut Teil D auf.
- ACHTUNG BEIM ERSTEN START: welten.hafenwerder stand bis heute auf gewoehnlich. Was in einer schon erzeugten Welt im Boden liegt, bleibt liegen - nur NEUE Chunks folgen der Zeile, und das waere eine Welt aus zwei Haelften. Der vorhandene Ausweis in der level.dat sagt es als Klage ("wurde als GEWOEHNLICH angelegt, die Liste sagt INSELN"); wer die Art wirklich wechselt, loescht den Weltordner einmal. Der Startwert 110926003 ist weg - er waere eine Zahl, die nie wirkt.
- Aus 5000 werden 18 Felder je Achse (-9 bis 8) und die wirkliche Kante 4736 um -64/-64: 324 Felder, davon 323 zu nehmen. Feld 0/0 ist die ANKUNFTSINSEL, dort landet das Tor aus Altfurt. Eine Grenze von 5000 um 0/0 schnitte Feld -10 mitten durch - dieselbe Falle wie bei den Freistaedten am 11.11.
- Zwei Stellen, an denen die Art zu erweitern war, sind jetzt Verzweige UEBER ALLE ARTEN und keine Ketten von Ungleichheiten mehr: startwertWirkt (ohne default - eine neue Art uebersetzt sich sonst nicht) und das Rastermass im Weltenbau. Letzteres stand dreimal in setzeGrenze (Mindestmass, Rueckfall, Fenster); bei der zweiten Art waeren das drei Stellen gewesen, die sich einzeln vergessen lassen - und eine davon vergessen heisst, die Grenze laeuft durch bezahltes Eigentum.
- Proben 3437 -> 3484. Neu: FeldrechnungTest (12), InselplanTest (14), InselgrenzeTest (10), InselbodenTest (6), WeltenlisteTest +2 (beide Richtungen der Bindung art: inseln an inseln.welt), Tafeln 31av (die vier Spannen des Generators als TEXT - alle vier rechnen mit demselben Gipfel) und 31aw (jede Weltart hat ihr Wort in Weltenliste.art UND in der Klage, die Liste aus der Aufzaehlung gezogen und nicht abgeschrieben).
- Die Probe hat MICH berichtigt: feldBei(-1,-1) ist OFFENES_MEER und nicht Inselfeld. -1 ist der letzte Block des MEERES von Feld -1; das Feld selbst endet bei -129. Der Zwischenraum gehoert zum Feld davor, auch im Negativen.
- ~~Teil B und C: die Insel nehmen und schuetzen.~~ 18.11. GEBAUT, und ZUSAMMEN - eine gebaute, gruen gepruefte Schutzregel ohne Verdrahtung ist unsichtbar wirkungslos. /werder, /werder liste, /werder hafen, /werder hafen nehmen; Inselbuch als DRITTES Buch neben Besitzbuch und Freistadtbuch; Inselwache als dritte Ortsregel in der vorhandenen Schutzwache.
- DAS FELD IST DAS EIGENTUM: dritte art werder in parzellen (die ENUM-Spalte kannte den Wert schon, kein Schemaschritt), dritter Schreibweg werderAnlegen in der Schnittstelle und allen vier Umsetzungen. Eine Insel hat KEINEN Namen, KEINE Kasse, KEINE Regeln und KEINEN Besuchstag - die sieben Riegel in Parzelle fragen "ist Freistadt", und eine Insel ist die erste Art, die weder das eine noch das andere ist. Sieben Vertragsproben halten das fest, je dreimal.
- GENOMMEN WIRD, WORAUF MAN STEHT - wie bei der Parzelle und anders als bei der Freistadt. Eine Zuteilung durch das Buch braucht es nur, wo man nicht hingehen kann; nach Hafenwerder faehrt man mit dem Boot, und genau das soll der Reiz sein.
- DIE HOECHSTZAHL KOMMT AUS DEM RECHT (Wunsch des Nutzers): freimarken.inseln.<zahl> und freimarken.extra.inseln.<zahl> ueber die Vorgabe inseln.hoechstzahl. Die Auswertung stand in ParzellenBefehle und gilt jetzt fuer DREI Grenzen - sie ist nach Rangzahl umgezogen, ParzellenBefehleTest wurde RangzahlTest. Zwei Abschriften waeren zwei Orte, an denen der Zuschlag vergessen wird.
- FUND BEIM BAUEN, von meiner eigenen Probe: Parzelle.darfBauen(null) WARF - Set.of(...).contains(null) ist eine NullPointerException, auch bei einer LEEREN Mitbauerliste. Genau so fragt aber eine Wache, die einen Handbalken begruendet, und genau so kommen Ereignisse ohne Spieler herein. Die Inselwache haette daraus eine Klage ueber eine stumme Datenbank gemacht und jedes Bauen abgelehnt - mit einem Grund, der nicht stimmt. Jetzt heisst kein Spieler NEIN, an der einen Stelle fuer alle drei Buecher.
- FUND DER TAFEL 10h: Werdertext holte werder.hier.frei und werder.genommen mit eineZeile, und BEIDE Saetze haben zwei Zeilen - eineZeile WIRFT dann. Im Spiel haette der Zweig gar nichts gesendet. Keine Verhaltensprobe hat es gesehen; die Tafel, die Satz und Holstelle vergleicht, schon. Jetzt geht jede Methode des Werdertext ueber Listen, damit nicht die Gestalt eines Satzes entscheidet, welche Methode ihn holt.
- FUND IN DER SPIELERPROBE: Sie baute PermissionAttachmentInfo mit null als Traeger, und Bukkit wirft inzwischen "Permissible may not be null". Also warf JEDER Zweig, der die Rechte durchsucht - aufgefallen ist es erst jetzt, und daran sieht man, was fehlte: Die Hoechstzahl aus einem Recht hatte in keinem getippten Befehl eine Probe. Jetzt ist der Traeger der Spieler selbst, und WerderBefehlTest tippt beide Vorsilben durch.
- Proben 3530 -> 3581 (und 3484 -> 3530 davor durch die Vertragsproben und 25 Bildzeilen). Neu: InselbuchTest (19), InselwerteTest (6), InselwacheTest (11), WerderBefehlTest (14), BesitzLagerVertrag +7 (je dreimal), ParzelleTest +1. Nachgezogen: 31b/31c (eine Sendestelle in WerderBefehle), 31u und 31x (fuenf Schutzbalken, ein Befehlswort), 10e/10i, Probe 9 (siebzehnter Bereich), Probe 23/25 (fuenfzehnter Befehl).
- ~~Teil D: das Angelgut.~~ 18.11. GEBAUT, und zwar BEIDES, wie der Nutzer entschieden hat: eine eigene Fangtabelle UND ein Zuschlag fuer den Fischer.
- DIE ERSTE BEUTETABELLE DIESES PROJEKTS. Bis heute gab es nirgends eine Stelle, die bestimmt, WAS man faengt - das Angelereignis wurde nur gelesen, um den Fang zu bezahlen. Neu: Angelwerte und Fangregel im Kern, Angelliste als Leser, Angelwache als Hand am Ereignis.
- SIE ERSETZT DEN FANG NUR MANCHMAL (haeufigkeit-prozent, Vorgabe 20). Eine Tabelle, die IMMER greift, waere keine Besonderheit, sondern eine andere Welt - und die Fische aus der Lohntafel wuerden selten, also verdiente der Fischer in Hafenwerder WENIGER als ueberall sonst. Das ist die Umkehrung von Plan 9.1.
- NEUN FAENGE AB WERK, und sie sind nicht beliebig gewaehlt: Hafenwerder hat keine Hoehlen und keine Bauwerke, also kein Erz, kein Prismarin, keinen Schwamm. Was in der Tabelle steht, ist darum der einzige Weg zu diesen Dingen. Das MEERESHERZ mit Gewicht 1 ist der Reiz: Mit ihm und acht Schneckenhaeusern wird ein Aquisator gebaut, und durch Angeln gibt es es in Vanilla nirgends.
- DER TAUSCH SITZT VOR DER ZAHLUNG (Angelwache auf HIGH, Verdienstwache auf MONITOR): Die Lohntafel bezahlt den GETAUSCHTEN Fang. Danach waere die ganze Tabelle fuer den Verdienst unsichtbar, und Lohnzeilen zu den neuen Faengen waeren Zeilen, die nie greifen. Keine Probe kann das messen - dazu braeuchte sie den Ereignisbus. Tafel 31ax liest beide Stufen als WORT und prueft dazu die Anmeldung.
- DIE REIHENFOLGE DER TABELLE IST SORTIERT, und das ist die wichtigste Zeile der Fangregel: Map.copyOf sagt keine Reihenfolge zu, und sie ist je JVM-Lauf eine ANDERE. Ohne Sortierung gaebe derselbe Wurf heute ein Schneckenhaus und morgen ein Meeresherz - und jede Probe waere mal gruen und mal rot, ohne dass sich etwas geaendert hat.
- GEMESSEN UND ZUR NAHT GEMACHT: new ItemStack(art, 1) WIRFT ohne laufenden Server (die Gegenstandsregistrierung). Mit ihm mitten im Zweig liesse sich der Tausch - die ganze Sache der Klasse - von keiner Probe durchlaufen. Der Stapelbau kommt jetzt als Naht herein, wie der Zeitgeber des Verdienstbuchs; die Wesenprobe hat dafuer einen tauschbaren Fang bekommen.
- DER INSELBONUS (Plan 9.1): verdienst.inselbonus.<beruf>, ab Werk fischer: 50. Die WELT steht nicht im Schluessel - gemeint ist die aus inseln.welt. Ein Bonus je Welt waere eine vierte Ebene im Lohnschluessel und eine Einstellung, die fuer sechs Welten dasteht, in denen niemand sie braucht.
- DER ZUSCHLAG LIEGT VOR DER MINDERUNG: 60 Pfennige, gemindert auf die Haelfte, sind 30. Danach gerechnet waeren es 40 - und dann waere der Zuschlag am Ende einer Stunde der GROESSERE Teil des Lohns, und die Bremse gegen den Klickautomaten waere in Hafenwerder die schwaechste von allen.
- UND ER HAENGT AM BERUF, nicht am hoechsten Satz: hoechsterLohn sagt nicht, WELCHER Beruf den besten Satz traegt - und mit dem Zuschlag kann ein schlechterer Satz der beste werden. Die Regel rechnet darum jeden gehaltenen Beruf einmal mit SEINEM Zuschlag. Ein Zuschlag auf einen Beruf ohne Satz erfindet nichts.
- Die Wache haelt die Inselwelt als FELD mit Namen inselschluessel - damit Tafel 31ae sie findet. Jene suchte woertlich "private final String weltschluessel" und war damit blind fuer jede andere Schreibweise; sie liest jetzt ein MUSTER auf *schluessel. Dieselbe Tafel kannte die FABRIKFORM nicht (Angelwache.ueberBukkit) und hielt die Wache fuer ungebaut.
- FUND 31ak, zum achten Mal dieselbe Falle: Mein Block stand ZWISCHEN dem Dokublock der VORGABE und ihrer Deklaration. Und ein zweiter: Der Abschnitt inselbonus landete unter freistaedte: statt unter verdienst:, weil mein Anker eine Kommentarzeile kurz vor laeden: war - gefunden von der Probe, die jede GELESENE Zeile in der ausgelieferten Datei verlangt.
- FUND in der eigenen Probe: "an: ja" ist in YAML KEIN Wahrheitswert (es kennt true/false/yes/no/on/off). Die ausgelieferte Datei haette beim Start eine Klage gegeben und die Tabelle auf die Vorgabe zurueckgesetzt.
- Proben 3581 -> 3630. Neu: FangregelTest (9), AngelwerteTest (6), AngellisteTest (10), AngelwacheTest (11), VerdienstregelTest +4, VerdienstwerteTest +3, VerdienstlisteTest +4, VerdienstbuchTest +1, Tafel 31ax. Die 75 Aufrufstellen von pfennige und verdiene tragen jetzt FESTLAND statt eines nackten false - ein false an 75 Stellen waere 75-mal eine Zahl ohne Bedeutung.
- Offen in Schritt 10: nur noch die Spieltests.
- Offen beim Spieltest von Teil D: in Hafenwerder angeln, bis ein Fang aus der Tabelle kommt (jeder fuenfte), dabei den Handbalken des Fischers lesen (mit Zuschlag sind es 50 Prozent mehr); dasselbe in einem Ein-Block-Loch versuchen (nichts); in Altfurt angeln (Vanilla und kein Zuschlag); angeln.haeufigkeit-prozent auf 100 setzen, neuladen, und den Bericht lesen; einen Fangnamen verschreiben und die Klage im Startbericht finden.
- Offen beim Spieltest von Teil A bis C: Weltordner hafenwerder einmal loeschen (die Art hat gewechselt), dann den Startbericht lesen ("Meerinseln: Welt ..., Felder -9..8, bis zu 323 Inseln zu 75000 Mark"), mit /warp hafenwerder auf der Ankunftsinsel landen, ins Wasser springen und den Handbalken "Offenes Meer" lesen, zu einer Nachbarinsel schwimmen, /werder hafen, /werder hafen nehmen, einen Block setzen, einen Fremden dasselbe versuchen lassen, /werder, und zuletzt inseln.hoechstzahl auf 0 setzen und neuladen.

## Schritt 9: Freistaedte

- ENTSCHIEDEN 11.11.2026 (Chat schlaegt Plan): festes 256er-Raster auf einer FLACHEN Welt, wie die Marktstadt ihr Parzellenraster hat. Die Staedte sind durch LEERE getrennt - man kann nicht von einer zur anderen laufen -, und jede hat ihren eigenen Warp: /warp freistadt <name>. Plan 6.4 nachgetragen.
- Der Plan sagte "natuerliches Gelaende". Dagegen sprach: Jede Stadt waere eine andere (die eine im Tal, die naechste halb im Berg) - und 400.000 Mark koennen nicht fuer beide derselbe Preis sein.
- Das Recht wie bei laden.mieten: freimarken.freistadt.gruenden, Vorgabe fuer alle. Schritt 12 haengt spaeter den Rang Buerger daran, keine zweite Pruefung daneben.
- BAUPLAN in fuenf Teilen:
  - A: DIE WELT. Generator fuer das Stadtfeldraster (Geschwister des MarktstadtGenerator, Rechnung im Kern wie Rasterplan), neue Weltart, Startbericht. Dazu das ANKUNFTSFELD: Das Tor in Altfurt braucht ein Ziel, und "irgendwo in der Leere" ist keins.
  - B: GRUENDEN. /freistadt gruenden <name>, 400.000 Mark, ein freies Feld wird zugeteilt, Besitz der Art freistadt, Warp /warp freistadt <name>.
  - C: GRUNDSTUECKE. Der Besitzer teilt sein Feld und setzt Preise (kein Preisdeckel, Plan 16). Ein anderer kauft - DAS ist das Abnahmekriterium aus Plan 15: "ein Spieler verkauft einem anderen ein Grundstueck". In DREI Teilen, und der erste ist nicht das Verkaufen: C1 Stadtschutz, C2 Grundstuecke zuschneiden, C3 Verkauf.
- BEFUND 11.11. vor dem Bauen: In der Welt freistaedte griff GAR KEINE Wache. darfHandeln stieg bei fremder Welt aus, keine der 17 Zuhoererklassen las den Stadtfeldplan, die Welt laeuft in SURVIVAL. Jeder konnte in jedem Feld jeder fremden Stadt bauen - und das Abnahmekriterium haette gar keinen Unterschied machen koennen, weil der Kaeufer dort vorher schon durfte.
- Dazu: Die Zusage im Javadoc von Stadtfeldplan ("bruecken kann niemand, weil ausserhalb eines Feldes das Bauen verboten ist") trug die Begruendung fuer die 32 Bloecke Leere - und stand auf einem Verbot, das es nicht gab. Die Barriere ist vier Bloecke hoch; wer auf ihre Oberkante steigt, baut von dort weiter.
- ~~Teil C1: DER STADTSCHUTZ.~~ 11.11. GEBAUT: Freistadtbuch.darfBauen mit SECHS Urteilen (erlaubt, Leere, ausserhalb, Ankunftsfeld, freies Feld, fremde Stadt), Stadtwache als zweite Schutzwache.Ortsregel, sechs Handbalken.
- Sechs Urteile und nicht zwei, weil der Spieler lesen soll WARUM: In der Leere ist etwas anderes verboten als auf fremdem Feld, und auf einem freien Feld steht ein Weg offen, den er sonst nicht findet. Zwei Saetze tragen KEINEN Ausweg (Leere, Ankunftsfeld) - dort wird nie jemand bauen duerfen, und ein erfundener Ausweg waere schlimmer als keiner.
- Die Leere ist fuer JEDEN verboten, auch fuer den Besitzer des Feldes nebenan: Ein Block dort ist der erste Block einer Bruecke. Erst damit stimmt, was Stadtfeldplan ueber die 32 Bloecke sagt.
- Eine ORTSREGEL und keine zweite Wache - deren eigene Doku warnt davor: Die 15 Bukkit-Zugaenge muessten sonst ein zweites Mal dastehen, und einer waere irgendwann anders. Die Schnittstelle bekam dafuer zwei Fabriken (nacheinander, spaet) und eine neue Frage zustaendig(ort) OHNE Spieler - der Handbalken muss die zustaendige Regel wiederfinden, und dort gibt es keinen Spieler mehr.
- spaet(() -> stadtwache) loest denselben Zirkel wie das spaet gelesene Feld beim Shopklick: Die Schutzwache entsteht 80 Zeilen vor dem Freistadtbuch.
- Leere, Weltgrenze und Ankunftsfeld fragen die Ablage GAR NICHT - gezaehlt mit einer Attrappe, nicht behauptet. Das sind die haeufigsten Lagen am Feldrand, und die Regel laeuft bei JEDEM gesetzten Block.
- FUND aus Teil B, gefunden von der ersten Probe, die Mitbauer auf einer FREISTADT setzt: mitbauerSetzen WIRFT dort. SpeicherLager baute die Zeile ueber den verkuerzten Konstruktor neu, der KEIN_NAME setzt - und eine Freistadt ohne Namen ist verboten. Ein Stadtbesitzer konnte keinen einzigen Mitbauer eintragen. Der Kommentar daneben warnt woertlich vor der Falle ("Durchtragen, nicht neu setzen"); beim zwoelften Feld wurde sie uebersehen. Probe im VERTRAG, laeuft also dreifach - MariaLager traf es nicht.
- NEUE TAFEL GrenzeTest 31aq, weil derselbe Fehler zum zweiten Mal kam: Die Ladeneinheiten waren in Teil A fertige Geometrie ohne Anschluss, die Stadtfelder jetzt wieder. Beide Male war die Regel richtig gebaut und nur nicht angeschlossen - kein Probenlauf meldet so etwas. Die Tafel holt die Liste aus dem Quelltext (implements Schutzwache.Ortsregel) und prueft BEIDE Richtungen: gebaut und an die Wache uebergeben. Beim ersten Lauf sofort rot.
- Tafel 31ae hatte recht: Der Weltschluessel kam direkt von der Welt, waehrend alle anderen Freistadt-Verbraucher ihn ueber weltschluessel(...) bekommen. Zwei Formen im selben Bereich - genau die Naht, an der Teil B schon einmal auseinanderlief.
- Einspritzung 12 Faelle, 11 gefangen. Der zwoelfte war KEIN Fund, sondern ein schlechter Fall: null gegen eine zufaellige Kennung zu tauschen ist verhaltensneutral, beides ergibt FREMDE_STADT. Dahinter lag aber eine echte Luecke - Schutztext.stadtBalken wurde von keiner Probe gerufen, die beiden WERFENDEN Zweige von gar nichts. Jetzt SendewegTest 33ap, gegen den roten Lauf gehalten.
- Proben 2942 -> 2969. Neu: StadtwacheTest (12), FreistadtbuchTest +4, Vertragsprobe x3, GrenzeTest 31aq, SendewegTest 33ap, sechs Bildzeilen. Lagerattrappe in testFixtures, weil zwei Module sie brauchen.
- ~~Teil C2: GRUNDSTUECKE ZUSCHNEIDEN.~~ 12.11. GEBAUT: /freistadt grundstueck [ecke|anlegen|aufloesen], freie Quader aus ganzen 16x16-Zellen, eigene Tabelle, vollstaendiger Zwischenspeicher.
- ENTSCHIEDEN (Nutzer): FREIE QUADER mit zwei Ecken, und 16x16 als Rasterung der Ecken. Ein Grundstueck ist ein beliebiges Rechteck aus ganzen Zellen - und liegt damit nie halb in einem Chunk.
- Dass das aufgeht, ist kein Glueck: feldAnfang(n) = n*288, und 288 = 16*18. Jedes Feld beginnt auf einer Chunkgrenze und besteht aus genau 16x16 = 256 Zellen. Geprueft, nicht angenommen - zellenGehenAuf() fragt beide Masse.
- Der Riegel dafuer sitzt am ENGSTEN VERBRAUCHER: Erst stand er im Konstruktor des Stadtfeldplans und brach zwei Proben, die die Barriere bei kleinster Leere messen. Die hatten recht - ein Plan mit Rasterweite 258 ist fuer die WELT gueltig, er kann nur keine Grundstuecke tragen. Jetzt wirft das Grundstuecksbuch, die einzige Stelle, die in Zellen rechnet.
- EIGENE TABELLE, gegen Plan 6.1 - und der Widerspruch steht im Schemaschritt 20 selbst. Die FREISTADT bleibt in parzellen; nur die Grundstuecke ziehen aus, weil Parzelle.x/z primitive int sind (MariaLager.lies wirft bei NULL), uq ort (welt,x,z) je Zelle eine Zeile laesst, mitbauerSetzen vom Elternteil auf alle Kinder durchschreibt, alle Elternteil-Wege auf art='gruppe' festgenagelt sind und ON DELETE RESTRICT eine Stadt mit verkauften Grundstuecken unaufloesbar machte.
- Der GRUND fuer Plan 6.1 bleibt gewahrt: Es ging um den heissen Pfad. Darum rechnet die Flaeche in ZELLEN und nicht in Bloecken, und darum liegt davor ein vollstaendiger Zwischenspeicher ohne Verfall - die Frage "wem gehoert dieser Block" fasst die Datenbank nie an.
- Die Karte haelt JE ZELLE, nicht je Grundstueck: Ein Durchlauf ueber alle Quader waere bei voller Welt ein Vergleich gegen hunderttausend Rechtecke. Die Obergrenze ist eine RECHNUNG und keine Schaetzung - 399 Felder zu 256 Zellen, also hoechstens 102.144 Eintraege, und das folgt aus der Weltgeometrie.
- Der Zwischenspeicher deckt EINE Welt und reicht fremde ungepuffert durch. Dadurch laeuft derselbe Vertrag ueber ihn wie ueber Speicher- und Datenbankfassung - sonst haette er ausgenommen werden muessen, und das ist genau die Schwaeche, gegen die ein Vertrag gebaut ist.
- DAS GRUNDSTUECK STICHT DIE STADT, auch beim Stadtbesitzer. Ohne diese Zeile waere ein verkauftes Grundstueck eine Datenbankzeile ohne Wirkung. Zwei neue Urteile (ERLAUBT_GRUNDSTUECK, FREMDES_GRUNDSTUECK) - die Wache zaehlt beide Erlaubnisse gleich, aber das Buch unterscheidet sie fuer Teil C3.
- GEZAEHLT, nicht behauptet: Leere, Weltgrenze und Ankunftsfeld fragen die Grundstuecke GAR NICHT (0 Gaenge), ein Stadtfeld genau einmal.
- Die Ecken kommen per BEFEHL an der eigenen Stelle, kein Werkzeug: Ein Klickwerkzeug braeuchte ein eigenes Bukkit-Ereignis und einen Gegenstand, den jemand verlieren kann. Die dritte Ecke beginnt NEU und sagt es - nicht abwechselnd, denn dort muss man wissen, welche Ecke als naechste ersetzt wird.
- Die Groesse steht schon bei der ZWEITEN Ecke da: Wer sieht, dass sein Zuschnitt zu gross ist, legt ihn nicht erst an und liest dann die Absage.
- Nur der STADTBESITZER schneidet zu (ein Mitbauer der Stadt nicht), und nur der GRUNDSTUECKSBESITZER loest auf (der Stadtbesitzer nicht). Sonst koennte er ein verkauftes Grundstueck einziehen, und der Kaeufer haette fuer nichts gezahlt.
- freistaedte.hoechstzellen: 256 in der config.yml, also ab Werk OHNE Grenze - die Entscheidung fuer die Preisfreiheit aus Plan 16. Eine 0 heisst ebenfalls ohne Grenze, wie beim Tagesdeckel. Neuladbar.
- 518 ROTE PROBEN AUS 13 BEFUNDEN: vier Saetze brauchten Art.GEZAEHLT, einer trug ein verbotenes kaufmaennisches Und. Die Meldung sagt selbst, dass der Rest Folgen sind - "Dieser Abbruch reisst JEDE Probe mit, die die Datei laedt."
- Die Bildtafel verlangt bei einem gezaehlten Satz 1, 2 UND 0. Die Null kommt im Spiel nicht vor (ein Zuschnitt hat mindestens eine Zelle), aber ohne sie bliebe die Mehrzahlform dafuer ungeprueft.
- FUND an der eigenen Tafel: GrenzeTest 31ak hatte ZWEI Luecken, aufgedeckt vom eigenen abgerissenen Javadoc in Freistadttext. Ein EINZEILIGES Javadoc endet nicht mit einem */ auf eigener Zeile und wurde gar nicht betrachtet; ein Zeilenkommentar dazwischen galt als Code. Beide geschlossen, gegen den roten Lauf gehalten.
- Weitere Funde beim Bauen: leereAlles haette JEDE Datenbankprobe gebrochen (fk_grundstueck_stadt auf RESTRICT, und parzellen wird dort geloescht); die @BeforeEach-Reihenfolge (Oberklasse zuerst - mein Leeren in der Unterklasse loeschte die gerade angelegten Staedte, alle elf Datenproben rot); ein Widerspruch zwischen meinem Javadoc und meinem Code (die Mitbauer sollten in EINER Abfrage kommen, der Code holte sie je Zeile - MITBAUER_LESEN lag ungenutzt da).
- Blockecke lag zweimal da (in beiden Buechern) - jetzt ein eigener Record im Paket. Die Absteckung liefert sie, das Grundstuecksbuch nimmt sie; dass es DIESELBE ist, ist die ganze Naht.
- stadtAnlegen im Vertrag ist eine FABRIK und keine Konstante: Die Speicherfassung gibt eine Zahl, MariaDB legt eine echte Freistadt an. Sonst waere der Fremdschluessel, die staerkste Zusage dieser Tabelle, von keiner Probe beruehrt.
- Proben 2969 -> 3089. Neu: ZellquaderTest (10), GrundstuecksbuchTest (19), GrundstuecksablageVertrag (11) x3 Umsetzungen, ZwischenspeicherGrundstueckeTest (8), StadtfeldplanTest +5, FreistadtbuchTest +4, FreistadtBefehlTest +12, 21 Bildzeilen. Einspritzung 20 Faelle, 20/20 gefangen.
- ~~Teil C3: VERKAUF.~~ 12.11. GEBAUT - DAS ABNAHMEKRITERIUM AUS PLAN 15 STEHT: /freistadt grundstueck [anbieten <preis>|zurueckziehen|kaufen], /freistadt angebote. Ein Spieler verkauft einem anderen ein Grundstueck, und das Geld landet nicht beim Bund.
- Die REIHENFOLGE im Kauf ist nach dem SCHLIMMSTEN Fall gewaehlt: Konto des Verkaeufers eroeffnen, abbuchen beim Kaeufer, Besitzerwechsel, gutschreiben, Gebuehr. Scheitert das Gutschreiben, GILT der Kauf trotzdem und der Betreiber bekommt eine SEVERE-Zeile mit beiden Namen und dem Betrag. Ein Rueckweg muesste Wechsel UND Kaeufergeld aufheben - scheitert dabei das Zuruecknehmen beim Verkaeufer, haben beide Geld und niemand das Grundstueck. Ein Verlust ist heilbar, eine Geldschoepfung nicht.
- Der Riegel gegen den DOPPELKAUF liegt in der Ablage und nicht im Buch: UPDATE ... WHERE id = ? AND angebotspreis IS NOT NULL, und in der Speicherfassung derselbe Wurf im selben synchronized. Eine Pruefung im Buch haette zwischen Lesen und Schreiben eine Luecke.
- DREI PAARE VON SAETZEN, jedes zweite fuer ein GESCHENK (Preis 0). Eine Entscheidung ueber die Sprache und keine Doppelung: "zu 0 Mark angeboten" liest sich wie ein Fehler, und ein Stadtbesitzer, der Buerger anwirbt, verschenkt die ersten Grundstuecke. VIER Buchungsgruende, weil derselbe Betrag beim Kaeufer eine Ausgabe und beim Verkaeufer eine Einnahme ist.
- Die Marktgebuehr trifft den, der das Geld BEKOMMT - wie bei den Kistenshops, nie als Aufschlag. Die Zeile dazu kommt nur, wenn die GERECHNETE Gebuehr ueber 0 liegt: Bei 5 Prozent auf 19 Mark ist sie abgerundet 0. Die Grenze kommt aus Shopregel.brauchbareGebuehr, nicht aus einer zweiten Zahl.
- FUND beim Schreiben der Befehlsprobe: Der Kaufzweig fragte entscheid.erfolgreich(), und das Wort meint NUR ERTEILT (den Zuschnitt) - ein Kauf traegt GEKAUFT. Der Zweig lief nie, der Verkaeufer erfuhr nichts. Die Frage heisst jetzt nach dem Bescheid, und erfolgreich() sagt im Javadoc, was es nicht umfasst.
- FUND, dieselbe Bauart: biete() gab das Grundstueck zurueck, wie es VORHER aussah. Die Naht unterscheidet an ihm, ob angeboten oder zurueckgezogen wurde, und las beides vertauscht - "zurueckgezogen" nach einem Angebot. Jetzt gibt es Grundstueck.mitAngebot, und eine eigene Kernprobe fragt den ENTSCHEID statt der Ablage: Beide koennen einzeln falsch sein.
- FUND, der dritte derselben Art und der folgenreichste: Der Besitzerwechsel liess erworbenAm stehen. Die Verfallsregel aus Plan 16 rechnet damit - ein gekauftes Grundstueck waere nach dem Zeitpunkt des VORBESITZERS schon verwirkt. eigentuemerSetzen setzt ihn jetzt neu und GIBT DEN NEUEN STAND ZURUECK; das Buch baut die Zeile nicht mehr selbst nach. Die Vertragsprobe hielt bis dahin die falsche Zusage - mit der richtigen Begruendung als Grund dafuer.
- Dazu BEHOBEN: Jeder Geldbetrag des Freistadt-Zweigs kommt jetzt aus waehrung.betrag. Die zwei Saetze aus Teil A (frage, absage.zu-wenig-mark) trugen "Mark" hart in der Vorlage - der Betreiber haette das Wort geaendert und es stuende in /freistadt doppelt und verschieden da. Der Fehlbetrag nennt die Einheit jetzt mit.
- Proben 3089 -> 3156. Neu: GrundstuecksbuchTest +8, GrundstuecksablageVertrag +5 x3 Umsetzungen, SpeicherGrundstueckeTest +1 (mit stellbarer Uhr - mit der festen ist der Unterschied gar nicht messbar), FreistadtBefehlTest +13, 16 Bildzeilen, vier Buchungsgrundworte. Einspritzung 33 Faelle, 33/33 gefangen (ein Fall zweimal gefasst: Der erste Anker traf auch loeseAuf, und die echte Pruefung stand davor - der eingespritzte Zweig war unerreichbar).
- Offen in C3: nichts. Offen beim Spieltest: der ganze Weg zu ZWEIT - anbieten, der andere kauft, beide lesen ihre Zeile, /mark verlauf bei beiden. Dazu ein Verkauf an einen ABGEMELDETEN Verkaeufer (er sieht es nur im Verlauf) und zwei Kaeufer gleichzeitig auf derselben Flaeche.
- ~~Teil D: STADTBUERGER.~~ 12.11. GEBAUT, in fuenf Etappen: die TAT in der Wachenschnittstelle, die Buergerschaft im Kern, die schaltbaren Rechte je Stadt, das Bauurteil, die Liste. /freistadt stadtbuerger, /freistadt rechte [an|aus <recht>].
- STADTBUERGER und nicht Buerger, ueberall - im Spieltext wie im Quelltext. Der Rang Buerger kommt aus 40 Spielstunden, die Stadtbuergerschaft aus Grundbesitz. Zwei Bedeutungen fuer ein Wort waeren die Naht, an der jemand das Falsche liest.
- ABGELEITET und keine Mitgliedschaft: keine Tabelle stadt_buerger, kein Beitritt, kein Austritt. Wer kauft, steht drin; wer verkauft, nicht mehr. Die Liste kann nicht veralten, und Schritt 17 bekommt sie aus den Spalten, die schon dastehen.
- Der STADTBESITZER ist KEIN Buerger seiner eigenen Stadt, und daran haengt Teil E: Jeder frische Zuschnitt gehoert ihm. Zaehlte er mit, haette jede Stadt nach dem ERSTEN Schnitt fuer immer einen Buerger, und die Verfallsregel (zwoelf Monate ohne Besuch und ohne Buerger) koennte nie greifen. Ein MITBAUER zaehlt auch nicht - sonst immunisiert ein Stadtbesitzer seine Stadt mit einem Wort.
- DREI SCHALTBARE RECHTE je Stadt: bauen, benutzen, behaelter. Die Dreiteilung steckte schon in Schutzwache.istHeikel und war nur nicht benannt - Openable und Powerable sind Tueren und Knoepfe, ein InventoryHolder ist ein Behaelter, alles andere veraendert den Block. Aus istHeikel wurde tatAn, damit nur EINE Stelle weiss, was ein Behaelter ist.
- Dafuer traegt Schutzwache.Ortsregel jetzt die TAT - pruefe UND grund, und grund auch den Spieler. Die Aufzaehlung Tat liegt im KERN: Sie ist gleichzeitig die Tat und das Recht, und zwei Aufzaehlungen mit einer Abbildung dazwischen waeren zwei Wahrheiten darueber, was ein Behaelter ist.
- beimKlicken stellt jetzt ZWEI Fragen: eine fuer den Block, eine fuer die Hand. Mit einer einzigen - der strengeren - kaeme ein Buerger mit Benutzenrecht durch keine Tuer mehr, solange er eine Axt haelt.
- Die Rechte haengen an der PARZELLENZEILE (Tabelle freistadt_rechte, eine Zeile je freigeschaltetes Recht - Schemaschritt 23). Damit hat der heisse Pfad sie schon in der Hand, wenn er die Stadt gefunden hat: null zusaetzliche Gaenge. Ein viertes Recht braucht keinen Schemaschritt.
- Die Vorgabe fuer NEUE Staedte steht in freistaedte.buergerrechte und ist leer - eine frische Stadt gibt nichts her. Ein unbekanntes Wort wird BEKLAGT, nicht verschluckt.
- Neues Bauurteil ERLAUBT_STADTBUERGER, und jedes Urteil sagt jetzt SELBST, ob es erlaubt. Vorher fuehrte die Stadtwache eine Positivliste zweier Urteile - ein drittes Ja waere darin nicht vorgekommen und still auf VERBOTEN gefallen: gebaut, gruen geprueft, im Spiel wirkungslos.
- Das Buergerrecht sticht die STADT, nicht ein fremdes GRUNDSTUECK: Auf dem vergebenen Grundstueck eines anderen darf auch eine Mitbuergerin nichts, sonst waere Teil C2 fuer jeden Buerger der Stadt aufgehoben.
- Ein abgewiesener Buerger liest seinen EIGENEN Handbalken. Wem die Stadt mitgehoert, dem sagt "Das ist die Stadt von Anna" nichts - er weiss, wessen Stadt es ist; er will wissen, was er dort darf. Das ist der Grund, aus dem Ortsregel.grund einen Spieler bekommen hat.
- BERICHTIGT: FreistadtBefehle und plugin.yml versprachen, Schritt 12 haenge den Rang Buerger an das KAUFEN. Plan 2318 haengt an den Rang nur das GRUENDEN, und die Zusage war in sich gedreht - sie machte den RANG Buerger zur Voraussetzung fuer den Weg, auf dem man STADTBUERGER wird. Dazu nennt befehl.beschreibung.freistadt jetzt alle vier Zweige.
- FUND beim Bauen: SpeicherLager.schreibeMitbauer setzte die Zeile von Hand neu zusammen - dieselbe Stelle, die am 11.11. den Namen verlor. Diesmal waeren es die Buergerrechte gewesen, und LAUTLOS: Geworfen haette nichts, nur waere nach jedem Mitbauer-Setzen jedes Recht weg. Jetzt gibt es Parzelle.mitMitbauern, und einen Kurzkonstruktor mit Namen und ohne Rechte gibt es ABSICHTLICH nicht.
- FUND der Reparatur: Material.isBlock wirft ohne Server (ueber asBlockType in die Registry). aendertBlock fragt darum jetzt die billigen Merkmale zuerst - und erst damit liess sich beimKlicken ueberhaupt durchlaufen. Die Einschraenkung in GrenzeTest 31x ist fuer diesen Zweig aufgehoben.
- Proben 3156 -> 3232. Neu: TatTest (6, ganz neu - istHeikel war von KEINER Verhaltensprobe gedeckt), StadtbuergerschaftTest (7), FreistadtlisteTest (7, auch neu - der Freistadt-Leser hatte keine), GrenzeTest-Tafel 31ar (jeder Zugang nennt seine Tat), SchutzwacheTest +4 (36n/36o Naht, 36p/36q die ersten echten Klickproben), StadtwacheTest +2, FreistadtbuchTest +7, GrundstuecksablageVertrag +2 x3, BesitzLagerVertrag +3 x3, FreistadtBefehlTest +9, ParzelleTest +1, ZwischenspeicherGrundstueckeTest +1, 16 Bildzeilen.
- Einspritzung 35 Faelle, 30 sofort gefangen, 5 FUNDE - alle fuenf mit einer Probe geschlossen und erneut eingespritzt: der Lueckenrueckfall in beimKlicken, die dritte Karte in fuellen (Geisterbuerger nach /freimarken neuladen), der Riegel in Parzelle, die Vorgabe beim Gruenden, und istStadtbuerger, dessen Gegenbeispiel nicht ankam (Anna hatte in der Probe gar keinen Grund).
- Dazu ein FUND an der RUNDE selbst: Ohne --continue brach Gradle nach dem ersten roten Modul ab, die XML-Berichte des VORIGEN Falls blieben liegen, und der Bericht nannte Proben, die nicht dazu passten. Zwei Faelle erschienen dadurch als FUND und waren von Hand GEFANGEN. Der Treiber leert die Berichte jetzt und verlangt eine MINDESTZAHL gelaufener Proben - ein unvollstaendiger Lauf ist kein Ergebnis.
- Offen in D: nichts. Offen beim Spieltest: /freistadt rechte an benutzen, dann mit einem zweiten Spieler eine Tuer im Gemeinschaftsgebiet oeffnen und einen Block setzen (das eine geht, das andere nicht); der Handbalken des Buergers gegen den eines Fremden; /freistadt stadtbuerger vor und nach einem Kauf.
- ~~Als Naechstes E - Kasse und Auflagen (Teleporter, Ladenlizenz, Verfallsregel).~~ 16.-17.11. GEBAUT, E1 bis E6. Offen: Einspritzungsrunde und Spieltests.
  - D: BUERGER. Wer ein Grundstueck in der Stadt hat, ist Buerger; Liste und was ein Buerger im Stadtgebiet darf.
  - E: KASSE UND AUFLAGEN. Teleporter und Ladenlizenz erst ab einem vergebenen Grundstueck und einer Regelwand; Verfallsregel nach zwoelf Monaten ohne Besuch und ohne Buerger (Plan 16).
- ~~Teil E1: die Stadtkasse.~~ 16.11. GEBAUT: Stadtkasse im Kern, eine ZWEITE Kasse ohne Startguthaben, die ihre Konten aus einer gespeicherten Kontonummer je Stadt holt (Schemaschritte 24/25). /freistadt kasse [abheben <betrag>].
- Zwei Fallen, die keine Erkundung gefunden hat. stelleKontoSicher laeuft bei JEDER Buchung: Die erste Gutschrift an eine Stadt haette ihr das Spieler-Startguthaben geschenkt - bei 399 Staedten rund 20 Millionen Mark, und jede einzelne Buchung saehe richtig aus. Darum prueft die Stadtkasse beim Bauen selbst nach, dass ihre Kasse auf einem unbekannten Konto 0 gibt.
- Die zweite: AUTO_INCREMENT-Nummern werden nach einem Neustart wieder vergeben. Eine abgeleitete Kontonummer haette einer neu gegruendeten Stadt Guthaben und Verlauf der alten in die Hand gegeben. Die Stadt traegt ihre Kontonummer jetzt selbst.
- ~~Teil E2: der Regeltext.~~ 16.11. GEBAUT: /freistadt regeln [loeschen|<text>], hoechstens 256 Zeichen, Schemaschritt 26. Lesen darf jeder, setzen nur der Besitzer. Der Text wird EINGESETZT und nicht eingespleisst - ein <red> darin kommt zeichengleich an und wird keine Serverdurchsage.
- ~~Teil E3a: die Auflage am Teleporter.~~ 17.11. GEBAUT: Freistadtbuch.auflage gibt VIER Zustaende und nicht ja/nein, damit der Satz sagen kann, WAS fehlt. Besitzer, Mitbauer und Stadtbuerger duerfen immer hinein - sonst waere die Gruendung eine 400.000-Mark-Falle.
- Der Erfolgsfall eines Stadtwarps ist ohne Server NICHT herstellbar (Material.isSolid geht in die Bukkit-Registry). Gemessen wird die Entscheidung im Kern, die VERDRAHTUNG haelt Quelltexttafel 31at: darfReisen VOR reise.fuehre, und dazwischen ein return. Auf dem Weg dorthin sind sechs echte Luecken der Probenwelt geschlossen worden (World.getUID, getWorldBorder, getMaxHeight, Player.getVehicle, getNearbyEntities, teleportAsync).
- ~~Teil E3b: das Ladenrecht.~~ 17.11. GEBAUT: In einer Stadt ohne erfuellte Auflage entsteht KEIN NEUER Kistenshop; bestehende handeln weiter. Der Riegel sitzt in Shopbuch.anlege - der einen Stelle, an der aus einer Spielerhandlung ein Shop wird -, nach der Frage, ob dort eine Kiste steht, aber vor der Besitzfrage an ihr und vor beiden Shopablagefragen.
- Der STADTBESITZER ist hier NICHT ausgenommen, anders als beim Teleporter. Dort muss er hinein, sonst kaeme er nicht in seine eigene Stadt; hier sperrt ihn nichts ein. Mit einer Ausnahme fuer ihn baute er seine ganze Markthalle und erfuellte die Auflage nie - der Riegel traefe nur noch seine Buerger.
- FUND beim Bauen: auflage(stadt) kann ohne Grundstuecksablage NIE erfuellt sein. Ein Riegel, der sie unmittelbar fragte, haette bei stummer Datenbank in JEDER Freistadt jeden neuen Kistenshop verweigert. darfLaden faengt das ab wie darfReisen - und die Startzeile, die das MELDET, war an zwei Stellen zugesagt und nie geschrieben. Jetzt steht sie da.
- Die drei Worte fuer {was} sind von warp.auflage.* nach freistadt.auflage.* gezogen: Seit E3b liest sie ein zweiter Satz. Gewaehlt werden sie an EINER Stelle (Sprachschluessel.auflagenwort) und sind damit eine Familie wie die Taetigkeits- und Rechteworte - GrenzeTest 30 hat genau das gemeldet.
- Zwei Saetze sagten nur noch die halbe Wahrheit und nennen jetzt beide Wirkungen: freistadt.gegruendet und freistadt.regeln.geloescht.
- Proben 3232 -> 3335. Gezaehlt: StadtkasseTest (11, neu), BesitzLagerVertrag +7 (also 21, der Vertrag laeuft gegen drei Lager), FreistadtbuchTest +14, FreistadtBefehlTest +12, ShopbuchTest +11, ParzelleTest +6, FreistadtlisteTest +4, ShoptextTest +2, GrundstuecksbuchTest +2, WirtschaftsstandTest +1, WarpBefehlTest +1 (zwei gingen wieder heraus), Tafeln 31at und 31au, dazu Bildzeilen und die achte Familie in SendewegTest.
- BEGUTACHTUNG von E3b: fuenf Linsen, 20 behauptete Funde, je drei Widersacher. Zwei echte Programmfehler, einer davon von drei Linsen unabhaengig gefunden:
  - stadtAn bekam von meinem Riegel eine BELIEBIGE Welt - alle anderen Aufrufer halten einen Weltschluessel. BesitzLager.finde filtert absichtlich nicht nach art, und Marktstadt und Freistaedte teilen das Lager: Ein Shopschild in Altfurt an Block 10/10 fragte nach Feld 0/0 und bekam die RASTERPARZELLE. auflage() warf, mein Fang schluckte es, und ins Protokoll kam bei JEDEM Shopschild dort ein Datenbankausfall, den es nicht gibt. Jetzt wird istFreistadt gefragt - nicht ein vierter Weltschluessel, dessen Versagen still waere.
  - Ein Neuladen der zwei Auflagenschalter war voellig stumm: uebernimm zaehlte nur Zahlen auf, einen Melder fuer Ja-Nein gab es nicht. Wer das Ladenrecht nach einem Event wieder einschaltet, las "nichts geaendert".
- Dazu acht Dokumentationsstellen, die die Auflage noch mit EINER Wirkung beschrieben (config.yml, Freistadtwerte, auflage(), Schemaschritt 26, drei Saetze), und drei Luecken in meinen eigenen Proben: der Grund der Absage war nur mit OHNE_BEIDES gemessen, bauen.ueberall war zugesagt und ungeprueft, und die Zusage "ausserhalb der Freistaedte fasst der Riegel nichts an" ruhte auf einem LEEREN Lager.
- Und zweimal mein eigener Einschub zwischen Doku und Deklaration: einmal in Sprachschluessel (31ak hat es gefangen), einmal in der Vorlage, wo mein Satzblock den Kommentar von shop.absage.kein-bundrecht abgerissen hat - dort faengt es keine Tafel, nur ein Leser.
- Einspritzung 5 Faelle, 5/5 gefangen, jeder von genau den Proben, die es sollten: Riegel weg (2 rot), Kern-Entscheid weg (4), Bundshop nicht ausgenommen (1), erfuellte Auflage weist trotzdem ab (2), Ausfallzweig weg (1). Die Ruecknahme von Fall E scheiterte am Anker - LEHRE: Ein Einspritzanker muss in BEIDE Richtungen einmalig sein.
- Offen, braucht eine Entscheidung: Verliert eine Stadt ihr letztes vergebenes Grundstueck (der Besitzer kauft es zurueck), fallen Teleporter UND Ladenrecht ohne ein Wort. Wer soll es lesen - der Besitzer beim Kauf, der Verkaeufer, beide? Gehoert zu E5/E6, wo die Stadt ohnehin Zustandssaetze bekommt.
- ~~Offen, klein: Die VORGABE-Buergerrechte werden beim Neuladen stumm getauscht.~~ War am 20.11. mit dem Muenzhaus schon gebaut (Uebersetzer taten()); gefehlt hat die PROBE - 31cw prueft nur die Nennung im Quelltext. Jetzt haelt WirtschaftsstandTest beide Richtungen.
- ~~Teil E4: der Besuchsstempel.~~ 17.11. GEBAUT: Jede Freistadt merkt sich den TAG ihres letzten Besuchs (Spalte parzellen.besucht, Schemaschritt 27). Ein eigener Mitfahrer Besuchslauf im vorhandenen Minutentakt, je anwesendem Spieler; der Kern bleibt zeitfrei, der Tag kommt fertig herein.
- ZWEI WAECHTER gegen dasselbe Schreiben, wie bei Mietlauf und Truhenlauf: eine Menge der heute gestempelten Staedte im Arbeitsspeicher (spart die Abfrage) UND der Vergleich mit der Zeile (ueberlebt den Neustart). Kein doppelter Riegel - jeder kann, was der andere nicht kann.
- FALLE, die die Erkundung gefunden hat: Die vorhandene Messstelle.Ortsquelle liefert den WELTNAMEN (freistaedte), das Freistadtbuch rechnet mit dem SCHLUESSEL (freimarken:freistaedte). Haette der Stempel sie benutzt, waere er stumm leer geblieben - kein Wurf, keine Klage, und nach einem Jahr raeumt der Verfall jede Stadt ab.
- Und der Riegel aus E3b ist in den Kern gewandert: stadtAn filtert jetzt selbst auf istFreistadt. Mit dem Stempel gab es einen ZWEITEN Aufrufer mit beliebiger Welt, und zwei Riegel fuer dieselbe Frage laufen auseinander. Die Methode heisst stadtAn und verspricht eine STADT.
- ~~Teil E5: der Verfall.~~ 17.11. GEBAUT: Verfallsregel im Kern (fuenf Staende, eine Frist), Verfallswerte als eigenes Satzstueck, Verfallslauf als Mitfahrer UND Beitrittszuhoerer. Zustand.erlaubtBauen() wird zum ersten Mal gefragt - der Riegel sitzt an EINER Stelle vor allen drei Ja-Zweigen von darfBauen, nicht in ihnen.
- ZWEI WARNUNGEN SIND ZWEI STUFEN und keine zwei verschickten Nachrichten. Der Besitzer liest beim ANMELDEN, in welcher Stufe seine Stadt steht - und so lange wieder, bis er etwas tut. Nichts wird gezaehlt und nichts gespeichert: Ein Zaehler 'schon gewarnt' stuende falsch, sobald eine Nachricht verloren geht, und ein Besuch waere keine Entwarnung. Die Zeile besucht ist die ganze Wahrheit.
- In TAGEN gerechnet, nicht in Monaten - wie jede Frist dieses Projekts. Mit plusMonths haengt die Frist am Gruendungsdatum: Eine Stadt vom 31. Januar verfiele dreissig Tage eher als eine vom 1. Februar.
- Eine Stadt MIT Stadtbuerger verfaellt nie, auch unbesucht nicht - und ihr Besitzer wird dafuer auch nicht gewarnt. Ohne Grundstuecksablage gilt JEDE Stadt als bewohnt: Die umgekehrte Richtung als bei der Auflage, und dieselbe Lesart - eine Wirtschaftsregel, die ausfaellt, darf nicht schaden.
- ENTSCHIEDEN (Nutzer, 17.11.): Warnungen bei der naechsten Anmeldung; entfernt wird eine verfallene Stadt NICHT von selbst, nur das Team raeumt; die Ladengebuehr gibt es zweifach, einmalig beim Anlegen UND taeglich, getrennt einstellbar.
- Proben 3335 -> 3402. Neu: VerfallsregelTest (9), BesuchslaufTest (11), VerfallslaufTest (10), VerfallswerteTest (6), FreistadtbuchTest +11, BesitzLagerVertrag +3 (also 9), ParzelleTest +2, dazu sechs Bildzeilen. Tafeln 31ai/31b/31c/31u nachgezogen.
- Die Javadoc-Falle zum sechsten Mal in dieser Sitzung - diesmal in Freistadttext, gefangen von Tafel 31ak und nicht vom Uebersetzer.
- ~~Teil E6: das Ende einer Stadt und die Ladengebuehr.~~ 17.11. GEBAUT: /freistadt aufgeben (der Besitzer, Recht freimarken.freistadt.aufgeben) und /freistadt raeumen <name> (das Team, Recht .raeumen), beide mit Rueckfrage und bestaetigen. Zuruecklaufen tun freistaedte.erstattung-prozent (Vorgabe 50) vom EINGESETZTEN Preis der Zeile UND das Guthaben der Stadtkasse.
- Das Guthaben geht MIT, und ohne diese Zeile waere es verloren: Das Konto bleibt bestehen, aber der Weg dorthin fuehrt ueber die Stadtzeile - und die gibt es nicht mehr.
- DER RIEGEL, OHNE DEN DER BEFEHL EIN DIEBSTAHL WAERE: Eine Stadt, in der jemand anderem ein Grundstueck gehoert, laesst sich nicht aufloesen. Ein Stadtbuerger hat sein Grundstueck BEZAHLT. Der Besitzer kauft es zurueck, dann geht es - und solange dort jemand wohnt, verfaellt sie ohnehin nie.
- ERST ENTFERNEN, DANN ZAHLEN, und das ist die gefaehrlichste Entscheidung in E6. Umgekehrt machte ein gescheitertes Entfernen aus dem Befehl eine GELDQUELLE: Die Stadt stuende noch, der Spieler tippt erneut und wird ein zweites Mal bezahlt. So ist der schlimmste Fall eine Schuld mit Namen und Betrag im Protokoll - eigener Bescheid GELD_HAENGT, eigener Satz.
- DIE LADENGEBUEHR gibt es zweifach, wie der Nutzer entschieden hat: einmalig beim Anlegen eines Kistenshops (freistaedte.ladengebuehr.anlage, Vorgabe 250) und taeglich je Shop (.tag, Vorgabe 0). Beide fliessen in die Stadtkasse, beide rechnen ueber EINE Stelle im Kern.
- Die ANLAGEGEBUEHR ist NICHT 0 ab Werk, und das ist Absicht: Die andere Einnahme einer Stadt (handel.<lage>) liegt bei 0, und mit zwei Nullen waere die Stadtkasse eine Kasse, in die nie etwas fliesst - ein Betreiber hielte sie fuer kaputt. Reihenfolge: PRUEFEN, schreiben, erheben. Wer nicht zahlen kann, bekommt keinen Shop UND verliert kein Geld.
- Die TAGESGEBUEHR ist 0 ab Werk, und das ist ebenso Absicht: Sie hat eine Folge, die die einmalige nicht hat - wer sie nicht zahlen kann, VERLIERT SEINEN SHOP. Das Schild wird abgebaut, die Ware bleibt in der Kiste (wie bei jedem Abbau). Ob ein Server das will, entscheidet sein Betreiber.
- Offene Grenze, bewusst: Der Tageslauf baut den Shop in der ABLAGE ab, das Schild in der Welt bleibt stehen - einen Block in einem nicht geladenen Chunk zu entfernen hiesse, ihn zu laden, fuer jeden Shop einzeln. Gehandelt wird ueber die Ablage und nicht ueber den Text, also handelt es nicht mehr; wer es abbaut, hat ein Schild.
- Tafel 31ai hat eine echte Reihenfolgefrage gefunden und ist dabei SCHAERFER geworden: richteShopsEin laeuft aus richteSpielzeitEin und nicht aus onEnable. Die Tafel prueft fuer diesen Fall jetzt die Reihenfolge INNERHALB der Methode - wer den Aufruf hinter new Spielzeitwache schiebt, gibt ein null hinein, und weil null erlaubt ist, faellt es nirgends auf.
- Proben 3402 -> 3435. Neu: LadengebuehrlaufTest (10), FreistadtbuchTest +11, ShopbuchTest +4, dazu acht Bildzeilen und die Tafeln 31b/31c/31ai/10e/11b.
- ~~Einspritzungsrunde ueber ganz E.~~ 17.11.: 40 Faelle, 36 sofort GEFANGEN, 4 FUNDE - alle vier geschlossen und erneut eingespritzt. Kein unvollstaendiger Lauf, kein Ankerfehler. Abgedeckt: Stadtkasse, Regeltext, beide Auflagenriegel, Besuchsstempel, alle drei Verfallsgrenzen, Zustandsriegel, Wiederbelebung, Ende einer Stadt und beide Ladengebuehren.
- DIE LEHRE DER RUNDE, und sie gilt fuer jeden kuenftigen Lauf: Eine Probe, die sagt "nichts ist passiert", ist blind gegen einen Lauf, der ABGEBROCHEN ist. Drei der vier Funde waren genau das.
  - F37: Ein Bundshop hat keinen Besitzer. Ohne die Ausnahme wirft das Abbuchen, der Fang beendet den ganzen Durchgang - im Spiel haelt EIN Bundshop die Tagesgebuehr fuer ALLE Laeden auf, jeden Tag.
  - F40: Dasselbe mit einem Shop in der Wildnis. Der erste Behebungsversuch trug NICHT: Ein zweiter, zahlender Shop beweist nur, dass der Lauf BIS ZU IHM gekommen ist, und alle() ist nach der Stelle sortiert - welcher zuerst kommt, ist Zufall der Koordinaten. Erst eine Probe gegen die KLAGE im Protokoll traegt, und die haengt an keiner Reihenfolge.
  - F33: Alle Erstattungsproben gruendeten zum Vorgabepreis und rechneten gegen denselben - der Unterschied zwischen "Preis der Zeile" und "Preis aus der Datei" war nie zu sehen. Im Spiel: Senkt der Betreiber den Gruendungspreis auf 100.000, bekaeme wer 400.000 gezahlt hat nur noch 50.000 statt 200.000.
- F3 war KEIN Probenfehler, sondern ein DOPPELTER RIEGEL: Hinter betrag > stand liegt ein zweiter - abbuchen gibt false und antwortet mit demselben Bescheid. Zwei Pruefungen fuer denselben Fall machen sich gegenseitig unpruefbar. Die Vorpruefung bleibt (sie ist die Kostenreihenfolge), aber ihr Fehlen ist jetzt BEOBACHTBAR: Mit einer Kasse, die den Stand nennt und beim Abbuchen wirft, kommt mit Vorpruefung ZU_WENIG und ohne sie GESTOERT.
- Proben 3435 -> 3437. ZWEI REGELN DES TREIBERS, die beim naechsten Mal wieder gelten: Zurueckgenommen wird aus dem GEMERKTEN Urtext und nicht ueber einen zweiten Anker - daran war in dieser Sitzung schon eine Ruecknahme gescheitert, weil ein Rueckanker in BEIDE Richtungen einmalig sein muss. Und ein Lauf unter der Mindestzahl gelaufener Proben ist KEIN Ergebnis, nicht ein Fang.
- Offen in E: nur noch die Spieltests (E1 bis E6, oben aufgeschrieben).
- Offen beim Spieltest von E6: /freistadt aufgeben ohne und mit bestaetigen; dasselbe mit einem vergebenen Grundstueck in der Stadt (muss anhalten); /freistadt raeumen <name> als Operator und das Geld beim abwesenden Besitzer nachsehen; ein Shopschild in einer Stadt setzen und die 250 Mark in /freistadt kasse finden; ladengebuehr.tag auf 10 setzen, neuladen, und einen Shop eines leeren Kontos verschwinden sehen - mit voller Kiste.
- Offen beim Spieltest von E4/E5: in einer Stadt stehen und im Protokoll den Stempel sehen; verfall.unbesucht-tage auf 1 setzen, neuladen, eine Minute warten und die Stadt verfallen sehen; dann als Besitzer hineingehen (sie wacht auf) und als Fremder (sie wacht nicht auf); mit verfall.warnung-frueh-tage und einem zurueckgesetzten Besuchstag beide Warnstufen beim Anmelden lesen.
- Offen beim Spieltest von E: Kasse fuellen und abheben; /freistadt regeln mit einem <red> darin; als Fremder in eine Stadt ohne Auflage warpen; in einer Stadt ohne Auflage ein Shopschild setzen (auch als Besitzer) und danach mit Regeltext und vergebenem Grundstueck noch einmal.
- ~~Offen vor Teil A: Wie BREIT ist die Leere?~~ 11.11. ENTSCHIEDEN: 32 Bloecke, und die Felder sind mit BARRIEREBLOECKEN eingegrenzt (Wunsch des Nutzers). Die Barriere loest zwei Dinge auf einmal: Niemand faellt in die Leere, und niemand brueckt hinueber. Bei 256 Feldkante und 32 Leere passen rund 400 Staedte in die Welt - der Plan rechnete mit ~320.
- Die BARRIERE STEHT IN DER LEERE, nicht auf dem Bauland - dieselbe Entscheidung wie bei der Mauer der Marktstadt, die auf dem Weg steht. Wer 256 verspricht, liefert 256; eine Barriere auf dem Feld naehme der Stadt den Ring von 1.020 Bloecken, an dem jeder bauen will.
- ~~Offen vor Teil A: Wohin fuehrt das Tor in Altfurt?~~ 11.11. ENTSCHIEDEN: auf das ANKUNFTSFELD (Feld 0/0, gehoert niemandem). Dort steht zusaetzlich ein VERTEILER - oeffentliche oder hervorgehobene Staedte per Schildklick erreichbar (Wunsch des Nutzers). Der Verteiler wird ein eigener Teil: Schilder zu Staedten brauchen erst Staedte.
- ~~Teil A1: die Rechnung im Kern.~~ 11.11. GEBAUT: Stadtfeldplan (STADTFELD, BARRIERE, LEERE), feldNummer/feldAnfang/feldMitte, istAnkunftsfeld. Die LEERE gewinnt gegen die Barriere - eine unsichtbare Wand mitten zwischen vier Staedten begrenzt nichts und laesst den Server kaputt wirken. Neun Proben, Einspritzungsrunde 5/5 gefangen. Proben 2811 -> 2821.
- Die Probe hat beim ersten Lauf MICH berichtigt: Bei -32 steht der Rand von Feld -1, das Bauland faengt erst bei -33 an. Die Zeile steht jetzt mit dem Grund in der Probe.
- ~~Teil A2: der Weltgenerator.~~ 11.11. GEBAUT: StadtfelderGenerator (Geschwister des MarktstadtGenerator) und Feldboden (die Blocktabelle, Geschwister von Stadtboden). Der Normalfall dieser Welt ist NICHTS - darum wird jeder Chunk zuerst ganz geleert und erst danach gefuellt. Umgekehrt haette jeder vergessene Fall einen Steg zwischen zwei Staedten stehen lassen.
- Vier Griffe je Spalte statt siebzig: Die Schichten sind zusammenhaengend, also setzt jeder Griff eine ganze Schicht. Bei rund 140.000 Chunks ist das der Unterschied zwischen Minuten und Stunden.
- ~~ZWEI BIOME, weil das Biom den Himmel faerbt.~~ 11.11. BERICHTIGT: Tut es nicht - PLAINS und THE_VOID haben in der Oberwelt beinahe denselben Himmel. Getrennt wird trotzdem, aber aus dem Grund, der traegt: Das Biom entscheidet, was WAECHST. Ein Feld ist eine Wiese, die Leere nichts.
- ~~Unter der Barriere ist LUFT.~~ 11.11. FEHLER, behoben: Das war ein LOCH. Wer am Feldrand einen Block tief graebt und zur Seite tritt, stand unter der Wand in der Leere und fiel. Die Wand reicht jetzt von der Weltunterkante bis ueber den Kopf - ein Rundweg wird sie dadurch nicht, auf einer Barriere steht niemand.
- Die Weltart stadtfelder ist verdrahtet (Weltangabe, Weltenliste, Weltenbau, FreimarkenPlugin), und welten.freistaedte traegt sie in der ausgelieferten config.yml - OHNE Startwert, denn das Gelaende kommt aus der Rechnung.
- FUND beim Bauen: Die Probe "jede Welt hat einen eigenen Startwert" nahm alles ausser RASTER. Das war eine handgeschriebene Ausnahmeliste; gefragt wird jetzt startwertWirkt(), also die WIRKUNG statt des Namens. Mit vier Arten ohne Startwert waere die Liste beim naechsten Zuwachs stehengeblieben.
- Das Tor aus Altfurt braucht keine eigene Zeile: Reise nimmt den Erscheinungspunkt der Welt, und der ist die Mitte des Ankunftsfeldes (getFixedSpawnLocation). Geprueft am Quelltext von Reise und Weltenbau - jener holt den Punkt nur herein, wenn er AUSSERHALB der Grenze liegt, und 127/127 liegt drin.
- Proben: StadtfeldplanTest (9) und FeldbodenTest (4). Der Generator selbst hat keine - ChunkData braucht einen laufenden Server, und das Vorbild hat aus demselben Grund auch keine. Proben 2821 -> 2825.
- LESESUCHE ueber Teil A (11.11.): fuenf echte Funde, alle behoben. Die Zusammenfuehrung des Workflows ging schief (jedes Urteil verloren) - die Funde selbst waren gut, durchgerechnet habe ich sie von Hand.
- Die Wand war ausserdem einen Block ZU NIEDRIG: gezaehlt wurde ab dem Grasblock, stehen tut der Spieler einen darueber. Die Probe sagte trotzdem gruen - sie schrieb assertEquals(4, BARRIERE_HOCH) die Deklaration ab, statt etwas zu messen. Jetzt gemessen, und die Erdschichten ebenso.
- Feldboden.darueber hatte KEINEN Aufrufer, und sein Javadoc nannte den Generator als Nutzer. Geloescht - zwei Tabellen fuer denselben Wandblock sind das Fehlerbild, das niemand sucht.
- ~~Teil A3: die Weltgrenze.~~ 11.11. GEBAUT: Feldgrenze im Kern, Geschwister von Stadtgrenze. Die Kante in der Datei ist ein WUNSCH und kein Mass: Aus 6000 werden 20 Felder je Achse und die wirkliche Kante 5792 um -16/-16. 6000 ist kein Vielfaches von 288 - die alte Grenze schnitt 84 Felder an und lief mitten durch Feld 10, dessen Mitte bei 3007 liegt. Ein Warp dorthin haette den Spieler VOR die eigene Weltgrenze gesetzt.
- Also 399 Staedte und nicht "rund 400": Feld 0/0 ist das Ankunftsfeld.
- Weltenbau braucht einen Server und hat keine Probe. Zwei Quelltexttafeln halten ihn: 31w schreibt auf, WAS an setSize und setCenter geht, 31x, dass das Mindestmass genau einmal dasteht - unter 608 wirft Feldgrenze.ausKante, und zwar mitten im Start.
- Proben 2825 -> 2839. Zwei Einspritzungsrunden, 6/6 und 9/9 gefangen; die beiden, die in der ersten Runde nie rot wurden, haben die Nachzuegler F8 und F9 rot gemacht.
- ~~Offen in Teil A: Eine zweite Welt mit art: stadtfelder bekaeme Stadtfelder, aber kein Freistadtbuch.~~ 11.11. MIT TEIL B GESCHLOSSEN: Weltenliste bindet stadtfelder an freistaedte.welt, in BEIDE Richtungen - wie raster an die Marktstadt. Milder als dort: Die Welt wird uebersprungen, der Server laeuft weiter.
- ~~Teil B: GRUENDEN.~~ 11.11. GEBAUT: /freistadt (Liste), /freistadt gruenden &lt;name&gt; (Rueckfrage mit Preis und Restguthaben), /freistadt gruenden &lt;name&gt; bestaetigen, /warp freistadt &lt;name&gt;.
- ENTSCHIEDEN: Eine Freistadt ist eine ZEILE IN parzellen mit art: freistadt und einer neuen Namensspalte - keine eigene Tabelle. Art.java schreibt den Grund selbst hin (Plan 6.1: ein Grundstueck in einer Freistadt ist dieselbe Zeile mit Elternteil). Eine eigene Tabelle wuerde den zweiten Ort fuer "wem gehoert dieses Feld" erst SCHAFFEN - und die Schutzwache muesste bei jedem Block zwei Orte fragen.
- Dadurch findet finde(welt, x, z) eine Stadt mit DERSELBEN Punktabfrage wie eine Parzelle. x/z sind Feldnummern, nicht Bloecke.
- Der NAME ist der Riegel: Schemaschritt 19 haengt name VARCHAR(32) NULL und UNIQUE KEY (welt, name) an. "Ist der Name frei?" im Befehl haette zwischen Fragen und Schreiben eine Luecke, dieser Schluessel hat keine.
- Parzelle bekommt ein zwoelftes Feld und einen kurzen Konstruktor fuer die 14 Aufrufstellen. KEIN stiller Vorgabewert: Der kanonische Konstruktor wirft, wenn eine FREISTADT ohne Namen ankommt - und ebenso, wenn eine Parzelle einen traegt.
- Zuteilung von innen nach aussen, Ring um Ring um das Ankunftsfeld: Die ersten Staedte liegen beieinander, und die Welt erzeugt nur die Chunks, wo jemand ist. Gefragt wird die Feldgrenze und nicht die Zahl aus der Datei.
- 400.000 Mark und eine Stadt je Spieler stehen in der config.yml (freistaedte.gruendungspreis, freistaedte.hoechstzahl) - beide neuladbar. Die Hoechstzahl ist eine ZAHL und keine Eins im Programm: Schritt 12 haengt die Raenge daran.
- Zwei Stufen beim Gruenden, wie bei /laden mieten. 400.000 Mark sind kein Knopfdruck, und ein Vertipper im Namen darf sie nicht kosten.
- KEIN Vorschlagsgeber beim GRUENDEN: Der Name ist frei erfunden, ein Vorschlag waere dort eine Behauptung. Beim WARP gibt es ihn seit dem 11.11., siehe unten.
- ~~Offen in Teil B: Keine Probe fuer den Zweig /warp freistadt. Und der Vorschlagsgeber fehlt.~~ 11.11. BEIDES GESCHLOSSEN.
- Stadtverzeichnis (Kern): die Namen im Arbeitsspeicher, damit der Vorschlagsgeber nicht bei jedem Tastendruck in die Datenbank laeuft. Das Freistadtbuch BESITZT es (Feld), statt es hereingereicht zu bekommen - es ist die einzige Stelle, die gruendet, und hereingereicht liesse es sich vergessen.
- Der Zwischenspeicher traegt VORSCHLAEGE, die Tat fragt weiter die Ablage. Ein verpasster Eintrag kostet damit einen Vorschlag und keine Reise - der Unterschied zu ZwischenspeicherShops, der ANTWORTET und dessen veraltete Antwort einen Trichter abziehen liess.
- erneuereVerzeichnis gibt die ANZAHL zurueck, und die Startzeile nennt sie. So haengt der Aufruf an einer Zeile, die der Betreiber bei jedem Start liest - eine Methode, die nur fuellt, liesse sich vergessen zu rufen, und kein Probenlauf meldete das.
- KEINE Obergrenze fuer die Vorschlaege: Eine Kappung bei etwa fuenfzig traefe genau den Spieler, dessen Stadt an einundfuenfzigster Stelle steht - er tippt den richtigen Anfang und sieht sie nicht. Vanilla schlaegt bei /give @p ueber tausend Gegenstaende vor.
- FUND beim Schreiben der Probe: warp.stadt-unbekannt traegt eine Umbruchmarke, der Zweig sendete mit sprache.eineZeile - das WIRFT. Im Spiel gab ein vertippter Stadtname eine rote Brigadier-Zeile statt der Absage. Die Bildtafel hielt den zweizeiligen Satz zeichengleich fest und sah es trotzdem nicht: Sie ruft den Satz selbst und kommt am Zweig vorbei.
- Die Wurzel war allgemeiner: Die drei Freistadt-Zweige waren die einzigen in WarpBefehle, die unmittelbar aus dem Zweig sendeten - genau das, wovor das Javadoc von SENDESTELLEN seit Schritt 5 warnt ("wer die Zahl wieder steigen sieht, sieht einen Satz, der ins Programm zurueckgewandert ist"). Sie gehen jetzt ueber die Naht, GrenzeTest 31n haelt die sechs Aufrufe.
- Befehlsprobe sieht jetzt ins VORSCHLAGSFENSTER (Methode vorschlaege). Die dritte Sache, die keine Tafel sagen kann: Ein Vorschlagsgeber am falschen Argumentknoten sieht im Quelltext gleich aus und zeigt im Spiel die Welten statt der Staedte - Probe 41i.
- Nebenbei behoben: VerdienstregelTest hatte einen abgerissenen Javadoc-Block (Warnung beim Uebersetzen). Der Satz gehoerte zu MIT_LOEHNEN und war beim Einfuegen von werteMitTafel davon abgerutscht.
- LESESUCHE 11.11. (6 Linsen, jeder Fund von zwei Skeptikern geprueft): vier bestaetigt, alle behoben - und der wichtigste lag gar nicht im Neuen.
- ZWEITER FUND derselben Art, voellig unabhaengig: shop.absage.schon-ein-shop ist zweizeilig (Absage + Weg zum Aendern), Shopwache holte ihn mit eineZeile. Wer ein zweites Schild an einen Shop schrieb, las GAR NICHTS, und der Betreiber bekam je Versuch einen Stapelabzug. Auch hier hielt die Bildtafel den Satz zeichengleich fest.
- Zweimal derselbe Fehler heisst Fehlerklasse: VorlageTest 10h sucht JEDEN eineZeile-Aufruf im Quelltext, loest den Satz auf, BAUT ihn und verlangt genau eine Zeile. Nicht nach Backslash-n gesucht - geprueft wird die Bedingung, an der eineZeile wirklich wirft; eine Zeichensuche waere eine zweite Abschrift und bei einer Fluchtform blind.
- Dazu 10i, ohne die 10h eine Behauptung waere: die fuenf Stellen, an denen der Satz erst zur Laufzeit feststeht, als TEXT festgenagelt. Einzeln geprueft - vier holen nur Art.HANDBALKEN, fuer den Sprachliste schon beim LADEN ueber jede Umbruchmarke klagt. Bleibt genau eine (Bonustext), die nur Nachsehen deckt.
- erneuereVerzeichnis riss den ganzen Serverstart ab: der erste Datenbankgriff NACH dem Weltenbau, und der dauert bei einer neuen Welt Minuten. Faellt die Datenbank in der Zeit aus, lief kein registriereBefehle mehr - und die Marktstadt stand geladen und OHNE Schutzwache da. Jetzt gefangen wie richteShopsEin; der Preis ist ein leeres Vorschlagsfenster.
- Die Startzeile belegte nichts: Sie zaehlte die Ablagezeilen statt das Verzeichnis, haette also bei weggefallenem Fuellen dieselbe Zahl genannt. Jetzt gibt erneuereVerzeichnis verzeichnis.anzahl() zurueck - und bei stummer Ablage "UNBEKANNT VIELE".
- EIGENE PROBE MASS NICHTS, zweimal hintereinander: Das Gegenbeispiel "altneustadt" sortiert vor "neu" und liegt ausserhalb des tailSet; der zweite Anlauf "oberneustadt" wurde vom break hinter "nordheim" abgeschnitten. Erst ohne den Nicht-Treffer dazwischen wurde sie rot. Die erste Einspritzung hatte es verdeckt, weil sie tailSet UND startsWith zugleich austauschte - zwei gleichzeitige Aenderungen decken sich gegenseitig.
- Nebenbei: ein abgerissener Javadoc-Block in VerdienstregelTest (Uebersetzungswarnung), ein Umlaut in Feldboden, zwei falsch gewordene Zahlen im Kopf von ReiseSatz.
- Proben 2921 -> 2942. Neu: StadtverzeichnisTest (9), FreistadtbuchTest +4, WarpBefehlTest 41e-41j (6), VorlageTest 10h/10i. Zwei Einspritzungsrunden: 12/12 und 6/6 - der sechste erst nach dem Schaerfen der Probe.
- Das Literal freistadt in /warp braucht ein EIGENES .executes: Brigadier gibt bei einem passenden Literal nur dieses zurueck, die Argumentknoten werden gar nicht mehr betrachtet. Ohne das endete "/warp freistadt" ohne Namen in Brigadiers englischem "Unknown or incomplete command".
- FUND der Einspritzungsrunde (12 Faelle, 11 sofort gefangen): Der Riegel "eine Freistadt braucht einen Namen" war von KEINER Probe gedeckt - alle legten Staedte mit Namen an. Ein Riegel, den nie etwas ausloest, ist gruen mit und ohne. Zwei Proben in ParzelleTest nachgetragen, danach 12/12.
- Die Bildtafel hat die zweizeiligen Saetze (frage, gegruendet) angenommen, die NAHT warf: Sprache.eineZeile nimmt genau eine Zeile. Gefunden von der Befehlsprobe, nicht von der Tafel - genau der Unterschied, den "Bildtafel ist keine Nahtprobe" meint.
- Proben 2839 -> 2921. Neu: FreistadtbuchTest (13), StadtnameTest (8), FreistadtwerteTest (8), FreistadtBefehlTest (9), 7 Vertragsproben x3, dazu die Bindungs- und Riegelproben.

### Spielprobe 9.11.2026 - vier Fehler, die KEINE Probe sah

- ~~Der WELTSCHLUESSEL ohne Namensraum.~~ Der schwerste Fund. Die config.yml schreibt NAMEN (laeden.welt: altfurt), die Welt traegt SCHLUESSEL (World.key().asString() = freimarken:altfurt). DREI Stellen verglichen das eine mit dem anderen und bekamen immer "ungleich": /laden mieten sagte "Die Markthalle steht in Altfurt - dort wird gemietet" MITTEN IN ALTFURT, der Ladenschutz hielt sich ueberall fuer nicht zustaendig (in jedem gemieteten Laden durfte jeder bauen), und Ladenware fand die Ladenwelt nie (der taegliche Lauf raeumte keine Truhe). Nur die erste war sichtbar; die anderen zwei schwiegen.
- Warum keine Probe es sah: Jede Probe dieser Klassen gibt den Schluessel schon FERTIG herein. Sie prueft damit eine andere Eingabe als die Wirklichkeit - die Naht zwischen Datei und Welt liegt davor, in FreimarkenPlugin, und das ist ohne Server nicht zu betreten. MarktBefehle machte es seit jeher richtig, aber VON HAND ("freimarken:" + name); genau diese Handarbeit wird an der naechsten Stelle vergessen. Jetzt: weltschluessel(name) an einer Stelle, GrenzeTest 31ad haelt es.
- ~~{tage} trug ZWEI Begriffe.~~ Im Spiel standen nebeneinander "Der Vertrag laeuft bis 15.11.2026 - 7 Tage" und "Dein Laden: bis 15.11.2026 (6 Tage)". Beide Zahlen waren RICHTIG und zaehlten Verschiedenes: Vertragslaenge gegen Restlaufzeit. Von vier Saetzen sagte nur einer, welche er meint. Jetzt heissen die Platzhalter {miettage} und {resttage}, und die Worte sagen es mit ("laeuft 7 Tage, bis zum" / "noch 6 Tage").
- Der schiefste war nicht der gemeldete: "ist verlaengert - bis X, DAS SIND 13 Tage" nannte die Restlaufzeit und klang nach Laenge. Wer frueh verlaengert, las nach einer Verlaengerung um 7 die Zahl 13.
- ~~pruefeLadeneinheiten wurde nicht gerufen.~~ Die Methode war fertig gebaut, mit Javadoc und Klagetexten - und niemand rief sie. Kein Probenlauf meldet das; die Methode ist richtig, sie ist nur tot. GrenzeTest 31ab haelt jetzt, dass jede pruefeX-Methode des Plugins auch gerufen wird; die Namen holt die Probe selbst per Regex, eine handgeschriebene Liste bliebe beim naechsten Zuwachs stehen.
- ~~bauplan.seed() war unbewacht.~~ Einspritzung S6 nahm die Zeile heraus: alle 2.418 Proben blieben gruen. Dann waere welten.<name>.startwert eine Zahl, die nie wirkt - gelesen, auf Doppelte geprueft und wirkungslos. GrenzeTest 31ac haelt es.
- Der Sprungschutz hat sauber gearbeitet und mich belehrt: laeden.lizenz von 50.000 auf 1.000 ist mehr als Faktor 2 und braucht wirtschaft.sprung-bestaetigt. Der Startbericht sagte es wortwoertlich samt Loesungszeile - ich hatte ihn nur nach "Ladenmiete" gefiltert statt Satz fuer Satz gelesen.
- Im Spiel BESTAETIGT: /warp, /laden (Liste), /laden mieten (Abschluss 6.000 = 1.000 Lizenz + 5.000 Kaution), die Sperre gegen einen zweiten Laden, /laden verlaengern (15.11. -> 22.11.), /mark verlauf mit allen Buchungen in richtiger Reihenfolge, Tagesbonus 500, Verdienst aus Arbeit, Verkauf an den Bundesmarkt.
- Offen in der Spielprobe: Kuendigen samt Kautionsrueckgabe, der Ladenschutz in der Luecke zwischen stand-1 und stand-2, die Kistenshops samt Handel und Kistenschutz, der taegliche Lauf (braucht einen Tageswechsel).
- ~~Offen und benannt: Es gibt KEINEN Betreiberbefehl fuer Geld.~~ 20.11. GEBAUT als /freimarken geld mit eigenem Recht und zwei Buchungsgruenden, siehe Nachlese zu Schritt 12. Vorher: Fuer Proben und fuer den Ernstfall (ein Spieler verliert Mark durch einen Fehler) geht es heute nur ueber die Datenbank oder ueber eine Einstellung. Ein /freimarken geld <spieler> <betrag> mit eigenem Buchungsgrund waere der Weg.
- 0 ist ein GUELTIGER Startwert, darum ist das Feld ein Long und kein long: Bei der Kante heisst 0 "keine Grenze", hier waere dasselbe ein Gelaende, das niemand mehr einstellen kann.
- Ein WORT als Startwert wird beklagt und nicht umgerechnet. Minecraft nimmt im Menue Woerter und rechnet sie selbst in eine Zahl um; das nachzubauen waere eine Abschrift des Spiels - weicht sie eines Tages ab, entsteht aus demselben Wort ein anderes Gelaende, und jeder eingemessene Quader liegt daneben.
- Zwei Welten mit gleichem Startwert UND gleicher Umgebung werden beklagt (dasselbe Gelaende, Block fuer Block - zwei Namen fuer eine Landschaft). Gleicher Wert in ANDERER Umgebung ist keine Klage: Oberwelt und Nether aus derselben Zahl sind in Vanilla der Normalfall.
- ~~Fuer die Ladeneinheiten fehlt die Startpruefung ganz.~~ 9.11. GEBAUT: pruefeLadeneinheiten, nach dem Vorbild von pruefeTore. Die harte Bedingung ist die TRUHE - ohne sie zahlt ein Mieter Miete fuer einen Stand, in den er nichts legen kann, und die Klage kam bisher erst beim taeglichen Lauf, also fruehestens einen Tag nach dem ersten Mieter. Gefragt wird ueber Ladenware.inventarAn, dieselbe Stelle, die der Lauf fragt.
- Einspritzung 9.11. (Startwert): 9 Faelle, 9 gefangen - aber EINER erst nach einer Nachbesserung. S6 nahm bauplan.seed(...) aus Weltenbau: alle 2.418 Proben blieben gruen. Weltenbau braucht einen laufenden Server und hat keine Verhaltensprobe. Dann waere der Schluessel in der config.yml eine Zahl, die nie wirkt - gelesen, beklagt und wirkungslos. Dagegen jetzt GrenzeTest 31ac.
- Dabei ein eigener Fehler, der aufgeschrieben gehoert: pruefeLadeneinheiten war fertig gebaut, mit Javadoc und Klagetexten - und NIEMAND rief sie. Kein Probenlauf meldet das; die Methode ist richtig, sie ist nur tot. Dagegen jetzt GrenzeTest 31ab: Fuer jede pruefeX-Methode des Plugins muss der Quelltext mindestens einen Aufruf zeigen, und die Namen holt die Probe selbst aus dem Text - eine handgeschriebene Liste bliebe beim naechsten Zuwachs stehen.
- Falscher Verdacht, gemessen und verworfen: Ladenware nutzte getState() ohne false und schrieb dann ins Inventar - das sah nach der Schnappschussfalle aus. Die Doku von Shopware widerlegt es: getInventory() liefert auch auf einem Schnappschuss das LEBENDE Inventar, nur getSnapshotInventory() waere die Kopie. Das Raeumen wirkte immer; es kostete nur eine unnoetige Kopie, und die ist beim Zusammenlegen auf Ladenware.inventarAn weg.
- Nicht pruefbar und darum per Quelltexttafel gehalten: dass seed() beim Anlegen wirkt, dass die Abweichungsklage zwischen Datei und bestehender Welt kommt, und dass die Startpruefung der Einheiten gerufen wird. Alle drei liegen in Klassen, die ohne Server nicht zu betreten sind.
- Pruefen im Spiel, ein Befehl je Stand: /tp 62 73 8 - /tp 62 73 18 - /tp 82 73 18. Steht man in Luft ueber dem Boden, liegt der Quader richtig; steht man IM Boden, muss der Stand um eins nach oben, und schwebt man zwei hoch, um eins nach unten.
- ~~Schritt 8, Teil A: Ladeneinheiten.~~ 7.11.: laeden.welt und laeden.einheiten (Quader + Truhe je Stand), /laden zeigt sie. Hoechstens 4.096 Bloecke je Einheit; Ueberschneidung verwirft BEIDE.
- ~~Schritt 8, Teil B: Mietvertrag.~~ 8.11.: /laden mieten <name>, verlaengern, kuendigen. Schemaschritt 13 (Tabelle ladenmiete), vier neue Buchungsgruende.
- Der Vertrag traegt Miete UND Kaution eingefroren (Plan 1943, Bestandsschutz): Eine Erhoehung trifft nur NEUE Vertraege, auch ueber eine Verlaengerung hinweg.
- Ein Laden je Spieler (Plan 7.1) wacht die ABLAGE, nicht der Befehl: uq_ladenmiete_mieter. Ein Befehl, der erst fragt und dann schreibt, hat dazwischen eine Luecke.
- Die Lizenz ist einmalig, und der BELEG dafuer ist das Buchungsbuch (Grund laden_lizenz) - keine Spalte. Eine zweite Wahrheit liesse sie ein zweites Mal zahlen.
- Scheitert ein Abschluss nach dem Abbuchen, kommt nur die KAUTION zurueck. Die Lizenz bleibt bezahlt - sie haengt an keinem Laden.
- Verlaengern kostet nichts (gezahlt wird taeglich) und geht, sobald weniger als eine Mietdauer uebrig ist. Damit liegt das Ende nie weiter als 59 Tage voraus.
- Nur das MIETEN haengt an Altfurt; verlaengern und kuendigen gehen von ueberall - sie beruehren nur den eigenen Vertrag.
- ~~Schritt 8, Teil C: taeglicher Lauf und Abholfach.~~ 8.11.: Miete wird taeglich abgebucht, auch wenn der Mieter offline ist; 3 Ausfaelle in Folge raeumen (laeden.ausfaelle-bis-raeumung). Schemaschritt 14 (Tabelle abholfach), Buchungsgrund laden_miete, /laden abholfach.
- ~~Spalte fuer Warnungen~~ 8.11. heisst ausfaelle (Tage ohne Zahlung IN FOLGE, zurueck auf 0 bei Zahlung). Sie steht am VERTRAG und nicht im Buchungsbuch - aus dem Buch gerechnet raeumte ein Serverausfall von fuenf Tagen die ganze Halle.
- Mietdauer 8.11. von 30 auf 7 Tage GEAENDERT. Kein Preis: Die Miete steht je Tag, Kaution und Lizenz bleiben. Kurz, damit ein verlassener Stand nach einer Woche wieder mietbar ist. Schranke beim Verlaengern damit 13 Tage.
- Kaution 8.11. ENTSCHIEDEN: Beim Ablauf ohne Verlaengerung kommt sie zurueck (er hat jeden Tag gezahlt), nur beim Raeumen wegen Zahlungsausfall verfaellt sie.
- Reihenfolge der Raeumung: Truhe lesen, ins Fach legen, Vertrag loeschen, Truhe leeren. Scheitert das Einlegen, bleibt ALLES stehen - darum prueft raeumungsreif mit >= und nicht ==.
- Das Abholfach wird mit einem BEFEHL geleert und nicht in einem Fenster: Ein offenes Fenster ist ein Zustand zwischen Spieler und Datenbank, und wer es im falschen Augenblick schliesst, haette die Ware zweimal.
- jedes Loeschen im Fach fragt nach empfaenger UND id: Der AUTO_INCREMENT von MariaDB beginnt nach einem Neustart bei MAX(id)+1, eine Nummer kann also ein zweites Mal vergeben werden.
- ~~Es gibt KEINEN Ladenschutz.~~ 8.11. GEBAUT: Mietregel.darfInDerEinheit als Regel, Ladenschutz als Schutzwache.Ortsregel - also als zweite Frage an darfHandeln und nicht als zweite Wache. Damit gelten alle 15 Ereignisarten der Wache auch im Laden (Bloecke, Eimer, Feuer, Bilderrahmen, Kisten, Tuerklinken).
- Ein FREIER Stand ist fuer JEDEN gesperrt, nicht fuer jeden offen. Der Quader ist Einrichtung des Betreibers; was ein Spieler dort hinterlaesst, hinterlaesst er dem naechsten Mieter. Das traegt zugleich die Raeumungsreihenfolge: An eine Truhe ohne Vertrag kommt niemand heran.
- Das Teamrecht freimarken.bauen.ueberall gilt AUCH im Laden - sonst muesste ein Betreiber kuendigen, um aufzuraeumen.
- Das ERLAUBT der Ortsregel ist in ihrer Welt endgueltig: Steht die Markthalle in der Marktstadt (laeden.welt erlaubt das), liegt eine Einheit auf einer Parzelle. Dann gilt die Einheit.
- Drei eigene Handbalken: fremder Laden (mit Stand UND Mieter), freier Stand (gelb, mit /laden mieten darin), Datenbank stumm. Letzterer eigens, weil "frei" dann falsch und "gehoert X" erfunden waere.
- ~~Die HANDELSTUER (Schutzwache.Handelsklick) steht schon da, obwohl es keine Kistenshops gibt.~~ 8.11.: echt, und in BEIDEN Klickzweigen - Lumias Verkauf ist ein Linksklick.
- Das !werkzeugHeikel ist AUS der Tuer heraus: aendertBlock sagt bei jedem Block in der Hand ja, und wer Stein verkaufen will, haelt Stein. Die Tuer waere fuer die haeufigste Ware zu geblieben. Der Schutz liegt jetzt in der WIRKUNG - Shopklick setzt nach einem Shopklick beide Ergebnisse auf DENY, also handelt er und die Hand bleibt untaetig.
- Folge davon: Das Schild eines FREMDEN Shops ist per Linksklick nicht mehr zu brechen, auch nicht mit dem Teamrecht. Abgebaut wird es ueber die Kiste oder den Traegerblock - Shopwache kennt beide Wege und baut das Schild mit ab.
- ~~Kistenshops, Spielschicht.~~ 8.11.: Anlegen am SignChangeEvent, Schutz von Schild, Kiste und TRAEGERBLOCK, der Trichter. Neun Ereignisse, Abschnitt handel: in der config.yml, Recht freimarken.shop.bund, 24 Saetze unter shop:.
- Das Schild wird GEWACHST (setWaxed) - seit 1.20 darf sonst jeder ein Schild bearbeiten, also auch fremde Preise aendern. Wachs ist Vanilla und wirkt ohne dieses Plugin; der zweite Riegel ist, dass die Wache jedes Beschreiben einer Stelle mit Shop abweist, gleich von wem.
- Der TRAEGERBLOCK ist mitgeschuetzt, statt das herabgefallene Schild nachzufassen (so macht es ChestShop). Weniger Code und kein Zeitfenster: Was nicht wegkann, laesst nichts fallen.
- Der Trichter ist UNSYMMETRISCH: nur die Quelle wird geprueft. Kein Trichter zieht aus einer Shopkiste, auch nicht der des Besitzers; Hineinlegen bleibt erlaubt - so wird ein Laden beliefert.
- BEIDE Haelften einer Doppelkiste sind geschuetzt, auch die ohne Schild: Sie teilen ein Inventar, und wer die freie Haelfte abbaut, bekommt deren Haelfte der Ware. Inventory.getLocation gibt dort die MITTE zwischen den Haelften, also eine Stelle ohne Block - der Weg geht ueber getHolder(false).
- Gefragt wird nach der KISTENSTELLE und nicht nach dem Schild: Schild auf eigenem Grund, Kiste einen Block weiter auf fremdem - sonst verkauft der Shop die Ware eines anderen. Kein Vorbild schliesst das selbst.
- Vor dem Lesen steht istGemeint (B oder S mit etwas dahinter in Zeile 3). Ohne diese Vorfrage bekaeme jedes Wegweiserschild die Absage "Zeile 2 ist keine Zahl".
- Einspritzung 8.11.: 24 Faelle in fuenf Gruppen, 22 gefangen; 2 brauchten eine Nachspritzung (ein Anker sass hinter der Pruefung, zwei Faelle ueberschatteten sich). Drei Nachspritzungen fanden echte Luecken: die Laengenpruefung des Bundworts im SATZ, die Zuordnung shopAnlage(ANGELEGT) und die Hilfe in der Sprachdatei, die B 100 hart nannte. Alle drei geschlossen und nachgeprueft.
- Nicht im Spiel geprueft (alles mit Bukkit): Shopware (Gegenstand zu Text), die neun Ereignisse der Shopwache, das Wachsen, die Doppelkiste, der Trichter. Haengt an ShopwacheTafel - das sind Quelltexttafeln und keine Nahtproben.
- ~~Beim VERMIETEN die Truhe leeren.~~ 10.11. GEBAUT: Mietbuch bekommt dieselbe Naht (Mietlauf.Truhe) wie der taegliche Lauf und leert die Truhe der frisch vermieteten Einheit - EINE Ladenware fuer beide, nicht zwei Zugaenge zur selben Kiste. GELESEN wird vorher: leer schweigt, voll gibt eine Warnung mit Einheit und Zahl (eine Kopie, die so still verschwindet, wie sie entstanden ist, waere ein Vorgang, den niemand nachweisen kann).
- Die Reihenfolge ist die Zusage: erst der Vertrag, dann die Truhe, und das Leeren in einem EIGENEN Fang. Der Mieter hat bezahlt - eine Truhe, die sich nicht leeren laesst (Welt nicht geladen, keine Kiste mehr da), darf ihm den Laden nicht wieder wegnehmen. Vier Proben, Einspritzungsrunde 4/4 gefangen. Proben 2789 -> 2793.
- Die Truhen-Attrappe der Proben liegt jetzt in EINER Datei (Probentruhe) statt in MietlaufTest: Vier Probenklassen brauchen sie, und vier Abschriften laufen auseinander.
- BEFUND 10.11.: Schritt 8 ist im CODE fertig. Teil E (der einfache Laden) sind die Kistenshops vom 8.-10.11. (Anlegen am Schild, Schutz, Handel, Schluessel, /shop liste), Teil F (Marktgebuehr) steht als handel.<lage> in der config.yml und wird in Shophandel.bezahle als SHOP_GEBUEHR gebucht. Offen sind nur noch Handarbeit (Markthalle bauen und einmessen), die Spieltests und die kleinen Punkte unten.
- ~~Shopschild an einer Freien Truhe.~~ 11.11. GEBAUT: pruefeFreieTruhen am Ende von richteShopsEin klagt je Stelle mit Schildkoordinate und sagt, ob es ein Bundschild ist. Ein Schild dort verkauft die Gemeinschaftsware - kein Spielerloch, aber eine Falle fuer den Betreiber.
- Zwei Entscheidungen daran: Gefragt wird mit anDerKiste und NICHT mit istShopkiste (jene gibt bei stummer Datenbank eine leere Liste, diese ein true - sonst klagte ein Datenbankausfall ueber JEDE Stelle, und eine Klage ueber nichts nimmt der Leser beim naechsten Mal nicht mehr ernst). Und die Stellen werden als Feld gehalten, weil zwischen richteTruheEin und richteShopsEin zwei Startschritte liegen.
- Drei Quelltext-Tafeln halten es, weil der Start des Plugins keinen Lauf hat: 31ab (wird gerufen), 31ao (anDerKiste, nicht istShopkiste), 31ap (das Feld wird gefuellt). Einspritzungsrunde 3/3 gefangen, jeder Fall von genau seiner Probe. Proben 2793 -> 2795.
- ~~/markt liste wirft bei stummer Datenbank.~~ War beim Nachsehen am 11.11. schon erledigt: zeigeListe hat den Fang samt MarktSatz.GESTOERT, und Markttag.heuteSchon steht mit darin. Die Zeile hier war veraltet.
- DAMIT IST SCHRITT 8 IM CODE FERTIG (A bis F).
- ~~Truhenschutz fuer Altfurt.~~ 11.11. GEBAUT, und NICHT auf Quader-Grundlage: Eine Quaderliste waere eine Liste von Loechern. Die Einrichtungswache sperrt am InventoryOpenEvent JEDEN Behaelter der Ladenwelt - ausser der Freien Truhe (dort entscheidet die Truhenwache), den Kistenshops (die Shopwache) und den gemieteten Staenden (die kommen gar nicht an: Den Klick bricht schon der Ladenschutz ueber die Schutzwache ab). Das Teamrecht freimarken.bauen.ueberall oeffnet alles - dasselbe Recht wie beim Bauen, kein zweites daneben.
- Am InventoryOpenEvent und NICHT als zweite Ortsregel der Schutzwache: Jene entscheidet ueber fuenfzehn Ereignisarten an einem ORT, hier geht es um eine Tat an einem BEHAELTER. Tueren, Hebel und Knoepfe einer Stadt soll jeder benutzen duerfen, und eine Ortsregel koennte das nicht trennen - sie sieht nur Orte. Dieselbe Mechanik, mit der die Shopwache ihre Kisten schuetzt.
- Ein Inventar OHNE Kiste (Rucksack, Werkbank, Braustand, Endlager) gibt bei Shopware.bloeckeVon eine leere Liste und wird darum nie gesperrt. Ohne diesen Riegel waere in Altfurt jedes Inventar zu - auch das eigene. Beim Durchdenken der Einspritzungsfaelle aufgefallen, nicht beim Bauen.
- Die Kistenattrappe der Proben liegt jetzt in EINER Datei (Kistenprobe), weil zwei Wachen am InventoryOpenEvent pruefen. Eine Doppelkiste laesst sich damit nicht nachbauen: DoubleChest ist in Bukkit eine KLASSE, und ein Stellvertreter kann nur Schnittstellen tragen - was bei zwei Haelften gilt, steht als Satz an den Wachen.
- Einspritzungsrunde 7/7 gefangen, jeder Fall von der Probe, deren Name ihn nennt. Vier festgenagelte Tafeln wurden dabei rot und mussten nachgezogen werden (BildGleichTest 11b, GrenzeTest 31b, 31c, 31u) - genau ihr Zweck. Proben 2795 -> 2806.
- Offen bleiben: die Markthalle in Altfurt von Hand bauen und einmessen, die Spieltests, der Truhenlauf-Merker (ein Merker je Stelle) und die Besitzermeldung beim Kauf (bewusst nach dem Spieltest).
- Pruefen beim Spieltest: eine gewoehnliche Kiste in Altfurt bleibt zu und meldet "Diese Kiste gehoert zur Stadt" im Handbalken; die Freie Truhe geht auf; ein eigener Kistenshop geht fuer seinen Besitzer auf; der Rucksack und eine Werkbank gehen auf; mit freimarken.bauen.ueberall geht alles auf.
- Offen und ausdruecklich auf NACH dem Spieltest verschoben: eine Meldung an den Shopbesitzer, wenn jemand bei ihm kauft. Ob das bei zwanzig Kunden noch gewollt ist, zeigt erst der Betrieb.
- 8.11. ENTSCHIEDEN, drei Saetze, die zusammen gelten: HANDELN darf jeder, immer und ueberall (kein Rang, kein Recht, kein Ort). Verkauft wird standardmaessig ueber KISTENSHOPS - Schild an einer Kiste. Im Laden darf nur der MIETER bauen, bearbeiten und Kisten befuellen oder leeren.
- Daraus folgt die Trennlinie des Ladenschutzes: Am SCHILD handeln darf jeder, auch im fremden Laden; die KISTE oeffnen nur der Mieter. Ein Schutz, der pauschal nichts in fremden Einheiten zulaesst, macht Kistenshops unmoeglich - und das faellt erst auf, wenn der erste Kunde nichts kaufen kann.
- Der Schutz braucht darum von Anfang an eine BENANNTE Luecke fuer den Handelsklick, die der Kistenshop-Schritt fuellt. Eine Luecke, die erst spaeter eingebaut wird, bricht den Schutz an einer Stelle auf, die dann niemand mehr ueberblickt.
- ~~Der HANDEL: kaufen und verkaufen am Schild, Sneak fuer den ganzen Stapel, die drei Buchungen.~~ 8.11. gebaut.
- Die Reihenfolge ist in BEIDEN Richtungen dieselbe: Zahler abbuchen (shop_gekauft), Ware von der Geberseite nehmen, Ware auf die Nehmerseite legen, Empfaenger gutschreiben (shop_verkauft), Gebuehr vom Empfaenger (shop_gebuehr). Beim Kauf ist der Zahler der Kunde und die Geberseite die Kiste, beim Verkauf umgekehrt - EINE Abfolge und nicht zwei, darum gibt es Shophandel.Seite.
- shop_verkauft traegt den VOLLEN Preis und nicht den Erloes [berichtigt 8.11.]. Vorher sagte der Javadoc "nach Abzug der Gebuehr" UND shop_gebuehr "eine eigene Zeile" - zusammen zoegen sie die Gebuehr zweimal ab (1000 Mark, 7 Prozent: 860 statt 930). Die Summenprobe der Kasse faengt das nicht, denn sie gilt je Konto. Ein drittes Konto fuer den Staat gibt es nicht und soll es nicht geben.
- Die Gebuehrzeile faellt nur, wenn die GERECHNETE Gebuehr ueber 0 liegt (Shopregel.Handel.kostetGebuehr) - nicht schon, wenn ein Satz ueber 0 steht: 5 Prozent von 19 Mark sind abgerundet 0.
- shop_erstattet ist NEU: Der Zahler wird zuerst abgebucht, also braucht der Fall "gezahlt und nichts bekommen" eine Gegenbuchung - dieselbe Antwort wie bei Parzelle, Beruf und Laden. Nur die ZAHLUNG wird gedreht, nie die Gutschrift: Was nach ihr fehlt, wird NACHgebucht und nicht gegengebucht.
- Je LAGE ein Gebuehrensatz [entschieden 8.11.]: marktgebuehr-spielershop-kauf-, -verkauf- und marktgebuehr-bundshop-verkauf-prozent. Auch WANN eine Gebuehr faellt, ist eine Einstellung. Die vierte Lage - Kauf am Bundshop - hat absichtlich keinen Schluessel: Dort bekommt niemand Geld, dem sie abzuziehen waere. Die zwei alten Zeilen werden beklagt, nicht uebergangen.
- Am EIGENEN Shop wird nicht gehandelt [entschieden 8.11.]. Der wichtigere Grund ist nicht das Geld an sich selbst, sondern der Linksklick: Er ist der Anfang eines Abbaus, und am eigenen Schild verbietet der Schutz nichts. Darum wird der Klick dort auch nicht verbraucht - sein Schild bleibt abbaubar.
- Die Ware kommt aus dem ganzen RUCKSACK [entschieden 8.11.], nicht nur aus der Hand: Sonst waere der Sneak-Klick auf einen Stapel begrenzt, egal wie viel der Spieler traegt. Nur Lagerplaetze - getStorageContents und nicht getContents, denn removeItem bedient die zweite Hand nicht. Mit getContents gezaehlt waere Ware dort vorhanden und nicht entnehmbar: Geld aus nichts.
- Platz und Vorrat in STUECK und nicht in Plaetzen (Shopregel.platzInStueck / vorratInStueck). Ladenware.freiePlaetze zaehlt SLOTS - damit bekaeme ein Shop mit Menge 64 immer "kein Platz". Und angefangene Stapel zaehlen MIT: Wer Erde kauft, traegt meistens schon Erde, und 35 volle plus ein halber Platz nehmen 32 Stueck auf.
- Die Stapelgroesse kommt GEMESSEN vom Gegenstand (Shopregel.Stufung). Die feste 64 galt nur fuer gewoehnliche Ware - Enderperlen stapeln zu 16, Werkzeug und jedes verzauberte Stueck zu 1, und verzaubert ist seit dem Fragezeichen der Normalfall. Ein Sneak-Klick an einem Spitzhacken-Shop forderte sonst 64 Stufen an.
- Nur die HAUPTHAND handelt: Ein Klick kommt bis zu zweimal, einmal je Hand. Sicher erreichbar ist das beim SNEAK-Klick, also genau bei der Geste fuer den ganzen Stapel - dort waere die Doppelbuchung am teuersten.
- Einspritzung 8.11. (Handel): 35 Faelle, 31 gefangen. DREI echte Luecken, alle geschlossen und nachgeprueft: (1) Die Rangfolge der drei Maengel (Vorrat, Platz, Geld) liess sich vertauschen, weil die Tafel jede Lage EINZELN fuhr. (2) Die Erstattung liess sich auf den Erloes umbiegen - alle Erstattungsproben liefen mit Gebuehr 0, und dort ist erloes gleich preis: Der Unterschied war nicht der gepruefte. (3) Die Kassen-Mitschrift der Probe schrieb erst NACH dem Aufruf mit, also war ein Aufruf, der WIRFT, unsichtbar - damit blieb unbemerkt, wenn der Handel fuer den Bund gutschreiben(null) versucht. Der vierte ungefangene Fall ist nicht messbar (in Vanilla stapeln alle Behaelter gleich) und haengt an einer Tafel.
- Nicht im Spiel geprueft (alles mit Bukkit): dass ein Klick wirklich zweimal kommt, dass DENY den Abbau verhindert, dass addItem und removeItem sich so verhalten wie zugesagt, die Doppelkiste beim Zaehlen. Dafuer stehen Quelltexttafeln in ShopwacheTafel - die pruefen Text und nicht Wirkung.
- Im SPIELTEST zu messen, welche Plaetze removeItem beim Spieler anfasst: Wir zaehlen nur die Lagerplaetze, also verspricht die Zahl nie mehr als sie - die Richtung ist sicher. Offen ist nur, ob der Shop manchmal WENIGER annimmt, als ein Spieler traegt (Ware in der zweiten Hand).
- Und im Spieltest: dreimal am eigenen Schild verkaufen und das Schild zaehlen (es muss stehen bleiben, denn dort wird nicht gehandelt und der Klick nicht verbraucht), einmal mit offener Kiste kaufen und hinsehen, und mit einem Stapel Stein in der Hand im fremden Laden kaufen.
- Offen beim Handel: eine Meldung an den SHOPBESITZER, wenn jemand bei ihm kauft. Heute sieht er es nur unter /mark verlauf. ChestShop schickt eine Zeile; ob das bei zwanzig Kunden noch gewollt ist, zeigt der Spieltest.
- Offen und benannt: Ein BUNDSHOP mit Verkaufspreis ist eine Geldquelle OHNE Tagesdeckel - der Deckel des Bundesmarkts rechnet ueber bundesmarkt_verkauf, der Shop bucht shop_verkauft. Keine Spielerluecke (es braucht freimarken.shop.bund), aber eine Falle fuer den Betreiber. Gehoert mit dem Bundesmarkt-Schild zusammen entschieden: Sind die zwei dasselbe Ding, loest es sich dort auf.
- ~~Eine Shopkiste ist nicht gegen eine HAND geschuetzt.~~ 8.11. GEBAUT: Shopwache.beimOeffnen am InventoryOpenEvent, elfter Zuhoerer. In der Marktstadt und in der Markthalle greift schon der Klickzweig der Schutzwache, also kommt das Ereignis dort nicht an - keine zwei Handbalken aus zwei Drosseln.
- Dabei ein ZWEITES Loch gefunden und mitgeschlossen: Wer die Kiste nicht oeffnen kann, haengt sonst einfach ein eigenes Schild daneben und verkauft die fremde Ware auf sein Konto - mit dem Fragezeichen sogar automatisch die, die drin liegt. Shopbuch.anlege prueft nur anDemSchild, also das Schild, nie die KISTE. Eine Tuer bauen und das Fenster offen lassen waere sinnlos gewesen; neuer Bescheid FREMDER_SHOP.
- Entschieden 8.11.: Das Oeffnen wird GANZ verweigert, nicht nur das Herausnehmen. Das Nehmen hat nicht einen Weg, sondern zehn (Links-, Rechts-, Umschaltklick, Ziehen, Zahlentasten, zweite Hand, Doppelklick, Wurf), und jede vergessene Art ist ein Loch. Folge: Ein Fremder kann einem Shop auch nichts schenken. Dafuer kommt der SCHLUESSEL als eigener Schritt - /shop freigeben <spieler>, eine Mitbenutzerliste je Shop.
- Die Zutrittsregel steht jetzt an EINER Stelle: Kistenshop.offenFuer(wer, teamrecht, bundrecht), drei Stufen, im Kern und ohne Server pruefbar. Sie traegt DREI Wege - Abbauen, Oeffnen, zweites Schild. Dort haengt spaeter der Schluessel ein; ein vierter Fall in der Methode gilt dann sofort fuer alle drei. ShopwacheTafel haelt, dass niemand sie nachbaut (die Wache darf demBund() und gehoert( nicht mehr selbst fragen).
- Der Oeffnen-Zweig fragt BEIDE Richtungen der Ablage: istShopkiste gibt bei stummer Datenbank true (schuetze sie), anDerKiste eine leere Liste (erfinde keinen Besitzer). Nur die zweite zu fragen ist die naheliegende Vereinfachung - und sie schliesst bei schweigender Datenbank jede Shopkiste der Welt AUF, denn eine leere Liste heisst "niemand fremd". Dafuer ein fuenfter Schutzsatz shop.schutz.nicht-lesbar; "der Shop von -" sah aus wie ein Programmfehler.
- NICHT betroffen() fuer das Oeffnen: Jene Methode faellt bis zum TRAEGER-Zweig durch, und dann waere eine Kiste gesperrt, die bloss ein fremdes Schild TRAEGT, obwohl keine Shopware drin liegt. Beim Abbauen ist der Traeger richtig, beim Oeffnen zaehlt nur, wessen Ware drin liegt.
- Die Reihenfolge in anlege ist jetzt die der LAGE und nicht die der Kosten: erst das Schild (wer sein eigenes ueberschreibt, soll "hier haengt schon ein Shop" lesen), dann die Kiste, dann die WARE. Sonst liest das Fragezeichen die fremde Kiste, und bei einem ausverkauften fremden Shop bekaeme er "die Kiste sagt nicht, was du handeln willst" - dann wartet er auf Ware, wo kein Warten hilft.
- Shopwache hat jetzt eine echte Verhaltensprobe (ShopwacheTest, 10 Proben): das Ereignis wird WIRKLICH gebaut und der Zweig durchlaufen. Gemessen dabei: InventoryView ist eine Schnittstelle und der Ereigniskonstruktor fragt sie nicht an, aber DoubleChest ist eine KLASSE - darauf geht kein Proxy. Der Weg ist eine echte DoubleChest um ein DoubleChestInventory, und das ist wieder eine Schnittstelle. Eine Kistenhaelfte muss BlockState UND InventoryHolder tragen, sonst wirft getHolder beim Umwandeln.
- Einspritzung 8.11. (Kistenschutz): 16 Faelle, 16 gefangen, jeder von einer namentlich genannten Probe. Zwei Lehren: E13 uebersetzte erst nicht - das war ein Fehler der MESSUNG und kein Fang, erst die zweite Fassung belegt die Reihenfolge. Und bei E15 (Team- und Bundrecht vertauscht) blieb die Bundshopprobe gruen, weil teamrecht und bundrecht dort BEIDE oeffnen - der Unterschied war nicht der gepruefte; gefangen hat es die Spielershopprobe.
- Nicht im Spiel geprueft: dass Bukkit fuer eine Shopkiste wirklich ein InventoryOpenEvent feuert, dass der Halter einer Doppelkiste dort als DoubleChest ankommt, und dass ein abgesagtes Oeffnen den Deckel wirklich zulaesst.
- ~~Eine fremde Shopkiste laesst sich per TRICHTER mit Muell fuellen.~~ 9.11. GEBAUT: Der Zweig prueft jetzt auch das ZIEL, aber nicht pauschal - nur die WARE des Shops kommt hinein (Shopware.istWareVon ueber die eine isSimilar-Stelle).
- Entschieden 9.11.: Warenpruefung und NICHT pauschale Sperre. Eine pauschale Sperre waere eine Zeile gewesen und haette den automatischen Shop mitgenommen - Farm, Trichter, Shopkiste -, auch den eigenen, denn ein Trichter fragt nicht, wem er gehoert. Benannte Restluecke: Wer die ECHTE Ware verschenkt, kann die Kiste trotzdem fuellen; das kostet ihn mehr als den Besitzer, der sie nur ausraeumt.
- EINES der Shops an der Kiste genuegt, nicht der erste: An einer Doppelkiste koennen zwei Schilder mit zwei Waren haengen, und wer nur den ersten fragt, sperrt dem zweiten die Belieferung.
- Einspritzung 9.11. (Trichter): 6 Faelle. T1 und T6 haben einen DOPPELTEN RIEGEL aufgedeckt: ziel.gestoert() und die leere Shopliste in istWareVon deckten sich gegenseitig - jeder allein entfernt aenderte NICHTS, also hielt keine Probe mehr, welcher traegt. Der zweite Riegel ist heraus; jetzt faengt die Verhaltensprobe den Fall. Ein Riegel mit Probe ist mehr wert als zwei ohne.
- Gemessen und aufgeschrieben: Den TAUSCH von Quelle und Ziel faengt keine Verhaltensprobe. Solange keine Warenprobe aufloesbar ist (isSimilar gibt es ohne Server nicht), sagt der Zweig in jeder Lage mit einer Shopkiste ab - gleich auf welcher Seite sie steht, der Dreher ist verhaltensgleich. Das haelt allein die Quelltexttafel, und der alte Kommentar dort hatte recht.
- Spieltest, Trichter: dass PASSENDE Ware per Trichter hineinkommt (die positive Zusage - sie ist ohne Server nicht pruefbar), dass Muell abgewiesen wird, und dass eine Sortieranlage an einer gewoehnlichen Kiste weiterlaeuft.
- Gedeckt, obwohl es nach Luecke aussah: Das Schild UMSCHREIBEN scheitert am Wachs und an SCHON_EIN_SHOP. Das Zeitfenster beim SERVERSTART ist zu, weil die Wache erst NACH dem Fuellen des Zwischenspeichers angemeldet wird - dafuer gibt es schon eine Tafel.
- ~~Ein BLOCKBUCH fuer die Moderation - wer wann wo was getan hat.~~ 9.11. GEBAUT: Tabelle blockbuch (Schemaschritt 16), Blockwache an drei Ereignissen, /freimarken blockbuch auf den Block im Blick plus nach Spieler, Aufraeumen am Spielzeittakt.
- Entschieden 9.11.: WEISSLISTEN statt true/false, je Welt eine eigene. Das Projekt kennt kein getBoolean (gemessen) - Bukkit liest jedes Wort ausser true stumm als false, aus "benutzen: ja" waere ein abgeschaltetes Buch ohne eine Zeile Klage geworden.
- "abgewiesen" ist KEINE Tat, sondern ihr Ausgang: Eine vierte Konstante koennte nicht mehr sagen, WAS abgewiesen wurde. Die Zeile traegt beides; das Wort wirkt quer zu allen Taten und schaltet keine zu, die der Betreiber abgewaehlt hat.
- benutzen braucht eine eigene Blockliste (blockbuch.benutzbare-bloecke, 15 Behaelter ab Werk). Material.isInteractable() ist VERALTET und haelt jedes Gartentor fuer interessant; das Projekt hat kein SuppressWarnings und uebersetzt mit -Xlint:all ohne Warnung.
- MONITOR OHNE ignoreCancelled - genau umgekehrt zu Verdienst- und Setzwache. Jene wollen nur gelungene Taten (sie zahlen Geld), dieses Buch will beides: Ein Dutzend abgewiesener Versuche an einer fremden Parzelle ist der Fund, bei dem NICHTS passiert ist.
- Das Aufraeumen haengt am Spielzeittakt und NICHT an der Blockwache: Jene wird bei leerer Auswahl gar nicht angemeldet, und dann waeren die alten Zeilen unloeschbar. Der Tagesmerker wird nur bei VOLLSTAENDIGER Raeumung gesetzt - sonst kaeme der naechste Versuch erst morgen, und bei Wochen Rueckstand holte die Frist nie auf.
- Einspritzung 9.11. (Blockbuch): 26 Faelle, 22 gefangen. Die vier Luecken sind geschlossen und nachgemessen.
- Der WELTSCHLUESSEL an einer NAHT war die echte Luecke: 31ad/31ae pruefen nur Felder, die Naht hat keins. Jetzt 31ah (jede Schluesselmacher-Naht bekommt weltschluessel), ein Wurf im Kern (ein Schluessel ohne : trifft nie) und eine Klage, die sagt "das ist KEIN Fehler in deiner Datei".
- Zwei eigene Waechter waren stumpf: 31aj suchte "void imTakt(" und fand Blockraeumung nicht (sie gibt ein Ergebnis zurueck) - er prueefte zwei Mitfahrer und schwieg ueber den dritten. Und die Tatwort-Probe holte ihre Erwartung aus tatwort() selbst, also aus der Funktion, die eingespritzt wurde. Beides gemessen, beides geschlossen.
- Die ZONE beim Zeitstempel faengt keine Bildtafel: Dort wird die Zeit FERTIG eingesetzt, geprueft wird die Form, nicht die Herkunft. Die Probe nimmt jetzt einen Zeitpunkt, bei dem Berlin schon den naechsten TAG hat.
- Spieltest 9.11. bestanden: abgewiesene Truhenzugriffe rot mit x, gelungenes Setzen grau, Berliner Zeit, nur Behaelter bei benutzen, ein Klick = eine Zeile (das Hand-Tor haelt). Offen: das Aufraeumen (braucht einen Tageswechsel) und der Kistenschutz gegen einen ZWEITEN Spieler.
- Das Blockbuch ist NICHT neuladbar - eine Aenderung braucht einen Neustart, und der Startbericht sagt es.

- ~~Als naechstes der SCHLUESSEL: /shop freigeben <spieler> und /shop sperren <spieler>, eine Mitbenutzerliste je Shop (Tabelle, Befehl, Recht).~~ 10.11. GEBAUT: Tabelle kistenshop_freigabe (Schemaschritt 17), /shop freigeben|sperren|freigaben, Recht freimarken.shop, dreizehn Saetze. /shop ist der erste eigene Befehl des Projekts - bisher lief der ganze Shopbereich ueber Schild beschreiben und Klicken.
- Die drei Wege sind NICHT gleichwertig, anders als hier vorher stand: Freigabe = Kiste oeffnen UND abbauen, aber KEIN zweites Schild. Das zweite Schild war das Loch vom 8.11. - wer an fremder Kiste ein eigenes Schild anhaengt, verkauft fremde Ware auf sein Konto. Waere es fuer Freigegebene offen, stuende das Loch fuer genau die Leute wieder auf, denen man gerade Vertrauen geschenkt hat. Die Regel bleibt an EINER Stelle (Kistenshop.offenFuer), gibt aber je Weg eine eigene Antwort; welcher Weg fragt, sagt die Aufzaehlung Shopweg.
- Freigeben darf NUR der Besitzer - nicht das Team (das Teamrecht oeffnet eine Kiste, damit ein verlassener Laden abgebaut werden kann, es macht niemanden zum Hausherrn) und nicht ein schon Freigegebener (sonst wuesste nach zwei Schritten niemand mehr, wer Zutritt hat). Ein Bundshop gehoert niemandem und laesst sich darum von niemandem freigeben.
- Einspritzung 10.11. (/shop freigeben): 13 Faelle, 7 beim ersten Durchgang gefangen. Alle sechs Luecken sind geschlossen und nachgemessen, 2617 Proben.
- Die drei Aufrufstellen nannten ihren WEG ungeprueft: Dass die Stelle den Kern RUFT, hielten die Proben; WELCHEN Weg sie dabei nennt, keine. Drei Einspritzungen, die nur dieses eine Wort tauschten, liefen gruen durch. Jetzt eine Tafel Stelle<->Weg plus Verhaltensproben in ShopbuchTest und ShopwacheTest.
- Die FREIGABEN waren auf keinem Leseweg geprueft: MariaShops liest sie einzeln fuer anDemSchild und in EINER Abfrage fuer die drei Listen (gegen N+1). Jetzt drei Vertragsproben, also an allen drei Ablagen - auch dass ein zweites Schreiben ERSETZT und nicht dazulegt, denn genau das IST das Sperren.
- FUND 10.11., groesser als der Anlass: Ein gelaufener Schemaschritt ist nicht mehr aenderbar. migriere() fuehrt nur Schritte ab dem gespeicherten Stand aus - wer einen BESTEHENDEN Schritt aendert, aendert auf einer laufenden Datenbank NICHTS, die Tabelle steht wie am ersten Tag. IF NOT EXISTS ist dabei nur der zweite Riegel. Auf einer frischen Probendatenbank wirkt dieselbe Aenderung sofort, und dann sagen die Proben etwas ueber ein Schema, das auf keinem Server steht. Gemessen: eine Einspritzung, die ON DELETE CASCADE entfernte, lief vollstaendig gruen durch.
- Darum SchemaTest.schritteSindUnveraenderlich - je Schritt eine Pruefsumme, maschinell aus einem absichtlich roten Lauf. Wer einen alten Schritt aendert, wird rot und liest, dass ein NEUER anzuhaengen ist (ALTER TABLE ... IF NOT EXISTS); wer einen anhaengt, bekommt die fertige Zeile zum Einsetzen. Dafuer ist Datenbank.SCHRITTE paketprivat statt privat: Die Proben sehen so die ECHTEN Schritttexte und nicht aus dem Quelltext geschnittene Kopien.
- Und MariaShopsTest.fremdschluesselNimmtDieFreigabenMit laesst die Tabelle FALLEN, fuehrt den echten Schritttext aus und baut dann einen Shop mit Freigabe ab. Ohne ON DELETE CASCADE stuende der Fremdschluessel auf RESTRICT, und ein Shop mit Freigabe liesse sich nie wieder abbauen - das Schild blieb stehen und der Besitzer kaeme nicht an seine eigene Kiste.
- Offen beim Spieltest: die Freigabe mit einem ZWEITEN Spieler erproben (der Kistenschutz gegen einen Fremden steht noch aus demselben Grund aus).
- Als naechstes der SCHLUESSEL: /shop freigeben <spieler> und /shop sperren <spieler>, eine Mitbenutzerliste je Shop (Tabelle, Befehl, Recht). Haengt an Kistenshop.offenFuer als vierter Fall - eine Stelle, drei Wege.
- ~~Als naechstes eine Shopliste fuer /shop (Shopablage.vonSpieler) - wer viele Shops hat, findet sie sonst nicht wieder.~~ 10.11. GEBAUT: /shop liste [seite], sechs Saetze, handel.zeilen-je-seite (Vorgabe 10, erlaubt 1-100).
- SEITEN und kein Schnitt: Ein Schnitt bei zehn Zeilen verfehlte genau den Zweck - wer dreissig Laeden hat, findet sie nicht wieder, und zwanzig davon waeren ueber den Befehl gar nicht erreichbar. "Unbegrenzt" gibt es nicht, der Chat ist kein Anzeigemittel fuer hundert Zeilen.
- Der einzige /shop-Zweig OHNE Blick auf ein Schild. Die Wurzel bleibt spielerpflichtig, aber die Begruendung ist neu: drei Zweige fragen den Shop im Blick, liste fragt nach den EIGENEN Laeden - und die Konsole hat keine.
- FUND 10.11.: Shopablage.vonSpieler versprach "nach Schluessel geordnet", und die Ablagen ordneten VERSCHIEDEN. Der Schluessel ist Text (welt x y z), beide Speicher-Ablagen gaben ihre Listen ueber die values() einer TreeMap darueber heraus - lexikographisch steht 100 vor 9 und -20 vor 8. MariaShops ordnete in allen drei Listenabfragen numerisch, und zur Laufzeit liest immer der Zwischenspeicher. Bei einer Liste auf Seiten entscheidet die Ordnung, WAS auf Seite eins landet.
- Gesehen hat es keine Probe: Der Vertrag vergleicht bei vonSpieler einelementige Listen, und bei alle() stand nur "zweimal hintereinander dieselbe" - das hielten beide Ordnungen. Jetzt Kistenshop.NACH_STELLE als die EINE Ordnung, von allen Ablagen gehalten, plus eine Vertragsprobe mit Stellen, deren Text- und Zahlenordnung auseinanderfallen (-20, 8, 10, 100).
- Die Preise sind WORTSTUECKE (Art.WORT) nach dem Muster parzelle.liste.mitbauer: Ein leerer Wert verschwindet rueckstandslos, darum stehen die Trenner in den Stuecken. Ein Laden, der nur ankauft, hat so gar keine Kaufangabe - statt einer "0 Mark", die Shopschild als PREIS_BEIDE_NULL abweist.
- Die Pruefung der Seitengroesse steht ZWEIMAL, und das ist Absicht: Der Satz Shopwerte WIRFT (ein Shopwerte mit 0 Zeilen darf es nicht geben), der Leser Shopliste KLAGT und nimmt die Vorgabe (er laeuft beim Start - ein Server soll wegen einer Zahl in einer Datei nicht liegenbleiben).
- Shopwerte hat jetzt fuenf Komponenten, 25 Aufrufstellen in 4 Dateien. Die Proben setzen ueberall einen Wert, der NICHT die Vorgabe ist; zwei neue Proben halten, dass alle vier mitX-Methoden ihn durchgeben - faellt eine auf die Vorgabe zurueck, sehen zehn Zeilen richtig aus.
- Einspritzung 10.11. (/shop liste): 12 Faelle, 10 beim ersten Durchgang gefangen. Die zwei Luecken waren Fehler in den PROBEN, nicht im Programm: Die Preise liessen sich vertauschen (die Proben pruefen Warennamen, die Bildtafel setzt die Preisstuecke fertig ein), und NACH_STELLE liess x und z tauschen, weil die Vertragsprobe nur x aendert - alle ihre Shops liegen bei z=6. Beide geschlossen und nachgemessen, 2640 Proben.
- Die neue Achsenprobe braucht ein Paar mit GLEICHEM x (5/1/9 und 5/9/1): Bei lauter verschiedenen x entscheidet x allein, und eine Vertauschung von y und z waere fuer immer blind.
- Offen beim Spieltest: /shop liste mit mehr als einer Seite, und die Freigabe mit einem ZWEITEN Spieler.
- Als naechstes eine Shopliste fuer /shop (Shopablage.vonSpieler) - wer viele Shops hat, findet sie sonst nicht wieder.
- ~~Offen in Teil D: die Freie Truhe (Entnahme ~16 Gegenstaende je Spieler und Tag, taeglich geleert).~~ 10.11. GEBAUT, siehe unten.
- ~~Noch von Hand zu tun, bevor jemand mieten kann: die Markthalle in Altfurt bauen und ihre Staende als Quader in laeden.einheiten eintragen.~~ Quader stehen seit 9.11.; die HALLE ist am 11.11. gebaut - nicht von Hand, sondern als Skript aus 26 Konsolenbefehlen (vanilla /fill, kein WorldEdit: jenes braeuchte ein zweites Plugin UND eine Spielersitzung fuer die Auswahl).
- Die Halle umschliesst, was schon dastand: Torreihe z=0 als Nordwand (um eine fuenfte Schicht ergaenzt), Seitenwaende x=56 und x=86, Rueckwand z=24, Dach mit drei Glasbaendern, neun Haengelaternen. Die drei Standboeden sind dunkles Eichenholz, die Freie Truhe steht auf einer Platte mit zwei Laternen. Die LUECKE z 11..15 bleibt glatter Boden - eine Zierde darin machte die Ladenschutzprobe unlesbar.
- FORCELOAD ZUERST, sonst tut /fill gar nichts und meldet "Successfully filled 0 blocks". Und nach dem Bau wieder freigeben - danach schlagen aber auch Pruefbefehle (execute if block) still fehl, weil die Chunks weg sind. Das hat mich einen Durchgang gekostet.
- Gemessen vor dem Bau (execute if block + say): Die Torreihe steht auf y -60..-57, beide Arten Truhen stehen, der Boden war noch Generatormaterial. Gemessen danach: 14 Pruefungen, alle ja - und der Neustart meldet "Ladeneinheit stand-3: Truhe bei 82/-60/18, 99 von 125 Bloecken frei" ohne eine schwere Zeile.
- EINE ZAHL verschiebt alles: BODEN, die oberste Bodenschicht. Flacher Probeserver -61, ein gewoehnliches Altfurt 70. Fuer den echten Server ist das Bauen damit ein Lauf des Skripts und keine Handarbeit mehr.
- ~~Die Freie Truhe ist eine DOPPELTRUHE~~ 11.11. (Wunsch des Nutzers): 54 Plaetze statt 27, beide Haelften bei 72/-60/20 und 73/-60/20, beide in freie-truhe.truhen. Steht nur eine dort, ist die andere eine gewoehnliche Kiste der Stadt - und die Einrichtungswache haelt sie zu. Der Startbericht sagt jetzt "Truhenstellen: 2".
- GEMESSEN und nicht geraten: Bei facing=north ist type=left die Haelfte mit dem KLEINEREN x. Umgekehrt gesetzt, hat der Server die zweite Haelfte still auf type=single zurueckgedreht - das Spiel prueft die Paarung selbst, eine falsche ueberlebt kein setblock. Das Programm war schon darauf gebaut (Shopware.bloeckeVon, eine Haelfte genuegt), geaendert wurden nur Welt und Einstellung.
- Die ausgelieferte config.yml zeigt im Beispiel jetzt BEIDE Haelften - wer eine Zeile kopiert, baut sonst eine halb geschuetzte Truhe.
- ~~8.11. ENTSCHIEDEN, Bundesmarkt: Verkauft wird NUR am Schild, nicht per Befehl.~~ 10.11. GEBAUT: Ein Bundshop mit Ankaufpreis IST der Bundesmarkt. /markt verkaufen ist weg, /markt liste bleibt, sieben neue Saetze unter shop.bund, neue Kernklasse Bundankauf.
- Keine neue Wache - das stand hier falsch: Der Klick auf ein Shopschild laeuft seit dem 8.11. durch Shopklick, und ein Bundshop ist ein Shopschild. Gebraucht wurden DREI REGELN an der Stelle, wo die Markttafel auf eine Shopzeile trifft.
- REGEL 1, der Preis kommt aus bundesmarkt.ankauf und NICHT vom Schild. Nur so wirkt eine Preisaenderung an jedem Stand, ohne dass einer neu beschrieben wird - die Zusage "beliebig anpassbar". Stuende der Preis in der Shopzeile, waere die Tafel nach dem ersten Schild eine zweite Wahrheit, und /markt liste nennte Preise, die kein Stand zahlt. Weicht ein Schild ab, wird zur TAFEL gehandelt und das Protokoll nennt beide Zahlen samt Stelle.
- REGEL 2, die Menge muss eine ganze Mark ergeben (menge mal pfennige durch 100 ohne Rest). Verkaufsregel liess beim Befehl Stuecke liegen; am Schild ist die Menge FEST, und 16 Stein zu 20 Pfennigen sind 3,20 Mark - 20 Pfennige verschwaenden bei JEDEM Handel. Die Absage nennt die kleinste passende Menge (100 durch den groessten gemeinsamen Teiler).
- REGEL 3, ein Bundschild traegt genau EINE Richtung. Was der Bund ankauft, steht in der Tafel, was er verkauft, auf dem Schild - auf einem Schild mit beiden Zahlen entscheidet eine Haelfte der Betreiber und die andere die Tafel, und eine spaetere Erhoehung des Ankaufpreises macht daraus einen Rundlauf (billig kaufen, teurer zurueckverkaufen). Geprueft beim Anlegen UND bei jedem Ankauf: Ein Schild von vorher traegt beides weiter, und die Schranke im Satz Kistenshop galt nur beim Bauen der Zeile.
- Die benannte LUECKE aus Plan 7.4 ist damit zu: Ein Bundshop mit Verkaufspreis war eine Geldquelle ohne Tagesdeckel. Jetzt dieselbe Deckelregel, dasselbe Heute und derselbe Buchungsgrund wie der Befehl - bundesmarkt_verkauf. Die Marktgebuehr bleibt SHOP_GEBUEHR: eine Ausgabe des Kunden, keine zweite Quelle.
- Der ORT ist jetzt das Gebaeude. Die Weltpruefung "bist du in Altfurt?" ist mit dem Zweig gegangen - ein Schild steht, wo es steht. Dafuer nennt der Startbericht die Zahl der Ankaufstaende: Wer sein Sortiment einstellt und kein Schild stellt, hat einen Markt, der nichts kauft, und nirgends fehlt etwas.
- FUND 10.11., zweimal derselbe: Schnappschuss statt Halter. Shophandel hielt ein Shopwerte als FELD (die Gebuehren des Serverstarts), Shopwache das Bundwort als TEXT. Ein Neuladen baut absichtlich kein Objekt neu - also wirkte keine der beiden Aenderungen je, waehrend /freimarken neuladen "uebernommen" meldete. Dieselbe Falle wie bei /shop liste am 10.11. frueh. Beide halten jetzt den Wirtschaftsstand; zwei Proben tauschen ihn MITTEN im Lauf.
- FUND 10.11.: Die erste Zeile eines fertigen BUNDSHOPS trug den Namen des Betreibers. Shopschild.schreibe verlangt im Javadoc "der Spielername ODER das Bundwort", und die eine Aufrufstelle gab immer den Namen. Gehandelt wurde richtig (die Datenbankzeile ist die Wahrheit, nicht das Holz), aber jeder Kunde las etwas Falsches. Die WAHL steht jetzt im Kern - dort ist sie ohne Server pruefbar, und die Wache fragt nirgends mehr selbst nach demBund(), was eine Quelltexttafel ausdruecklich verlangt.
- FUND 10.11.: In Shopliste.java stand zeilenJeSeite samt eigenem Javadoc ZWISCHEN dem Doku-Block von bundwort und bundwort selbst - jener dokumentierte nichts mehr, und bundwort hatte keine Doku. Javac meldet es (dangling-doc-comments), aber die Warnung war verdeckt: Die Aufgabe war beim naechsten Lauf UP-TO-DATE. Jetzt Probe 31ak ueber ALLE drei Module, und das Projekt uebersetzt weiter ohne -Werror.
- Das MATERIAL kommt aus einer neuen Naht und NICHT aus ware().name(): Jener Name ist gekuerzt (NETHERITE_PICKAXE wird PICKAXE), zwei Materialien koennen denselben Kurznamen haben, und ShopwareTafel haelt die Zusage, dass er nirgends verglichen wird. Die Tafel bundesmarkt.ankauf steht je Material.
- Markttag ist eine eigene Klasse fuer EINE Abfrage: Was ein Spieler heute schon aus Verkaeufen bekommen hat, brauchen jetzt ZWEI Stellen - /markt liste verspricht die Zahl, der Stand entscheidet damit. Zwei Abschriften liefen auseinander, und dann verspricht die Liste etwas, das der Stand nicht hergibt.
- Zehn Proben von /markt verkaufen sind mit dem Befehl gegangen; ihre Zusagen stehen namentlich am Stand (ShophandelTest.deckelBeschneidetDenStapel, deckelZaehltNurVerkaeufe, ausDemSortimentGenommen, ShopbuchTest.unebeneMengeWirdAbgewiesen). Der Kopf von MarktBefehlTest sagt, welche wohin gewandert ist.
- ~~Offen, unveraendert: /markt liste wirft bei stummer Datenbank.~~ Der Fang stand seit dem 10.11.; echt offen waren der falsche SATZ (eine stumme Kasse meldete unlesbare Staende), die weggeworfene Liste und die fehlende Probe. 20.11. behoben, siehe Nachlese zu Schritt 12.
- Offen: Ein Bund-Ankaufstand auf einem BESONDEREN Gegenstand (verzaubert) zahlt den Preis des gewoehnlichen - die Tafel steht je Material. Kein Spielerloch (nur der Betreiber stellt Staende), aber eine Falle fuer ihn. Nicht gebaut, weil der Vergleich "gewoehnlich?" nur mit laufendem Server pruefbar waere.
- UMKEHR 10.11. nachmittags, auf Zuruf des Betreibers: "einfach alles freigeben, admins stellen die schilder auf, es kann ja nur ge und verkauft werden, wofuer es ein schild gibt". Die Tafel bundesmarkt.ankauf ist damit GEFALLEN - mit ihr die Pfennige als Einheit, die Pruefung auf ganze Mark, die Absage "das kauft der Bund nicht" und die Regel "ein Bundschild traegt eine Richtung".
- Der PREIS steht jetzt auf dem Schild, wie bei jedem Kistenshop. Ein Schildpreis IST eine ganze Mark, und beide Preise auf einem Schild haelt der Satz Kistenshop selbst (Verkauf nie ueber Kauf) - der Rundlauf, gegen den die Einrichtungsregel stand, ist dadurch unmoeglich.
- /markt zeigt seither die STAENDE aus der Datenbank: Welt, Koordinaten, Menge, Ware, beide Preise, auf Seiten wie /shop liste. Neun neue Saetze unter markt., sechs unter shop.bund gefallen. Der Bundesmarkt ist damit erst wirklich ein Ort - die Weltpruefung sollte das nachbilden und ist am Morgen mit /markt verkaufen gegangen.
- Die SEITENRECHNUNG steht jetzt im Kern (Seitenregel), geteilt von /shop liste und /markt liste. Vier der zwoelf Einspritzungen gegen /shop liste sassen in genau diesen Zeilen - abgerundete Seitenzahl, verschobener Anfang, abgewiesene letzte Seite, Schnitt ueber das Ende. Die zweite Liste sollte sie nicht neu einsammeln.
- Gefallen sind zwei ganze Kernklassen (Ankaufliste, Verkaufsregel) samt Proben, der Sortimentsleser in Marktliste, die Materialnaht in Shopware und 98 Zeilen aus der ausgelieferten config.yml. Die Preisgrenze HOECHSTER_PREIS wohnt jetzt im Kistenshop, wo sie hingehoert.
- DECKEL: bundesmarkt.tagesdeckel: 0 heisst KEIN Deckel [entschieden 10.11.]. Vorher war die 0 verboten ("niemand kann verkaufen"); abgeschaltet wird der Ankauf jetzt dadurch, dass kein Schild steht. Der Sprungschutz greift auch fuer die 0 - wer den Deckel abschaltet, traegt die Bestaetigung ein, und das ist angebracht: ohne Deckel ist der Ankauf eine Quelle ohne Grenze.
- ~~OFFEN: Niemand prueft mehr, ob ein Bundschild MEHR zahlt als die Arbeit einbringt.~~ ENTSCHIEDEN 10.11. vom Betreiber: "da ich die schilder selber platziere, pruefe ich das und nicht niemand." Die Tafel stand dafuer gegen die Lohnliste, weil sie der Betreiber in einer Datei pflegte und dort den Ueberblick verlieren konnte; ein Schild stellt er mit der Hand hin und sieht dabei, was darauf steht. Kein Riegel im Programm.
- ~~OFFEN: ein Tagesdeckel JE MATERIAL.~~ ENTSCHIEDEN 10.11., nicht gebaut: "haben wir ja jetzt frei einstellbar". Der EINE Deckel reicht - 0 heisst kein Deckel, jede andere Zahl begrenzt den Tag. Ein Deckel je Material braeuchte eine Spalte im Buchungsbuch (heute steht dort nur Grund und Betrag), einen angehaengten Schemaschritt und den Kassenvertrag; das ist der Preis, und er ist nicht faellig.
- ~~FUND 10.11., im Blockbuch gesehen: buchungen.zeitpunkt steht in UTC, blockbuch.zeitpunkt in ORTSZEIT.~~ 11.11. UMGESTELLT - und zwar JETZT, weil Freimarken nie auf einem dauerhaften Server gelaufen ist: Es gibt keine Zeile, die eine Umstellung mitnehmen muesste. Nach dem Produktivstart waere genau das der Preis gewesen.
- BETROFFEN WAREN ZWEI Tabellen, nicht eine: blockbuch.zeitpunkt UND abholfach.eingelegt. Beim Abholfach versprach der Schemaschritt seit jeher "eingelegt ist DATETIME(3) und UTC, wie jede Zeitspalte dieses Schemas" - der Quelltext hielt es nicht. Eine Zusage im Kommentar, die keine Probe traegt, ist eine Behauptung.
- Der Kalender wohnt jetzt an EINER Stelle (Datenbank.utcKalender) statt privat in MariaKasse; ein neues Exemplar je Aufruf, weil Calendar nicht nebenlaeufigkeitsfest ist. Die eine Ausnahme bleibt und ist keine Zeitspalte: spielzeit.tag ist ein DATUM in Ortszeit aus zeit.zone.
- KEIN Umrechnungsschritt im Schema: SchemaTest verlangt, dass jeder Schritt einen zweiten Lauf uebersteht (abgebrochener Start, zwei Server auf einer Datenbank), und ein "UPDATE ... zeitpunkt - Versatz" verschoebe dann doppelt. Alte Zeilen auf Probeservern bleiben in Ortszeit; das steht an der Schreibstelle.
- NEUE PROBE je Tabelle: Sie liest die Spalte als ROHEN Text (CAST AS CHAR) und vergleicht mit der UTC-Schreibweise. Die vorhandenen Proben schrieben und lasen durch denselben Treiber - und waren darum blind, solange BEIDE Seiten dieselbe Zone nahmen. Einspritzung 4/4 gefangen, jeder Fall von der Rohtextprobe UND den Rundlaufproben. Proben 2809 -> 2811.
- ~~Zwei [removal]-Warnungen in SetzwacheTest~~ 11.11.: BlockPlaceEvent und BlockMultiPlaceEvent bekommen die Hand (EquipmentSlot.HAND) - der Konstruktor ohne sie ist zum Entfernen markiert.
- FUND 10.11., der aelteste von allen: freimarken.beruf und freimarken.markt stehen NICHT in der plugin.yml - und ein Recht, das kein Plugin anmeldet, gehoert bei Bukkit NUR Operatoren (Permission.DEFAULT_PERMISSION ist OP). /beruf und /markt waren damit seit ihrem Bau fuer jeden gewoehnlichen Spieler verborgen UND verwehrt, waehrend im Programm woertlich "jeder" steht. Das ist der Einstieg in die Wirtschaft: ohne Beruf kein Verdienst, ohne Markt keine Verwertung.
- Warum es ein halbes Dutzend Spieltests ueberlebt hat: Jeder lief mit einem Operator, und fuer den verhaelt sich ein Recht mit default true genauso wie ein fehlendes. Der Unterschied zeigt sich nur bei einem Spieler OHNE Rechte - und der war nie dabei. Dieselbe Lehre wie bei der Freigabe und beim Kistenschutz, die beide noch auf einen ZWEITEN Spieler warten.
- Jetzt drei Rechte angemeldet (beruf default true, beruf.spezialisierung default op als Rangvorteil, markt default true) und Probe 31al: Jedes Recht, das das Programm fragt, steht in der plugin.yml - und jedes angemeldete wird gefragt. BEIDE Richtungen, denn ein Recht ohne Zweig ist eines, das ein Betreiber vergibt und bei dem nichts geschieht. Die vier Vorsilben dynamischer Rangknoten (freimarken.parzellen.5 und so) sind ausgenommen und benannt: Sie sind keine angemeldeten Rechte, sondern Werte.
- Probe 31ak: kein Dokumentationsblock haengt an einem anderen, ueber ALLE drei Module. Javac meldet das als dangling-doc-comments, aber nur im Lauf, in dem wirklich uebersetzt wird - und die Aufgabe war beim naechsten Lauf UP-TO-DATE. Das Projekt uebersetzt weiter ohne -Werror; eine Probe sieht es bei jedem Lauf.
- Einspritzung 10.11. (Bundesmarkt am Schild): 22 Faelle, 17 beim ersten Durchgang gefangen. Drei echte Luecken, eine unerreichbare Pruefung, ein Fehler der Messung.
- LUECKE 1: Dass die erste Schildzeile eines BUNDSHOPS das Bundwort traegt, hielt keine Probe - also genau der Fund desselben Tages. Gefangen hat ihn niemand, weil das Zurueckschreiben nur mit einem SPIELERSHOP geprueft war, und dort ist der Spielername richtig. Jetzt stehen beide Faelle beieinander: Der Unterschied zwischen ihnen IST die Regel.
- LUECKE 2: Die Reihenfolge von Deckel und gewoehnlichem Mangel hielt keine Probe. Vertauscht liest ein Spieler ohne Ware "heute ist genug verkauft" und geht nach Hause, obwohl der Deckel gar nicht angetastet ist. Erreichbar ist der Unterschied nur mit leerem Rucksack UND offenem Deckel.
- LUECKE 3: Dass die Wache dem Kern BEIDE Namen hereingibt, hielt keine Probe. Ohne das Wort kann Shopschild.schreibe fuer einen Bundshop nur werfen - mitten im Schildschreiben, nachdem der Shop schon steht.
- UNERREICHBAR, nicht ungedeckt: Der Riegel preis >= 1 in brauchbarerStufenpreis war von keiner Einspritzung zu treffen. Beide Aufrufstellen fragen zuerst gehtGlattAuf, und wenn das ja sagt, ist menge mal pfennige mindestens 100 - der Stufenpreis also mindestens 1. Zwei Pruefungen fuer denselben Fall machen sich gegenseitig unpruefbar; die unerreichbare ist heraus, die untere Kante steht an EINER Stelle.
- MESSFEHLER, kein Fund: Die Einspritzung gegen Probe 31ak stellte den Block VOR den Doku-Block von bundwort, und das ist eine gewoehnliche Reihenfolge. Abgerissen ist er nur, wenn er ZWISCHEN jenem Block und seiner Deklaration steht - so nachgemessen.
- Offen beim Spieltest: der Ankaufstand selbst - Schild beschreiben, verkaufen, Tagesdeckel, Preisaenderung mit /freimarken neuladen, und ein Schild mit unebener Menge. Dazu /beruf und /markt mit einem Spieler OHNE Operatorrechte.
- Offen, Berufe: der Kolben, der einen markierten Block weiterschiebt, und der Pflastersteingenerator - Weltregeln, eigener Schritt.
- Entscheidung, Sprachdatei: sendeHandbalken hat seit Teil E zwei Aufrufer; Sprache.sende und sendeGezaehlt haben weiter keinen. Entfernen oder beim naechsten Sendeort nutzen?
- Entscheidung, Sprachdatei: Die Chatzeilen der vier Verwaltungszweige von /freimarken bleiben im Programm - sie gehen an einen Betreiber mit dem Recht, nicht an einen Spieler.
- ~~Offen, Sprachdatei: Eine Umbruchmarke in einem Satz, der ueber Sprache.eineZeile geht, laesst den Aufbau werfen.~~ 20.11. GEBAUT: Das Merkmal ist eine LISTE (Einzeiler), gerechnet aus den Holstellen, und die Klage kommt beim LADEN. Tafel 10j haelt beide Richtungen.
- Pruefen, Sprachdatei: Nach dem ersten Start liegen beide Dateien im Plugin-Ordner, /mark verlauf sieht aus wie vorher. Dann eine Zeile samt aller Ebenen darueber nach sprache.yml kopieren, Farbe aendern, /freimarken neuladen: die Antwort nennt, wie viele Saetze von dir sind.
- Pruefen, Sprachdatei: Eine kaputte Zeile in sprache.yml, etwa ein offenes <gold> - GAR NICHTS wird uebernommen, die Klage nennt Zeile und Spalte, die bisherigen Saetze gelten weiter.
- Pruefen, Sprachdatei: Die neun Befehlsbeschreibungen in /help kommen aus der Datei, aendern sich aber erst nach einem Neustart.
- Offen, Sprachdatei (nicht im Spiel geprueft): dass die Vorlage bei jedem Start ueberschrieben wird, sprache.yml nie angetastet, und dass SnakeYAML unter Paper zur Laufzeit erreichbar ist.
- ~~Offen: /rang gibt es noch nicht.~~ 20.11. GEBAUT, und das Beispiel in hinweise.rang-werbung nennt ihn jetzt - der Kommentar dort verbot den Verweis vorher ausdruecklich.
- Aufraeumen, wenn Schritt 6 abgeschlossen ist: Probeserver-Ordner im Temp loeschen (Zugangsdaten im Klartext) und die Testdaten in der echten DB zuruecksetzen - die Parzellen 0/-1 (id 108) und -1/-1 (id 111) waren nur fuer den Test, Thormak2002 steht auf 200.000 Mark.
- Offen, niedrig: Die Spaltenschleife kann im aeussersten Fall ~707.000 Blockzugriffe in einem Befehl machen, und die Warnung ist ohne Bestaetigung beliebig oft ausloesbar. Ein Hohlraum unter einer stehengelassenen Decke wird bewusst nicht gefunden.
- Offen (eigene Entscheidung): Ein abgebrochener Zurueckbau laesst sich nicht nachholen - die Gruppe ist in der Ablage weg, Gras bleibt auf oeffentlichem Weg liegen. Braeuchte einen Befehl, der die Welt der Ablage nachzieht.
- ~~CHECK-Bedingung (x IS NULL) = (z IS NULL).~~ 31.10. als Schemaschritt 6 gebaut.
- Rasterplan und Weltschlüssel stehen weiter fest im Code (§4.2 und §14 verlangen "Welt != Code"); die Weltenliste steht inzwischen in der config.yml.
- Noch nicht geschützt (Weltregeln, kein Spielerhandeln): Explosionen, Feuerausbreitung, Kolben über die Grenze, Flüssigkeiten von außen, Trichter in fremde Kisten.
- Datenbankzugriffe laufen im Hauptfaden. Zeitgrenzen sind gesetzt (5 s) und der Schutz scheitert geschlossen, aber ein Wiederholungsversuch für Leseoperationen fehlt - ein stiller Verbindungsabbruch kostet eine Absage.
- Gradle auf der Freigabe: nach "clean" scheitert der nächste Bau daran, dass ein gelöschtes Verzeichnis noch nicht weg ist. Fehlende build-Verzeichnisse von Hand anlegen, dann geht es.
- ~~Schritt 8, Teil D: die Freie Truhe.~~ 10.11. GEBAUT: eine offene Kiste in der Markthalle, jeder legt hinein, jeder nimmt heraus. Deckel 16 Stueck je Spieler und Tag (0 heisst kein Deckel), Inhalt wird jede Nacht geleert. Schemaschritt 18 (Tabelle freie_truhe), acht Saetze unter truhe., Abschnitt freie-truhe: in der config.yml.
- GEZAEHLT WIRD DIE DIFFERENZ, NICHT DER KLICK - die tragende Entscheidung. Aus einer offenen Kiste nimmt man mit Links-, Rechts-, Shift-, Doppelklick, Zahlentaste, Ziehen und Q: sieben Wege zur selben Tat, und ein Programm, das sechs kennt, hat keinen Deckel. Beim Oeffnen wird gezaehlt, beim Schliessen wieder, und was FEHLT, ist die Entnahme. Dieselbe Lehre wie 'alle Wege zur selben Tat'.
- Zwei Zusagen tragen die Zaehlung, und die Wache erzwingt beide: EINER sieht zur Zeit hinein (sonst ist die Differenz die Entnahme von zweien), und kein Trichter wird bedient - in BEIDEN Richtungen, denn ein hineinlegender Trichter machte aus der Truhe einen Muellschlucker mit Zwischenlager.
- Angerechnet wird, was nach dem Zuruecklegen NICHT in der Truhe ist: Wer ueber dem Deckel liegt, bekommt den Ueberschuss beim Schliessen zurueckgelegt (die Plaetze sind frei, er hat sie gerade geleert). Wer seine Ware vorher wegwirft, behaelt sie angerechnet - sonst waere Wegwerfen-und-aufsammeln der Weg um jeden Deckel.
- Hineinlegen zaehlt gar nicht, und zwar JE MATERIAL gerechnet: Wer zehn Diamanten nimmt und zehn Erde hineinlegt, hat zehn Diamanten genommen. Mit einer Gesamtzahl waere Schutt hineinlegen der zweite Weg um den Deckel.
- Verbotene Gegenstaende (geschriebene Buecher, alle 17 Shulkerkisten - als LISTE in der config.yml) kommen beim SCHLIESSEN zurueck, nicht beim Hineinlegen: In eine offene Kiste legt man auf ebenso vielen Wegen, wie man herausnimmt. Zurueck geht nur, was DIESER Spieler neu hineingelegt hat - sonst bekaeme der naechste Zuschauer fremde Ware, wenn der Betreiber ein Material nachtraeglich auf die Liste setzt.
- Der Schnitt zwischen Truhenwerte (Zahlen, NEULADBAR, im Wirtschaftsstand) und Truhenverzeichnis (Stellen, nur beim Start) ist derselbe wie bei laeden: Mietliste liest die Zahlen, Ladenliste die Einheiten. Begruendung hier: Eine Stelle, die sich waehrend eines offenen Fensters aendert, rechnete die Entnahme gegen den Inhalt einer ANDEREN Kiste ab.
- Die Stellen liegen in der LADENWELT (laeden.welt) und haben keinen eigenen Schluessel: Die Freie Truhe steht in der Markthalle, und ein zweiter Weltschluessel waere eine zweite Wahrheit ueber dieselbe Halle. Ein Einstellschluessel fuer einen Wert, den es noch nicht gibt, ist derselbe Fehler wie ein Index fuer eine Abfrage, die niemand stellt.
- Eine Stelle INNERHALB einer Ladeneinheit wird beklagt und verworfen: Dort kaeme niemand ausser dem Mieter heran, und der nimmt ohne Deckel. Genau diese Verwechslung passiert beim Einmessen von Hand - beide Angaben stehen in derselben Datei und nennen Koordinaten derselben Halle.
- Der taegliche Lauf faehrt im Spielzeittakt mit (wie Ladenmiete und Blockraeumung, Plan 8.8 schliesst einen zweiten Zeitgeber aus). Drei Verneinungen: eine OFFENE Truhe wird nicht geleert und haelt den Tagesmerker; geraeumt wird erst, wenn alle leer sind (die Zeilen von gestern sind der Deckel von gestern); geraeumt wird AUCH, wenn keine Stelle mehr eingetragen ist - sonst waeren die Zeilen unloeschbar.
- Kein eigenes Recht: Wer an der Truhe steht, darf hineinsehen. Ein freimarken.truhe waere eine Zeile in der plugin.yml, die niemand pflegt; gesperrt wird ueber den Bauschutz der Halle oder gar nicht.
- FUND 10.11. beim Bauen: Truhenwerte.HOECHSTE_ENTNAHME ist Kistenshop.GROESSTE_MENGE, also 3456 (54 mal 64) und nicht 576 - die eigene Probe mit 577 blieb gruen. Die Zahl steht jetzt an drei Stellen richtig (Probe, Rechenprobe, config.yml-Kommentar).
- FUND 10.11.: Das Javadoc von richteSpielzeitEin() haengte nach dem Einsetzen von richteTruheEin() an keiner Deklaration mehr - dieselbe Falle wie am Morgen in Shopliste.java, und diesmal hat Probe 31ak sie gefangen, bevor ein Lauf UP-TO-DATE sie verdecken konnte.
- Nebenbei: VerdienstwacheTest baute PlayerQuitEvent ohne Abgangsgrund, und der Bauplan ist zum Entfernen markiert. Die Warnung war monatelang unsichtbar, weil paper:compileTestJava UP-TO-DATE meldete - sichtbar wurde sie erst, als der Kern sich aenderte.
- ~~Offen beim Spieltest von Teil D: eine Stelle eintragen, Deckel 16 ueberschreiten, ein geschriebenes Buch hineinlegen, Trichter anbauen.~~ 10.11. gepruefft - ein Fund, zwei Entscheidungen. Offen bleiben: zu zweit besetzt pruefen und der taegliche Lauf mit zeit.zone.
- ENTSCHIEDEN 10.11. (Chat schlaegt Plan): ab Werk **kein Deckel** (entnahme-je-tag: 0) und eine **leere Verbotsliste**. Die Truhe steht am Spawn, wo niemand baut; was darin liegt, hat jemand freiwillig hineingelegt. Trichter sind darum auch gleichgueltig - der Schutz bleibt trotzdem. Beides bleibt gebaut, geprueft und einstellbar, die config.yml fuehrt die 18 Namen aus dem Plan als Beispiel mit. Plan 7.2 ergaenzt.
- FUND 10.11. im Spieltest, gemeldet als "item wird geloescht": Der Rueckweg leitete seinen Stapel aus einem VORHANDENEN ab und suchte in Truhe UND Rucksack - nachdem dieselbe Hand das letzte Exemplar eben herausgenommen hatte. Also keine Vorlage, kein Zuruecklegen, Gegenstand weg, Meldung "du bekommst es zurueck" trotzdem verschickt. Dieselbe Stelle traegt den Ueberschuss ueber dem Deckel, und dort war es schlimmer: "20 Gegenstaende behalten - heute noch 0 frei" bei vier verschwundenen Steinen. Die Hand merkt sich jetzt beim Nehmen, was durch sie geht.
- Zwei neue Ereignisproben dazu (verbotenes kommt in den Rucksack, Ueberschuss kommt in die restlos leere Truhe); eingespritzt, beide rot, nichts anderes rot - es gab wirklich keine Deckung. Spielerprobe hat jetzt einen Rucksack, der etwas haelt, Probestapel ein clone() und eine Stapelgroesse. Proben 2777 -> 2779.
- Jede Probe, die am Deckel oder am Verbot rechnet, setzt ihre Zahlen jetzt SELBST (WERTE = 16 Stueck, zwei Verbote). An der Vorgabe haengend pruefte sie die Vorgabe statt der Regel - und waere mit dieser Entscheidung reihenweise umgefallen oder, schlimmer, blind gruen geblieben (assertFalse istVerboten gegen eine leere Liste).
- Nebenbei 10.11.: Serverprobe trug eine herrenlose Javadoc-Zeile (dangling-doc-comments) - sie haengt jetzt an keineWeltenMehr(). Sichtbar wurde sie erst bei einer echten Uebersetzung; UP-TO-DATE und die Teiluebersetzung verdecken solche Warnungen.
- Offen, nicht neu: SetzwacheTest ruft zwei BlockPlaceEvent-Konstruktoren, die zum Entfernen markiert sind - zwei [removal]-Warnungen bei jeder vollen Uebersetzung.
- ENTSCHIEDEN 10.11.2026: **gemeinsam hineinsehen**. "muss nicht, man soll auch gemeinsam hereinschauen koennen" - das "einer zur Zeit" haengt jetzt an Truhenwerte.zaehlt() (Deckel ODER Verbotsliste). Ohne beides wird nichts gezaehlt, niemand abgewiesen, kein Schnappschuss genommen. Der Trichterschutz bleibt immer - er haengt nicht an der Zaehlung, sondern daran, dass die Truhe Spielern gehoert.
- Der TAGESSTAND haengt an der engeren Frage: Er gehoert allein dem **Deckel**. Gelesen (zutritt, beimSchliessen) und geschrieben (merke) wird nur mit Deckel - abrechnen(0, schon, genommen) gibt fuer JEDES schon (genommen, 0), die Zahl entscheidet also nichts. Preis: Wer einen Deckel mittags einschaltet, gibt allen den Tag neu.
- Lesesuche 10.11. (6 Linsen, 3 Skeptiker): 25 Funde, 23 mit Mehrheit, fuenf Wurzeln. Behoben: (1) Verbote OHNE Deckel lasen den Tagesstand - ein Datenbankausfall hielt eine undeckelte Truhe zu und nahm beim Schliessen ALLES zurueck, mit dem Satz "mehr als 0 am Tag nimmt niemand mit". (2) Veralteter Schnappschuss ueber ein /freimarken neuladen hinweg. (3) Vier Texte, die seit dem Morgen etwas anderes versprachen als das Programm tut (config.yml-Abschnittskopf, truhen: ohne "nur beim Start", zwei Stellen im Plan). (4) Zwei Proben, die mit der leeren Vorgabe blind geworden waren.
- Einspritzungsrunde "gemeinsam" (11 Faelle): 11/11 gefangen, jeder von der Probe, deren Name den Fall nennt. Zwei Durchgaenge gebraucht - der erste fand zwei eigene Luecken: der Riegel im Schliessen war ein DOPPELTER (heraus) und die andere Stoerungsrichtung hatte keinen Satz (Zaehlung EINGESCHALTET mitten im offenen Fenster - ohne Riegel zahlte der Erste, der schliesst, fuer alle anderen). Proben 2779 -> 2789.
- ~~Truhenlauf fuehrt EINEN Tagesmerker fuer alle Stellen.~~ 11.11. BEHOBEN: ein Merker JE STELLE (geleerteStellen) plus ein Tagesmerker darueber. Eine Stelle, die heute schon geleert ist, wird nicht noch einmal gefragt - was ein Spieler zwischen zwei Takten hineinlegt, bleibt liegen. Die alte Begruendung ("ein zweites Leeren einer leeren Truhe kostet nichts") stand als Zusage in einer PROBE und ist dort umgedreht worden, mit Grund.
- Die Protokollzeile ist entprellt: Der "NICHT fertig"-Satz steht einmal am Tag statt 1.440-mal. Dieselbe Antwort wie Truhenleerung.schonBeklagt eine Schicht tiefer.
- Das Raeumen BLEIBT an alles-oder-nichts gebunden, und das ist richtig: Solange irgendwo noch der Inhalt von gestern liegt, gilt auch der Deckel von gestern. Der zweite Riegel dafuer (ein eigener geraeumt-Merker) ist beim Bauen weggefallen - nach einem erfolgreichen Raeumen steht der Tagesmerker, der Zweig waere unerreichbar gewesen.
- Neue Proben: die Gabe zwischen zwei Takten bleibt liegen, am naechsten Tag wird wieder geleert (mit STELLBARER Uhr - mit der festen ist der Tageswechsel gar nicht zu erreichen), die Zeile kommt einmal. Einspritzungsrunde 5/5 gefangen. Proben 2806 -> 2809.
- Offen beim Spieltest: zu zweit GEMEINSAM hineinsehen (muss jetzt gehen, keine Meldung), und der taegliche Lauf mit zeit.zone.
- Offen, nicht gebaut: Niemand prueft, ob an einer Freien Truhe ein SHOPSCHILD haengt. Ein Bundschild an ihr waere ein Stand, der die Gemeinschaftsware verkauft - ein Fehler des Betreibers, kein Spielerloch. Die Pruefung braeuchte den Shop-Zwischenspeicher, und der ist bei richteTruheEin noch leer; sie gehoert in den Startbericht von Teil E/F.
- ~~Offen, bekannt und nicht neu: Jede ANDERE Kiste in Altfurt steht ebenfalls jedem offen.~~ Diese Zeile war beim Nachsehen am 20.11. veraltet: Die Einrichtungswache vom 11.11. sperrt jeden Behaelter der Ladenwelt.
- Einspritzungsrunde 18 (10.11.): 35 Fälle, 33 gefangen, zwei Funde — 31 für die Freie Truhe und vier als Nachrunde zu Runde 17 (T11-T13 waren an einem Übersetzungsfehler gestorben, T14 ist mit der neuen Probe "Am Deckel KAUFT der Kunde weiter" geschlossen). Grundlinie 2761 Proben.
- FUND D18: Die Probe "Gezählt wird JE MATERIAL" legte die Erde erst NACH dem Öffnen hinein. Gerechnet wird aber über die Materialien des Schnappschusses, und was vorher nicht darin lag, macht die Differenz nie negativ - der Riegel (nur aufnehmen, was > 0 ist) wurde von keiner Probe gefragt, obwohl eine mit genau diesem Namen dastand. Probe geschärft: Die Erde liegt jetzt schon beim Öffnen in der Truhe.
- FUND D29: doppelter Riegel. Kleingeschrieben wurde in Truhenwerte UND beim Lesen in Truhenliste; das zweite Kleinschreiben wegzunehmen änderte nichts und machte nichts rot. Jetzt liegt die Regel an einer Stelle (Truhenwerte.wieAbgelegt), der Leser fragt dort und zählt für seinen Bericht das Ergebnis: "STONE" und "stone" sind EIN Gegenstand, und der Bericht nennt die Zahl, die wirklich gilt.
- Lesesuche 10.11. (24 Leser/Skeptiker über Teil D): 16 Lücken, alle geschlossen. Die Wurzel war eine: Die Bukkit-Hälfte der Freien Truhe hatte keine einzige Probe, während die der Kistenshops beides hat (echte Ereignisse in ShopwacheTest, Quelltext-Tafel in ShopwacheTafel). Proben: 2762 -> 2777.
- Neu: TruhenwacheEreignisTest - die Wache am ECHTEN Ereignis (Stück statt Stapel, der Öffnende ist kein Fremder, Trichter in BEIDEN Richtungen, gewöhnliche Kiste bleibt frei, ohne Spieler keine Rechnung). Möglich geworden durch Serverprobe + Proxy + Wesenprobe.Probestapel, genau wie bei den Kistenshops.
- Neu: TruhenleerungTest - die Hand des nächtlichen Laufes (jeder Platz wird geleert, ein Zuschauer hält auf, keine Kiste = 0 und EINE Klage, Welt nicht geladen = SPAETER). Alle Stellvertreter stehen in der Probe selbst, weil Truhenleerung ihren Server im Konstruktor bekommt.
- GrenzeTest 31ai LIEST jetzt die Mitfahrer aus new Spielzeitwache(...) statt eine handgeschriebene Liste zu führen: Der vierte Mitfahrer (truhenlauf) war von der alten Fassung nicht gedeckt, und richteTruheEin() hinter richteSpielzeitEin() zu schieben liess alles grün. Fallen dabei auf: buch ist ein Feld UND in drei richte-Methoden eine örtliche Größe - nur Felder haben eine Reihenfolge.
- GrenzeTest 31an neu: Die Anmeldung der Truhenwache als Quelltext-Tafel. Kehrt sich die Frage nach den Stellen um, ist die Freie Truhe eine gewöhnliche Kiste, und keine Probe kann den Start des Plugins durchlaufen.
- Geschärft, weil die Probe den geprüften Zweig nicht betrat: je Material (die Erde liegt jetzt SCHON BEIM ÖFFNEN in der Truhe), der Deckel wirkt auch beim SCHLIESSEN, der angefangene Tag steht in der Zahl beim Öffnen, die 0 erreicht die Ablage nicht (Protokoll bleibt leer), Räumen löscht ALLE alten Zeilen (zwei Spieler, drei Tage), die Rückgabezeile nennt den Deckel (drei verschiedene Zahlen statt behalten = Deckel), beide Hälften der Rechenprobe, die verkürzte long-Zahl, die ganzzahlige Kommazahl 1.000.
- ~~Javadoc versprach zu viel: Truhenlauf.zuletzt~~ 10.11. berichtigt. Der Satz sagte, ein zweiter Lauf am selben Tag koste "einen Durchgang, keine Ware". Nach einem Neustart mittags kostet er genau die Ware, die in der Truhe liegt - der Merker lebt nur im Arbeitsspeicher. Das Verhalten ist richtig (ein Merker über den Neustart wäre eine zweite Wahrheit, und ein nachts neu gestarteter Server käme nie zum Leeren), der Satz daneben nicht.
