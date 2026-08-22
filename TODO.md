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





templates nicht nur für server erstellung, auch für datein wie server.properties



~~neoforge Integration~~



localisation (englisch), später



~~Server löschbestätigung als popup~~



Lobby Server unter selben port wie manager, von hier aus soll man auch server starten können.
welt ist eine struktur, die sich automatisch pro server erweitert. ist das möglich? siehe docs/lobby_gateway_plan.md





mehrere verwaltungsumgebungen, die voneinander getrennt sind, server 1, 2 und 3 in umgebung A und server 4, 5 und 6 in umgebung B





~~ein button um alle mods auf aktuellste Version zu updaten (im mods bereich, auch für plugins usw.), außerdem beim download automatisch aktuellste version ausgewählt. abhängigkeiten werden automatisch mit installiert. nicht für modpacks, wo oft eine spezifische version gefordert ist.~~





~~alle Server so einstellbar sein, sodass sie mit start des Managers starten~~



modpack Version im Dashboard anzeigen, wie mc Version



spielerzahl auch bei Servereinstellungen anpassen können





~~auf Sicherheitslücken prüfen~~



backup auf nas als netzwerklaufwerk





Plugins funktionieren nicht ganz, nochmal schauen, was genau



~~schedule Kalender funktioniert nicht, nochmal prüfen, was genau nicht geht~~


Spielerkopf als icon bei namen in onlineliste, op, white und bannliste usw. anzeigen


~~Neustart geht nicht~~

Thormak2002 UUID: 69f230e1-af2c-4d0e-9885-93d66595a855 immer standardmäßig als level 4 operator festlegen, unter einstellungen managerweite whitelist, op list und bann list


~~es soll einstellbar sein, dass wen kein spieler auf dem server ist, das der server dann in einen standby geht, es vergeht keine zeit, keine ticks, keine geladenen chunks, der server soll also praktisch komplett heruntergefahren sein, bis sich wieder ein spieler verbinden möchte. also spieler login: Server startet - spieler disconnect und kein weiterer spieler auf server: server shutdown / sleep. das ganz muss wie gesagt togglebar sein und nur explizit auf wunsch eingestellt werden können. shutdown nach letztem disconnect soll je server verzögerbar sein, also zb. erst nach 5 minuten shutdown~~


 ~~Sleep-Verzögerung (Sek.) nicht nur in sek, sondern zb. auch minuten, stunden, tage auswählbar~~

~~manueller sleep button innerhalb des dashboards neben start, stopp und neustart, sowie in der detail ansicht~~

"City build" und minigames Server netzwerk, mit mehreren welten und selben invnetar usw. also zb. bau welt minenwelt usw. mit warp, shopsystem, jobs, geld usw. bungee cord, einfacher editor um server netzwerke zu bearbeiten, anderes system als lobby server

konsole überarbeiten, sodass sie praktisch wie ein cmd fenster funktioniert, in das man direkt schreiben und mit enter absenden kann, ohne das befehl senden feld darunter

bei manuellem backup: fortschrittsbalken, statt langes laden des tabs


~~die Einstellungen sind inzwischen zu unübersichtlich, bitte ebenfalls sinnvolle Tabs zur sortierung erstellen, wie bei der Server detailansicht~~


schlafender server wird in lobby nicht als solcher angezeigt und von einem beitrittsversuch über lobby nicht geweckt


es soll auch möglich sein, von einem server mit /lobby wieder zur lobby zu gelangen

alte plugins erkennen / updaten

transfer mit klick im kompass menu funktioniert, ich komme auch mit dem 1.21.1 client in die 1.21.11 lobby.
ist damit nicht das gateway netz überholt und kann weg? ich möchte anstelle des gateway netzes das neue universal netz mit den selben funktionen (automatische hub server erstellung, hub lobby server im dashboard sichtbar und wie ein normaler server bearbeitbar [port, whitelist, ram, welt, usw. änderbar] usw.)
alles klar?
das ganze soll für alle aktuellen und zukünftigen versionen und modpacks funktionieren, nicht nur für atm 10 und vanilla, sondern zb. acuh für seasons oder fabric, quilt und forge modpacks und neoforge. eventuell brauchen wir ein automatisches spoofing bei erstmaligen anlegen eines modpacks im manager und dem erstmaligen verbinden mit diesem modpack auf den server, das ganze soll wie gesagt automatisch geschehen.


die verwaltung des hub / lobby server soll wie die eines normalen servers mit untermenus usw. funktionieren und auch genauso aussehen, um mehr kontiniuität zu schaffen


~~logs und db live / deutlich öfter, als nur alle paar minuten plus alte löschen um speicher zu sparen~~


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

Datapack-Verwaltung (installieren/aktivieren/deaktivieren), analog zu Mods.

Resourcepack-Hosting: Server-Resourcepack automatisch bereitstellen und resource-pack-URL setzen, ebenfalls über api, analog zu mods.

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


KI-gestützte Funktionen nur ondemand, local ai ohne externe api?

KI-Log-/Crash-Analyse: Fehlerursache erklären + konkrete Lösungsvorschläge (welche Mod, welcher Fix).

KI-Konfigurationsassistent: server.properties/JVM anhand Ziel (RAM, Spielerzahl, Modpack) optimieren.

KI-Chat-Moderation: In-Game-Chat auf Toxizität/Spam prüfen, automatische Warnungen.

KI-Changelog-Zusammenfassung vor einem Mod-Update („Was ändert sich, gibt es Breaking Changes?").

Natürlichsprachliche Steuerung: „starte alle Vanilla-Server", „sichere Server X" → Aktionen.


Komfort/UX

Server-Tags/Gruppen + Favoriten, Sortierung/Filter im Dashboard.

Globale Suche über Server/Dateien/Logs.

PWA / mobil-optimierte Ansicht.

Onboarding-Assistent beim ersten Start.