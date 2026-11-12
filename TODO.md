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

Offen: drei Lesefunktionen für den Weltnamen geben drei verschiedene Antworten, eine wirft bei Latin-1 - auf den einen Properties-Leser zusammenziehen.

~~Der Assistent kann in Archive sehen: Jars, Datapack-Zips, Resource Packs. Nur fuer Admins.~~

~~op, deop, pardon und whitelist off nur fuer Admins - an allen vier Tueren (Konsole, Zeitplan, Zugriffsseite, Dateieditor).~~

~~Pfadzugriff für Super-Admins: überall auf dem Managerrechner, nicht nur im Serverordner.~~

~~Die Grenze dazu steht an EINER Stelle: Geheimnisse bleiben zu (.env, DB, .git, Schlüsselspeicher), kein Schreibweg in den Programmcode, nichts dicht unter einer Laufwerkswurzel, kein Serverordner, Abzweigungen werden aufgelöst UND danach geprüft.~~

- ~~Freimarken Schritt 5 im Spiel prüfen: zwei Spieler, fremder Grund.~~ 31.10. bestanden.
- ~~Auf dem Testserver plugins/Freimarken/config.yml mit den MariaDB-Zugangsdaten füllen.~~ Startprobe durch.
- **Einmal auf dem Datenbankrechner skripte/mariadb_einrichten.bat erneut ausführen.** Es legt freimarken_test an; ohne das schlägt :daten:test absichtlich fehl. Vorhandene Passwörter bleiben unverändert.
- ~~Weltgrenze einziehen, bevor zum ersten Mal echt gespielt wird.~~ 31.10.: Stadtgrenze 42 Parzellen je Achse (-21..20), WorldBorder 2.989 um -3,5/-3,5, Einstellung welten.marktstadt.kante. Wandert nur nach außen.
- Marktstadt vorgenerieren, bevor echt gespielt wird: Mitte -3,5/-3,5, Radius 1.495, 35.344 Chunks, 36 Regionen im Feld - auf der Platte liegen mehr Dateien, die Dateizahl taugt nicht als Pruefung. Reihenfolge ist Pflicht - erst Grenze, dann vorgenerieren.
- Manager: Sicherung/Umzug muss world_border.dat mitnehmen. Sie liegt NICHT in der level.dat, sondern in dimensions/<namensraum>/<welt>/data/minecraft/.
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
- Der Abenteuermodus erlaubt aber das OEFFNEN von Truhen. Schritt 8 braucht darum einen Truhenschutz fuer Altfurt auf Quader-Grundlage - Plan 6.1 hat ihn vorhergesehen.
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
- Offen in C2: nichts. Als Naechstes C3 - Angebotspreis, Kauf, Geld von Spieler zu Spieler (das Abnahmekriterium aus Plan 15).
  - D: BUERGER. Wer ein Grundstueck in der Stadt hat, ist Buerger; Liste und was ein Buerger im Stadtgebiet darf.
  - E: KASSE UND AUFLAGEN. Teleporter und Ladenlizenz erst ab einem vergebenen Grundstueck und einer Regelwand; Verfallsregel nach zwoelf Monaten ohne Besuch und ohne Buerger (Plan 16).
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
- Offen und benannt: Es gibt KEINEN Betreiberbefehl fuer Geld. Fuer Proben und fuer den Ernstfall (ein Spieler verliert Mark durch einen Fehler) geht es heute nur ueber die Datenbank oder ueber eine Einstellung. Ein /freimarken geld <spieler> <betrag> mit eigenem Buchungsgrund waere der Weg.
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
- Offen, unveraendert: /markt liste wirft bei stummer Datenbank (die Zeile ueber das heutige Freie fragt die Kasse). War vorher auch so; ein eigener Satz dafuer fehlt.
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
- Offen, Sprachdatei: Eine Umbruchmarke in einem Satz, der ueber Sprache.eineZeile geht, laesst den Aufbau werfen - der Spieler liest dann GAR NICHTS. Braucht ein eigenes Merkmal am Satz.
- Pruefen, Sprachdatei: Nach dem ersten Start liegen beide Dateien im Plugin-Ordner, /mark verlauf sieht aus wie vorher. Dann eine Zeile samt aller Ebenen darueber nach sprache.yml kopieren, Farbe aendern, /freimarken neuladen: die Antwort nennt, wie viele Saetze von dir sind.
- Pruefen, Sprachdatei: Eine kaputte Zeile in sprache.yml, etwa ein offenes <gold> - GAR NICHTS wird uebernommen, die Klage nennt Zeile und Spalte, die bisherigen Saetze gelten weiter.
- Pruefen, Sprachdatei: Die neun Befehlsbeschreibungen in /help kommen aus der Datei, aendern sich aber erst nach einem Neustart.
- Offen, Sprachdatei (nicht im Spiel geprueft): dass die Vorlage bei jedem Start ueberschrieben wird, sprache.yml nie angetastet, und dass SnakeYAML unter Paper zur Laufzeit erreichbar ist.
- Offen: /rang gibt es noch nicht. Sobald er da ist, gehoert er ins Beispiel von hinweise.rang-werbung in der config.yml.
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
- Offen, bekannt und nicht neu: Jede ANDERE Kiste in Altfurt steht ebenfalls jedem offen (der Abenteuermodus erlaubt das Oeffnen). Die Freie Truhe lebt davon; fuer den Rest der Halle fehlt der Truhenschutz auf Quader-Grundlage.
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
