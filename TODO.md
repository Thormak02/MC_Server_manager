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

- Heute: EINE Domäne (network_domain), Server tragen nur ein Label (gateway_hostname), "Standard" gilt global. Künftig: Domäne als eigene Entität; Server UND Umgebung können je eine bekommen, jeweils auch mit eigener Subdomain.
- Routing: das Gateway muss die längste passende Domäne abschneiden statt der einen festen. Eindeutigkeit dann über (Domäne, Label) statt über das Label allein - sonst kollidieren zwei "lobby" in verschiedenen Domänen.
- "Standard" wird je Domäne statt global: freimarken.de ohne Label muss woanders landen als mc.friedrich-dietrich.de ohne Label.
- DNS: alle Domänen auf dieselbe IP, A-Record genügt, solange das Gateway auf 25565 sitzt. Abweichender Port braucht SRV (_minecraft._tcp.<name>).
- Zieht mit: public_base_url ist global, landet aber als Resource-Pack-URL in server.properties. Bei eigener Domäne je Server sollte sie aus DIESER Domäne kommen, sonst lädt der Spieler von einer fremd aussehenden Adresse.
- Web (Spielerseite, Manager) braucht je Domäne ein Zertifikat. Der Spielteil nicht - Minecraft spricht kein TLS.
- Velocity-forced-hosts müssen dieselben Namen kennen, sobald ein Unterproxy dazukommt.
- hängt an "getrennte Verwaltungsumgebungen" (Domäne je Orga) und an "Server-Vermietung" (URL-Schema)


getrennte Verwaltungsumgebungen - zuerst für die eigenen Server mit Helfern, als Grundlage für späteres Hosting:

- Nutzer/Orga-Modell: ein Nutzer besitzt, verwaltet und delegiert mehrere Orgas; eine Orga hat mehrere Nutzer; Rollenverwaltung je Orga. Super-Admin hat immer Zugriff auf alles.
- Umgebung (eigene Servergruppe: Freimarken, ATM10, Test) und Kunde (Vertrag, Rechnung, Kontingent) sind ZWEI Entitäten. Zusammengelegt wird man selbst Kunde Nr. 1 - getrennt modellieren.
- Billige Hälfte: Entität + server.umgebung_id + Mitglieder mit Rollen. Teure Hälfte: die ~30 globalen Einstellungen (Speicherwurzeln, Netzport/-modus, Gateway, Velocity, Hub-Lobby) je Umgebung umhängen - erst nötig, wenn es ein zweites echtes Netz gibt.
- Auslöser für den Bau: der erste Helfer, der nicht alles sehen soll. Vorher gibt es nichts zu gruppieren.
- Spieldaten der Spielserver brauchen KEINE Mandantenspalten - Trennung läuft über eine Datenbank je Netz.
- Bei Fremdkunden kippt "Super-Admin sieht alles" ins Datenschutzrecht: dann Auftragsverarbeiter, Zugriff nur auf Weisung und protokolliert.
- hängt zusammen mit "rechte verwaltung" und "Server-Vermietung" weiter unten

später: monetarisierung: server, orgas usw. vermieten, kosten nach kapazität, leistung und anzahl an orgas und server



Ideen aus dem Urlaub (09/2026), für viel später:

~~Graceful Rejection: ERLEDIGT als Vorab-Prüfung. Statt den Spieler wegzuschicken und danach aufzufangen (nach einem nativen Transfer ist die Lobby-Verbindung weg und kann ihn nicht mehr erreichen), wird VOR dem Wechsel gefragt - dann bleibt er einfach in seiner Lobby und liest den Grund im Chat. Eine Wahrheitsquelle (`lobby_service.check_join_allowed`) für BEIDE Lobbys: der Python-Hub ruft sie direkt auf, die Bukkit-Lobby über den lokalen TCP/JSON-Endpoint (`lobby_api_service`, Token, nur 127.0.0.1) - dadurch identische Texte, egal mit welchem Client. Greift bei Kompass/Command/Schild/Portal und auch bei `/lobby`. Geprüft werden: Server offline, Ban (mit Grund), Whitelist, voll, sowie per Status-Ping "läuft zwar, nimmt aber noch keine Verbindungen an". Überall fail-open - eine kaputte Prüfung darf niemanden aussperren.~~

~~Offen dazu: Modpack-Abgleich vor dem Wechsel (der Ping liefert bei Forge/NeoForge eine Mod-Liste - damit wäre "falsches Modpack" vorab erkennbar).~~ Weiterhin offen: Resourcepack-Abgleich (der Client meldet sein Pack nicht), der allererste Join (da gibt es noch keine Lobby zum Zurückkehren) und eine Ablehnung, die erst NACH dem Transfer passiert (z.B. ein Plugin auf dem Ziel) - dafür bräuchte es einen echten Proxy in der Verbindung.

Mobile Verwaltung: MCSM auch als App-Version + mobile Web-App zur Verwaltung. Selbes Backend, immer selber Stand wie die Web-Version. Dünne native App (PWA / Wrapper), die selbst nicht aktualisiert werden muss - alles läuft über die Web-App. Evtl. eigenes, für Mobile optimiertes Frontend. Außerdem die bestehende Website für mobile Browser optimieren. (erweitert die vorhandene "PWA / mobil-optimierte Ansicht")

Server-Vermietung: den Manager als Basis für Server-Vermietung schaffen - z.B. Bezahlmodelle, RAM- und Speicherzuweisung, Selbstverwaltung usw. URL-Schema: nutzername.servername.domain.de/... oder nutzername.domain.de/servername. Setzt die getrennten Umgebungen voraus, ist damit aber erst zu etwa einem Zehntel erledigt - dazu kommen Kontingente und Grenzen, Selbstbedienung beim Anlegen, Abrechnung, Verfügbarkeitszusagen, Supportpflicht und die Rolle als Auftragsverarbeiter.


live übersicht über map, spieler position, gebäude, inventar usw. Dynmap? interaktiv aus der vogelperspektive, pro server zuschaltbar und nach auswahl über link öffentlich einsehbar


rechte verwaltung: nutzern und nutzergruppen je per checkbox berechtigungen zuweisen - frei anpassbar, je Rolle UND je Umgebung. Ersetzt perspektivisch die feste Rollenliste (super_admin/admin/moderator/view_only) plus ServerPermission durch EINE Stelle. Teil der getrennten Umgebungen.


push benachrichtigungen auf handy und pc zb. bei crash


ai analysen für crashes usw

ai modpack / datapack / pluginpack / server generator und ersteller / modpack ersteller (modpack und quests und custom rezepte usw.) mit claude API -> curseforge code / link für installation


Automatisches löschen alter backups. komprimieren? zu viel speicherplatz nötig.



~~mir ist außerdem grade aufgefallen: es funktionieren nur vanilla tweaks, aber vanilla tweaks crafting rezepte werden nicht erkannt und nicht zur auswahl verfügbar~~

~~/lobby / /server lobby geht nicht~~


manager kann auch server pc restarten

~~zb. spigot oder andere pluginloader configs bearbeitbar machen~~



MOTD editor in Server einstellungen mit Farben, allen optionen usw. mit einer vorschau

eigener minecraft launcher mit manager server integriert mit curseforge und modrinth integration


~~KI-gestützte Funktionen nur ondemand, local ai ohne externe api?~~
~~Beantwortet: externe Claude-API gesetzt, lokale Modelle nur als Rückfall. Jeder Aufruf ondemand, abschaltbar, mit Monatsdeckel.~~
~~Beim Umsetzen zu beachten: Budget pro Monat deckeln und den Verbrauch sichtbar machen (sonst frisst eine Crash-Analyse-Schleife still das Guthaben); API-Schlüssel wie die übrigen Geheimnisse behandeln, nicht in die DB im Klartext; jeder KI-Aufruf ondemand und abschaltbar; Logs/Crash-Reports vor dem Versenden auf Spielernamen und IPs prüfen.~~
~~Rate Limit hinzufügen + missbrauch verhinden durch Themen lockin~~
~~API Anbieter einfach änderbar~~
~~API Keys nur in .env und die .env soll durch ein menufenster beschrieben werden, wie zb. bei der CurseForge API~~

~~Fundament: Schlüssel DPAPI-verschlüsselt (nicht .env), Monatsdeckel + Verbrauchsanzeige, Stunden-/Tageslimit, Themen-Sperre, IPs/Geheimnisse raus vor dem Versand, ab Moderator, Anbieter austauschbar.~~

~~KI-Log-/Crash-Analyse: Fehlerursache erklären + konkrete Lösungsvorschläge (welche Mod, welcher Fix).~~
~~Erledigt: Logs-Seite, Knopf "Analysieren". Gesendet wird nur Kopf + Fehlerstellen + Ende, Vorschau "Was wird gesendet?" kostet nichts. Crash-Reports und .log.gz wählbar.~~

~~KI-Konfigurationsassistent: server.properties/JVM anhand Ziel (RAM, Spielerzahl, Modpack) optimieren.~~
~~Erledigt: Dateieditor bei offener .properties. KI füllt nur das Formular, gespeichert wird von Hand. level-name/level-seed/Ports/online-mode gesperrt. Noch offen: KI-Vorschläge für YAML (der Assistent selbst kann YAML jetzt).~~

~~KI-Chat-Moderation: In-Game-Chat auf Toxizität/Spam prüfen, automatische Warnungen. -> nicht gewünscht~~

~~KI-Changelog-Zusammenfassung vor einem Mod-Update („Was ändert sich, gibt es Breaking Changes?").~~
~~Erledigt: Inhalte-Seite, Knopf "Was ändert sich?". Liest die Changelogs ALLER Fassungen zwischen installiert und Ziel, nicht nur der Zielversion. Ohne Changelog wird die KI nicht gefragt.~~

~~Natürlichsprachliche Steuerung: „starte alle Vanilla-Server", „sichere Server X" → Aktionen.~~
~~Erledigt über den KI-Assistenten: starten/stoppen/neustarten, sichern, Konsolenbefehl, Einstellung setzen. Noch offen: mehrere Server in einem Auftrag - es wird eine Aktion je Bestätigung ausgeführt.~~

~~eine KI integration, die fragen beantworten kann und auch cowork mößig aufgaben durchführen kann, nicht nur vorgefertigte commands, sondern freie bearbeitung und freies prompting wie claude code cowork auf dem rechner. aufgaben wie brarbeite diese config datei oder tue etwas in den einstellungen usw. rechte dürfen dadurch nicht umgehbar sein~~
~~Erledigt: eigene Seite KI-Assistent, 18 Werkzeuge (8 lesen, 10 verändern). Verändernde nur als Vorschlag, Ausführung prüft Werkzeug/Server/Recht neu gegen die DB. Beschreibung formuliert der Manager, nicht das Modell. Eine Aktion auf einmal.~~
~~Gesprächsverlauf in der DB, Liste links, anheften/umbenennen/löschen. Verlauf kommt vom Server, nicht aus dem Browser.~~
~~Antworten als Markdown dargestellt (eigener Darsteller, nur DOM-Knoten - kein HTML aus Modellausgabe).~~


~~die KI soll auch zugriff auf die ordnerstruktur, die datein und logs haben. allerdings nur innerhalb des managers, nicht darüber hinaus, allerdings muss hierbei die sicherheit beachtet werden, dass nutzer zb. keine schadsoftware einführen können oder informationen erhalten können, die sie nicht erhalten dürfen~~
~~Erledigt: dateien_auflisten, datei_lesen, log_ansehen, inhalte_auflisten, backups_auflisten. Nur innerhalb des Serverordners (aufgelöster Pfad, keine Symlinks/.. hinaus), nur Textdateien, nur Server, die der Nutzer sehen darf.~~
~~Keine Schadsoftware möglich: der Agent kann keine Datei anlegen oder hochladen. Geschrieben wird nur über einstellung_setzen (geprüfte Schlüssel) und Inhalte aus Modrinth/CurseForge - beides mit Rechteprüfung und Bestätigung.~~

~~Nach gegnerischer Prüfung 19 Fehler behoben: Fragefeld war unbegrenzt + ungesäubert, Geldschranken zählten erst nach der Antwort (parallele Aufrufe kamen durch), Geheimnisse mitten in Logzeilen blieben stehen, Logauswahl verlor Zeilen bei freiem Budget, analysiert wurde die falsche Datei.~~

~~Speicher-/CPU-Anzeige war für jeden Server falsch: gemessen wurde die cmd.exe-Hülle (~7 MB), nicht Java. CPU stand immer auf 0. Jetzt ganzer Prozessbaum.~~

~~Doppelte VanillaTweaks-Datapacks: Löschen der alten Datei scheitert bei laufendem Server, der Fehlschlag wurde verschwiegen. Minecraft lud beide. Jetzt wird es gemeldet.~~

~~declared_mc_version passte nicht zur Datei (1.21.2 auf 26.2-Server) - "schon aktuell" griff nie, bei jedem Update wurde alles neu erzeugt. Gruppe kommt jetzt aus dem Dateinamen.~~

~~op, deop, pardon, pardon-ip und whitelist off nur für Admins - geprüft in send_console_command, gilt also auch für die Konsolenseite und den Zeitplan. Namensraum (minecraft:op) und execute...run op werden mit erkannt.~~

~~Nicht Code, sondern Einrichtung: AdvancedBackups 3.6 ist Legacy ohne api-version; bei online-mode=false hinter Velocity muss der Serverport von außen gesperrt sein.~~


~~Zeitplan-Jobs liefen als "Manager selbst" und umgingen die Admin-Sperre komplett: jeder mit can_console konnte sich per Job OP geben. Jobs haben jetzt created_by_user_id, geprüft beim Anlegen und beim Ausführen; Anlegen wird protokolliert.~~

~~Zugriffsseite schrieb ops.json/banned-*.json/server.properties direkt - der dauerhafte Weg war mit can_edit_files offen. Jetzt dieselbe Admin-Schranke vor dem Schreiben, auch für das OP-Level.~~

~~KI-Assistent war bei vielen angehefteten Gesprächen dauerhaft kaputt: das Aufräumen löschte das gerade angelegte Gespräch, jede Frage endete im 500er.~~

~~view_only schrieb Gesprächszeilen, bevor die Rollenprüfung griff; die /api/ki/chats-Routen prüften die Rolle gar nicht.~~

~~Markdown-Darsteller: ReDoS, 6-32 s eingefrorener Tab. Jetzt ein Durchgang statt 500, schlimmster Fall 2,7 ms.~~

~~Agent installierte Plugins auf Fabric-Servern und meldete Erfolg; Resource Packs liefen in den Datapack-Zweig und brauchten kein Dateirecht.~~

~~VanillaTweaks: frisch installierte Packs wurden beim ersten Update unnötig neu geholt (Gruppe stand in version_label), Crafting Tweaks bei jedem Lauf, Resource Packs nie.~~

Offen: Testisolation. schedule_service war im conftest nicht neu geladen und fragte die echte Entwicklungs-DB ab; mindestens 14 weitere Dienste halten dieselbe SessionLocal-Modulreferenz.

~~Assistent für spigot.yml, bukkit.yml, paper-*.yml und Plugin-Configs. Ersetzt nur den geänderten Wert im Text - Kommentare, Reihenfolge und Anführungszeichen bleiben. Ohne Änderung ist die Datei danach byteweise gleich.~~

~~Dateien ohne Assistenten sagen jetzt warum, statt wortlos den Freitext zu zeigen.~~

~~Sortierung/Suche gingen auf JEDEM Rückweg zur Dateiseite verloren, nicht nur beim Umschalten: Speichern, Upload, Löschen, Ordner anlegen, Download-Fehler. Jetzt ein gemeinsamer Weg zurück.~~

~~Dateieditor war die dritte Tür zu op/pardon/whitelist off: ops.json und banned-*.json ließen sich mit can_edit_files schreiben, hochladen, neu anlegen und löschen. Auch über die API und über das KI-Werkzeug einstellung_setzen. Jetzt dieselbe Regel wie Konsole und Zeitplan.~~

~~YAML-Alias-Geflecht: 486 Byte ergaben 137 s Rechenzeit pro Seitenaufruf, auslösbar mit reinem Leserecht. Zu tiefe Verschachtelung endete im 500er.~~

~~Serverliste zeigte bei jedem Eintrag die Lobby-MOTD, 0/100, immer grün - auch bei ausgeschaltetem Server. Das Gateway schob auch Status-Pings in Velocity, und das beantwortet sie wegen ping-passthrough=DISABLED selbst. Jetzt antwortet der gemeinte Server.~~

~~Adresse ohne Route zeigte die Lobby statt "kein Server für diese Adresse" - ein Tippfehler sah aus wie ein laufender Server.~~

~~Status-Pings wurden als Join weitergeleitet: toter Zielport antwortete mit einem Login-Disconnect, den der Client als Status-JSON liest. Kaputter Listeneintrag statt offline.~~

~~Lobby → Paper-Server blieb stumm. Minecrafts pause-when-empty-seconds (Vorgabe 60) parkt den Server, lässt den Port aber offen: überall "online", nimmt aber niemanden auf. Velocity wartete 185 s. Wird jetzt beim Start abgeschaltet - der Schlafmodus des Managers macht das sichtbar.~~

~~Lobby-Plugin konnte nach einem Update veraltet weiterlaufen: der Auto-Build lief im Hintergrund, die Server starteten vorher und nahmen das alte Jar mit. Danach ist die Datei gesperrt. Start wartet jetzt auf den Build; ein wirklich veraltetes Jar wird gemeldet statt verschwiegen.~~

~~/lobby-Rückweg setzte velocity_name ohne Typ- und Portprüfung - zweite Regel für dieselbe Frage. Jetzt eine gemeinsame.~~

Offen: NAS-Abgleich steht seit 6.10. Task mcsm-nas-sync ist aktiv und korrekt eingerichtet, letzter Lauf 6.10. 14:17 mit Ergebnis 0 - der Auslöser hängt, Ursache noch offen.

~~Offen: 1.21.11-spigot und 26.2-spigot haben kein Gateway und keinen Hostnamen - im Netzwerk nicht erreichbar.~~

~~Lobby-Wechsel (Kompass und /lobby) blieb stumm: Velocity wurde EINMAL geladen und nie wieder, blieb auf 26.2 waehrend das Netz auf 26.3 zog. ViaVersion uebersetzte still 26.3->26.2 und verschluckte dabei die bungeecord:main-Nachricht. /server ging, weil das Velocitys eigener Befehl ist.~~

~~Velocity-Jar folgt jetzt der hoechsten Version im Netzwerk (nicht der Lobby-Version) und wird bei einem Versionswechsel neu geladen. Fehlschlag wird gemeldet statt verschwiegen.~~

~~Velocity lief mit "irgendein Java ab 17". Das neue Jar (4.3.1) braucht Java 25 -> UnsupportedClassVersionError, Absturz im Minutentakt. Die noetige Java-Fassung wird jetzt aus dem Jar gelesen.~~

~~config-version der velocity.toml stand fest auf 2.7, das Jar erwartet 2.8 - wird jetzt aus dem Jar gelesen. bungee-plugin-message-channel steht ausdruecklich drin.~~

~~Zwei .bat-Dateien zum Starten/Stoppen des Managers (scripts/), fuer Desktop-Verknuepfungen. Stoppen beendet auch den uvicorn-Prozess, sonst blockiert er den naechsten Start.~~

Offen: Absturzschleife des Proxys gehoert aufs Dashboard, nicht nur ins Pruefprotokoll. Velocity starb zwei Stunden im Minutentakt, sichtbar war nur "Lobby startet gerade".

Offen: Via-Plugins werden still auf dem alten Stand gelassen, wenn es fuer die neue Version keinen Build gibt - ohne Hinweis.

~~Kompass-Wechsel auf ein Velocity-Backend ging nie: das "Connect" an den Proxy wird nicht quittiert, und niemand hat nachgesehen. Modpack-Ziele liefen immer ueber den nativen Transfer, deshalb fiel es nicht auf - der 26.3er war das erste Backend im Menue. Jetzt wird nach 2 s geprueft und notfalls nativ transferiert.~~

~~Vier Stellen im Klickpfad des Lobby-Plugins stiegen wortlos aus (Cooldown, kuerzlich abgesagt, Pruefung laeuft, Versand). Jede schreibt jetzt eine Logzeile.~~

~~Platzte die Join-Pruefung im Async-Task, blieb der Spieler fuer immer in der Sperrliste - sein Kompass war danach dauerhaft tot, ohne Meldung. Der umgebende catch deckte nur den Aufruf ab, nicht den Rumpf.~~

~~Via-Plugins: Rueckmeldung von install_velocity_plugins wurde weggeworfen (kein Build fuer die neue Version -> alte Jars bleiben still liegen), und gewaehlt wurde nach der Lobby- statt der hoechsten Netzversion.~~

~~Velocity 4 braucht Java 25 und ein anderes Konfigurationsformat ([ping-passthrough] als Abschnitt, config-version 2.9). Beides wird jetzt aus dem Jar gelesen statt festgeschrieben.~~

Offen: warum Velocity die "Connect"-Nachricht verwirft, ist ungeklaert. Kanal, Servername, Laenge und Konfiguration stimmen nachweislich; Velocity 4 bedient den Kanal laut eigenem Jar. Der Rueckfall macht die Funktion benutzbar, erklaert sie aber nicht.

~~MOTD-Editor in den Servereinstellungen: zwei Zeilen, Minecrafts 16 Farben, sechs Formate, eigene Farbe bei Paper, Zähler ab 45 sichtbaren Zeichen, Live-Vorschau im Stil der Serverliste.~~

~~Vorschau hält sich an Minecrafts Regeln: eine Farbe setzt Formate zurück, unbekannte Codes werden verschluckt, §x nur wo es wirkt. Nur DOM-Knoten, kein HTML aus dem Eingabefeld.~~

~~Schwer: über das MOTD-Feld ließ sich eine zweite Zeile in server.properties erzeugen und damit level-name/difficulty setzen - Python trennt an acht Zeichen mehr als \n und \r. Reichte can_control_server, wirkte beim zweiten Speichern.~~

~~server.properties hatte vier verschiedene Leser mit vier Auslegungen. Jetzt ein properties_service, der Javas Regeln umsetzt: Trennung am ersten ungefluchteten = oder :, volle Fluchtzeichen inkl. \uXXXX, Zeilenfortsetzung, Kodierungserkennung mit Latin-1-Rückfall, Schreibfehler als Warnung statt HTTP 500.~~

~~errors="ignore" löschte beim Speichern jedes Nicht-ASCII-Zeichen der ganzen Datei, wenn sie nicht UTF-8 war - auch in Werten, die niemand angefasst hat.~~

~~Zweite Anmeldung desselben Spielers: ersetzen oder abweisen, als Einstellung unter Netzwerk & Lobby. Vorgabe ersetzen, sonst scheitert der Lobby-Rückweg an "Du bist bereits mit diesem Proxy verbunden!".~~

Offen: drei Lesefunktionen für den Weltnamen geben drei verschiedene Antworten (lobby_service, transfer_service, server_service); eine wirft bei Latin-1. Gehört auf den properties_service zusammengezogen.

~~Der Assistent kann in Archive sehen: Plugin-/Mod-Jars, Datapack-Zips, Resource Packs - Inhaltsverzeichnis oder eine Datei daraus. Ohne Pfad kommt die Liste der Archive, weil dateien_auflisten nur Textdateien zeigt. Nur fuer Admins.~~

~~VanillaTweaks-Teilpakete waren unsichtbar: die Liste steht IM Archiv, beim Resource Pack ausserhalb des Serverordners. Jetzt lesbar und aenderbar (vt_auswahl_zeigen/-setzen), ohne den Dateizugriff aufzuweiten. Nur fuer Admins.~~

~~Ein Katalogausfall bei VanillaTweaks verschluckte die ganze Antwort, obwohl die Auswahl in der Datei steht und den Katalog nicht braucht.~~

~~Pfadzugriff für Super-Admins: auflisten, lesen, schreiben, löschen, verschieben - überall auf dem Managerrechner, nicht nur im Serverordner. Archivblick nimmt jetzt auch einen vollen Pfad (data/resourcepacks).~~

~~Die Regel dazu an EINER Stelle (admin_path_service.pruefe): Geheimnisse bleiben zu (.env, DB, .git, SSH/DPAPI-Speicher), kein Schreibweg in den Programmcode, nichts dicht unter einer Laufwerkswurzel löschen, kein Serverordner, Abzweigungen werden aufgelöst UND danach geprüft.~~

~~write_text kürzt die Datei beim Öffnen und scheitert erst danach am Kodieren - gemessen blieben von 18 Bytes null übrig. Jetzt wird zuerst kodiert.~~

~~Schnitt-Toleranz beim Dekodieren war zu gutgläubig: aus cp1252 "Grüße" wurde ein utf-8-taugliches "Gr". Ein echter Schnitt ist am Fehler-ENDE erkennbar, nicht an der Fehlerstelle.~~

~~Auch das LESEN eines freien Pfades wird protokolliert - bei Serverdateien genügt der Dateieditor als Spur, bei einem freien Pfad nicht.~~

~~Werkzeugergebnisse gehen als EINE JSON-Zeile hinaus, die Geheimnis-Muster hängen an (?im)^ - in einem einzeiligen Blob gibt es keinen Zeilenanfang. Zugangsdaten aus Configs gingen damit im Klartext an den Anbieter, gegen die Zusage, die das Projekt selbst testet. Jetzt am Rohtext gesäubert, vor dem Verpacken.~~

~~Entpackgrenze griff nur bei Deflate: bei bzip2/lzma entpackt zipfile ohne Grenze, 32 KB werden zu ~43 GB in einem bytes-Objekt. Fremde Verfahren werden jetzt abgewiesen.~~

~~VT-Resource-Pack-Katalog ist nicht flach - die Hälfte aller Teilpakete steckt in Unterkategorien und galt als "unbekannt". Ein Pack damit ließ sich nicht mehr ändern, und der Ausweg "dann raus damit" verlor es endgültig.~~

~~"Auswahl leer" und "Auswahl nicht lesbar" waren nicht unterscheidbar - hinzufügen ersetzte dann die ganze Zusammenstellung. Aus 21 Teilpaketen wurde eines.~~

~~Namensvergleich per .lower(): das Manifest führt "WavyWater", Nutzer und Anzeige "Wavy Water" (bei 670 von 672 Packs verschieden). "weg: Wavy Water" entfernte nichts, der Dialog sagte trotzdem es sei weg.~~

~~Fehler aus der Parameterprüfung erschien als Tatbeschreibung im Bestätigungsdialog, mit aktivem Ausführen-Knopf. Jetzt Hindernis.~~

~~Kleiner: zeigen und setzen rechneten mit verschiedenen VT-Gruppen; Paketname aus der resource-pack-URL ungeprüft ("C:x.zip" ist laufwerksrelativ); zweiter CT-Eintrag überschrieb still die Auswahl des ersten; Abzweigungen brachten fremde Archivnamen in die Liste; zlib.error/ValueError entwischten dem Archivleser.~~

~~Testlücken: Admin-Vorbehalt war nur beim ersten Werkzeug geprüft, Byte-Grenze nur behauptet (jetzt über tracemalloc gemessen), Crafting-Tweaks-Hälfte ganz ohne Test.~~

- ~~Freimarken Schritt 5 im Spiel prüfen: zwei Spieler, fremder Grund.~~ 31.10. bestanden: Schutz greift mit Hinweis in der Aktionsleiste, Mitbauer schaltet frei und zu, zweite Parzelle für 25.000 bezahlt, Parzelle -1/0 stimmt. In der Datenbank gegengeprüft.
- ~~Auf dem Testserver plugins/Freimarken/config.yml mit den MariaDB-Zugangsdaten füllen.~~ Startprobe durch: Treiber wird im Plugin-Klassenlader gefunden, Welt entsteht, Befehle sind angemeldet.
- **Einmal auf dem Datenbankrechner skripte/mariadb_einrichten.bat erneut ausführen.** Es legt freimarken_test an; ohne das schlägt :daten:test absichtlich fehl. Vorhandene Passwörter bleiben unverändert. Grund: Die Tests leeren vor jedem Test alle Tabellen und liefen vorher gegen das Spielschema.
- ~~Weltgrenze einziehen, bevor zum ersten Mal echt gespielt wird.~~ 31.10. gebaut: Stadtgrenze im Kern (42 Parzellen je Achse, -21..20), WorldBorder 2.989 um -3,5/-3,5, Bescheid AUSSERHALB, Einstellung welten.marktstadt.kante. Wandert nur nach außen.
- Marktstadt vorgenerieren, bevor echt gespielt wird: Mitte -3,5/-3,5, Radius 1.495, 35.344 Chunks, 36 Regionsdateien. Reihenfolge ist Pflicht - erst Grenze, dann vorgenerieren.
- Manager: Sicherung/Umzug muss world_border.dat mitnehmen. Sie liegt NICHT in der level.dat, sondern in dimensions/<namensraum>/<welt>/data/minecraft/.
- ~~Vor Schritt 6 entscheiden: Datenmodell aus §6.1?~~ 31.10. gebaut: art, eltern_id, zustand; x/z NULL-fähig; KEIN bereich-Quader (darfBauen bleibt Punktabfrage). Gruppenkind behält eigenen Eigentümer, Einzeländerungen daran verweigert TEIL_EINER_GRUPPE bis Schritt 6.
- ~~Schritt 6, Teil A: Kern, Ablage, Schutz.~~ 31.10. gebaut: Richtung, Rasterplan.imInneren, Besitzbuch.zustaendigerBesitz/zusammenlegen/auftrennen, BesitzLager.kinderVon/gruppeSetzen/auftrennen (Transaktion mit Wettlaufriegel), Schemaschritt 6 (ck_besitz_ort). 171 Tests. Mitbauer werden jetzt durchgeschrieben; nur aufgeben bleibt am Kind verweigert.
- ~~Schritt 6, Teil B+C: Befehle und Weltarbeit.~~ 1.11. gebaut: /parzelle zusammenlegen <richtung> [bestaetigen x z], /parzelle auftrennen [bestaetigen x z], Gruppenflaeche (Fugen im Kern), Stadtboden (die eine Blocktabelle), Wegbau (fuellen, zaehlen, zurueckbauen), Recht freimarken.gruppe.<n>. 226 Tests.
- ~~Entscheidung Auftrennen:~~ Weg kommt zurueck und raeumt, was darauf steht; die Warnung nennt die Zahl vorher, bestaetigt wird mit Nummer.
- ~~Spieltest Zusammenlegen/Auftrennen:~~ 1.11. gelaufen, Befehle, Gebuehr, Schutz und Zurueckbau stimmen.
- ~~Fehler aus dem Spieltest: Gruppe war an den Fugenenden nicht umrandet.~~ Im Rasterplan gewinnt der Weg (fuer Kreuzungen), also blieb je Ende ein 5 Bloecke breites Loch zur Strasse. Gruppenflaeche laeuft jetzt ueber die NAHT und nennt je Koordinate ohneGruppe und mitGruppe; Probe 'rundum geschlossen' im Kern. Je Fuge 596 statt 576 Bloecke.
- ~~Vier Fehler aus der eigenen Pruefung des Mauerring-Fixes:~~ 1.11. behoben. (1) Fuellen war nicht wiederholbar - ein angehaengtes Feld plattete die alte, bebaute Fuge auf y=62/63; ueberDieNaht laesst jetzt aus, was schon so lag. (2) Die Warnzahl zaehlte ab y=63, der Zurueckbau schrieb auch y=62 und darunter - jetzt dieselbe Spalte und dieselbe Sollform fuer beides. (3) Der Zurueckbau raeumte auch ueber den Mauerstuecken, die nie Bauland waren. (4) Grundgestein stand nur im Generator, der Zurueckbau haette Stein hingeschrieben; nach unten bricht die Schleife jetzt beim Sollblock ab, nicht beim ersten festen.
- ~~Drei Fehler aus der zweiten eigenen Pruefung:~~ 1.11. behoben, alle in der Zaehlung, zwei davon neu eingebaut. (1) Die Warnung zaehlte das Fuellgras mit, sobald es zu Erde verrottet - ein Schaf genuegte, dann meldete eine unberuehrte Gruppe einen Verlust. (2) Sie nannte Bloecke, die der Zurueckbau gar nicht anfasst (Pflaster aus dem Strassenblock, Stufe an der Mauerkrone). (3) Fehlstellen wurden als 'verschwinden' gemeldet, obwohl sie verfuellt werden - ueber einem leeren Becken das Gegenteil der Wahrheit. Jetzt Stadtboden.wertung mit drei Faellen und zwei getrennten Zahlen in der Warnung.
- ~~Die Paper-Schicht hatte keine Tests.~~ Jetzt StadtbodenTest (11 Proben) UND WegbauTest (10 Proben). Wegbau nimmt dafuer einen Weltgriff statt World - vier Methoden, also in einer Map nachstellbar (Probewelt). Staerkste Probe: Fuellen und Zurueckbauen lassen die Stadt Block fuer Block wie den Generator zurueck.
- ~~Zwei der ersten Proben konnten nicht rot werden:~~ eine verglich Material mit sich selbst, eine nutzte die gepruefte Regel als eigenes Orakel (bei entfernter Auslassung null Zusicherungen, gruen). Beide ersetzt. Mutationsprobe: alle 8 Regeln, die in diesem Schritt mal falsch waren, einzeln kaputtgemacht - alle 8 werden gefangen (Skript scratchpad/mutationsprobe.py).
- ~~Warnsaetze stimmten nicht zu ihren Zahlen:~~ gezaehlt wird die ganze Spalte, gemeldet war 'auf der Wegflaeche'; Verfuellung hiess 'unter der Flaeche', obwohl die Oberflaeche dazugehoert; und die Einzahl fehlte ('verschwinden 1 Bloecke'). Alle drei nachgezogen.
- Hinweis fuer kuenftige Bauten: Nach vielen Gradle-Laeufen in Folge bricht die SMB-Freigabe weg (Unerwarteter Netzwerkfehler, Cannot create directory) und Gradle nimmt danach veraltete Klassen - zwei Tests waren rot, obwohl die Quelle stimmte. Abhilfe: build/reports loeschen, fehlende build-Unterordner per mkdir anlegen, neu bauen.
- ~~Spieltest mit dem neuen Plugin:~~ 1.11. gelaufen und bestanden. Zusammenlegen, drittes Feld anhaengen, Umrandung an beiden Bandenden, Auftrennen und Raeumen - alles richtig.
- ~~Schritt 6, Teil D: Auftrennen einzeln.~~ 1.11. gebaut: /parzelle auftrennen loest das Standfeld heraus, /parzelle auftrennen alles trennt die ganze Gruppe. Ein tragendes Feld wird abgelehnt (Gruppenflaeche.bleibtGanzOhne, Zusammenhang ueber Kanten); bei zwei Feldern wird ganz aufgetrennt. Neue Ablage-Operation ausGruppeLoesen in allen Umsetzungen plus zwei Vertragsproben. Die Weltarbeit nutzt dieselbe Auslassung wie das Fuellen, nur umgekehrt - nur der Weg zum geloesten Feld kommt zurueck.
- ~~Die Gruppenbefehle hatten im Besitzbuch keine Proben~~ (nur die Ablage hatte ihren Vertrag) - deshalb wurde jeder Fehler dieses Schritts durch Nachrechnen gefunden. Jetzt sieben Proben fuer das Herausloesen.
- ~~Spieltest Teil D:~~ 1.11. gelaufen und bestanden. Tragendes Feld abgelehnt, Randfeld geloest, die bleibende Fuge blieb Bauland.
- ~~Fehler NACH dem Spieltest gefunden (eigene Pruefung, in c54686c schon drin):~~ Am Arm einer Kreuzung schrieb der Rueckweg den Rasterplan (WEG) statt der Mauer, die die bleibende Gruppe dort braucht - zwei 5 Bloecke breite Loecher, und der Eigentuemer durfte sie nicht selbst zumauern. Betraf 148 von 476 Herausloesungen im 3x3-Fenster. Der Spieltest konnte es nicht finden: die getestete Gruppe war ein L ohne Kreuzung. Jetzt fragt ueberDieNaht die anderen Flaechen nach ihrem Anspruch (beiDenAnderen); lagSchonSo entfaellt. Zwei neue Proben, Mutation faellt in 6 Tests auf.
- ~~Spieltest Quadrat (Kreuzungsarm):~~ 1.11. gelaufen und bestanden, 19 von 19 Koordinaten. Beide Mauerstreifen zurueck (x=-7 und z=-7, je 5 Bloecke plus Krone), Kreuzungsmitte wieder Pflaster, die Seiten zum geloesten Feld auch, beide Fugen des bleibenden L blieben Bauland.
- ~~Die Messung war dabei still kaputt:~~ Der Server verschluckt Konsolenzeilen, wenn zu viele auf einmal kommen - von 17 Abfragen kamen 3 an. Eine verschluckte Abfrage sagt WEDER ok NOCH falsch, das Ergebnis sah also aus wie 'alles in Ordnung'. messe_quadrat.sh fragt jetzt einzeln mit Pause und verweigert das Ergebnis, wenn Antworten fehlen.
- ~~Prueflauf ueber den ganzen Befehlspfad~~ (1.11., 17 Befunde, 10 von den Skeptikern widerlegt). Behoben, je mit Probe und Mutation: (1) ausGruppeLoesen schloss die GETEILTE Datenbankverbindung - eigener Fehler aus Teil D, jedes Herausloesen erzwang einen Neuaufbau im Hauptfaden; (2) die Rueckbuchung beim Zusammenlegen umfasste den Lesezugriff NACH dem Festschreiben - Gruppe da und Geld zurueck, und niemand haette es gemerkt; (3) SCHON_IN_GRUPPE meldete auch dann, wenn beide Felder in DERSELBEN Gruppe liegen; (4) 'Hier ist keine Parzelle' auf freiem Bauland, wo /parzelle info 'frei' sagt - jetzt NOCH_FREI; (5) /parzelle info bot eine Parzelle mit Preisschild an, obwohl der Rang ausgereizt war. 231 Tests.
- Neu in der config.yml: hinweise.rang-werbung - eine Zeile unter jeder Absage, die ein RANG verursacht (Kauf, Zusammenlegen, freie Parzelle am Limit). Vorgabe 'Mehr erlaubt ein hoeherer Rang.'; leer heisst kein Hinweis. Bewusst nicht fest eingebaut: Was ein Spieler tun muss, um mehr zu duerfen, weiss nur der Server.
- ~~Kaufbare Raenge geben jetzt einen Zuschlag~~ [1.11., entschieden]: Foerderer +2 Parzellen und +1 Feld je Gruppe, Stifter +4 und +2. Als ZUSCHLAG gebaut (freimarken.extra.parzellen.<n>, freimarken.extra.gruppe.<n>), nicht als zweite absolute Zahl - sonst haette ein zahlender Reisender am ersten Tag so viele Parzellen wie ein Buerger nach 40 Stunden.
- ~~Plan 8.4 verbot 'mehr Parzellen, mehr Flaeche' in einer Mitgliedschaft.~~ 1.11. entschieden: Der Zuschlag ist den BEZAHLTEN Raengen vorbehalten und gibt es nicht fuer Goldmark - ebenso zusaetzliche Heimatpunkte und Warps. Begruendung im Plan 8.4: vier Parzellen sind schon 135x135 Bloecke, wer mehr will, will bauen und nicht gewinnen; das Angebot richtet sich an Vielspieler und Traeger des Servers. Kosmetik bleibt erspielbar.
- Offen: Zahlen fuer zusaetzliche Warps je bezahltem Rang. Heimatpunkte stehen (Foerderer +2), Warps sind in der Rangtabelle noch nicht beziffert.
- Offen, niedrig: Wird die Marktstadt voll, bindet ein Stifter mit 8 Parzellen doppelt so viel Flaeche wie ein Buerger ohne Mitgliedschaft (1.764 Parzellen insgesamt). Nachzujustieren ueber das Recht, nicht im Programm.
- ~~Schritt 7 erfasst~~ [1.11., Pruefauftrag ueber Plan und Code]: Bauplan A-G steht (A Buchungsbuch, B Wirtschaftswerte + /freimarken neuladen, C Spielzeit und Tagesgrenze, D Berufe waehlen, E Verdienen, F Bundesmarkt, G Messstelle). Vier Fragen beantwortet: 2.000-5.000 Mark je SPIELER (nicht je Beruf), Tagesgrenze Mitternacht Ortszeit (Zone aus der config.yml), ein Block zahlt nur einmal (gesetzte Bloecke werden markiert), und Schritt 3 wird VOR Schritt 7 gebaut.
- ~~Schritt 3, Teil A: Welten aus der Liste.~~ 1.11. gebaut: config.yml fuehrt je Welt art (raster/gewoehnlich/leer), umgebung und kante. Weltenliste liest und KLAGT statt zu raten (eine fehlende art wuerde aus der Marktstadt eine Welt mit Baeumen machen), Weltenbau legt an und setzt die Grenzen, Grenzregel im Kern aus Stadtgrenze herausgeloest - sie gilt jetzt fuer jede Welt. getDefaultWorldGenerator gibt das Raster nur noch fuer die Marktstadt zurueck; vorher fuer JEDEN Namen. Spieltest: acht Welten entstehen mit den richtigen Umgebungen und Grenzen, 7 von 7 Messpunkten (Marktstadt weiter Raster, Kornmark und Altfurt ohne Raster, Kornmark und Nether mit gewoehnlichem Grundgestein). 258 Tests.
- ~~Schritt 3, Teil A geprueft~~ 2.11. [74 Agenten, 28 bestaetigte Befunde, alle behoben]: art haengt jetzt am Namen - raster traegt allein marktstadt, und bei falscher art entsteht die Marktstadt GAR NICHT (im Spiel belegt: "Unknown dimension freimarken:marktstadt", Plugin aus). Jede Welt traegt ihre art im PersistentDataContainer; "neu" heisst damit zum ersten Mal gesehen, nicht "war nicht geladen" - vorher wurde /setworldspawn bei JEDEM Start ueberschrieben und der Grenzmittelpunkt auf 0/0 zurueckgeholt (belegt: Erscheinungspunkt 500/70/500 hat den Neustart ueberlebt). Kante wird nach Typ und Spanne geprueft ("6000" war still 0, 6.000 ist 6, zu gross liess setSize mitten im Start werfen). Fehlende Eintraege nach einem Update werden benannt - vorher entstand nur die Marktstadt, stumm. Eine werfende Welt reisst den Start nicht mehr mit. Die Klage trennt Verkleinern vom Sprung; der Bestaetigungstext wirkte beim Verkleinern nie. Eine Einstellung, ein Leser: marktstadt.kante wurde an zwei Stellen mit zwei Rueckfaellen gelesen. Weltname end statt ende (Plan 4). 270 Tests.
- Offen in Schritt 3: wolkenwerder fehlt in der Liste (Leere ohne Startinsel, kommt mit Schritt 11); Spielmodus je Welt (Altfurt = Abenteuer) gehoert zu Teil C; Vorgenerierung ist Teil B (3-4 Stunden, 15-20 GB); wie ein Spieler ZWISCHEN den Welten reist, ist nirgends entschieden - ohne das steht alles da und niemand kommt hin.
- Naechstes: Schritt 3 - Welten anlegen, Grenzen, Vorgenerierung, Altfurt als Rohbau. Grund fuer das Vorziehen: In der Marktstadt gibt es kein Holz, kein Wasser, keine Erze und keine Nutztiere (der Generator schaltet Rauschen, Hoehlen, Dekoration und Mobs ab) - vier der sechs Grundberufe stuenden im Leeren.
- Offen aus dem Pruefauftrag, vor Schritt 7 zu klaeren: Die Preisstaffel steht als Konstante im Kern (25.000 / 10.000) statt in der config.yml; /freimarken neuladen sagt Plan 12a zu, gibt es aber nicht; das Startguthaben wirkt rueckwirkend, weil MariaKasse ohne Kassenzeile den eingestellten Wert zurueckgibt und die Zeile erst bei der ersten Buchung entsteht; parzellen.hoechstzahl: 4 gibt jedem Neuling das, was Plan 9 dem Buerger nach 40 Stunden gibt.
- Offen: /rang gibt es noch nicht. Sobald er da ist, gehoert er ins Beispiel von hinweise.rang-werbung in der config.yml - bis dahin nennt das Beispiel den Weg (rund 40 Spielstunden und eine bebaute Parzelle) statt eines Befehls, den es nicht gibt.
- Aufraeumen, wenn Schritt 6 abgeschlossen ist: Probeserver-Ordner im Temp loeschen (config.yml mit DB-Passwort im Klartext) und die Testdaten in der echten DB zuruecksetzen - die Parzellen 0/-1 (id 108) und -1/-1 (id 111) waren nur fuer den Test, und Thormak2002 hat Mark gutgeschrieben bekommen (Stand 200.000).
- Offen, niedrig: Die Spaltenschleife kann im aeussersten Fall ~707.000 Blockzugriffe in einem Befehl machen (vier Felder, voll bebaut und tief ausgehoben), und die Warnung ist ohne Bestaetigung beliebig oft ausloesbar. Ein Hohlraum unter einer stehengelassenen Decke wird bewusst nicht gefunden - siehe Plan 6.3.
- Offen (eigene Entscheidung): Ein abgebrochener Zurueckbau laesst sich nicht nachholen - die Gruppe ist in der Ablage weg, Gras bleibt auf oeffentlichem Weg liegen. Braeuchte einen Befehl, der die Welt der Ablage nachzieht.
- ~~CHECK-Bedingung (x IS NULL) = (z IS NULL).~~ 31.10. als Schemaschritt 6 gebaut. Die frühere Notiz war falsch: MariaDB 13.0 nimmt ADD CONSTRAINT IF NOT EXISTS für CHECK sehr wohl an - geprüft, zweimal hintereinander, mit Verhaltensprobe.
- §4.2 und §14 verlangen "Welt != Code": Weltschlüssel und Rasterplan stehen fest im Code, config.yml hat keinen Welt-Abschnitt. Vor Schritt 3 (acht Welten) umstellen.
- Noch nicht geschützt (Weltregeln, kein Spielerhandeln): Explosionen, Feuerausbreitung, Kolben über die Grenze, Flüssigkeiten von außen, Trichter in fremde Kisten.
- Datenbankzugriffe laufen im Hauptfaden. Zeitgrenzen sind gesetzt (socketTimeout 5 s) und der Schutz scheitert geschlossen, aber ein Wiederholungsversuch für Leseoperationen fehlt - ein stiller Verbindungsabbruch kostet eine Absage.
- Gradle auf der Freigabe: nach "clean" scheitert der nächste Bau daran, dass ein gelöschtes Verzeichnis noch nicht weg ist. Fehlende build-Verzeichnisse von Hand anlegen, dann geht es.
