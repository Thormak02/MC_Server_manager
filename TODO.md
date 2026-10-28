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

getrennte nutzungsumgebungen, folgendes modell:
nutzer / Organisation modell: ein nutzer kann eine oder mehr organisationen besitzen, verwalten und delegieren, einer orga können mehrere nutzer angehören. rollenverwaltung in orga, aber für super admins immer zugriff auf alle und volle rechte
später: monetarisierung: server, orgas usw. vermieten, kosten nach kapazität, leistung und anzahl an orgas und server



Ideen aus dem Urlaub (09/2026), für viel später:

~~Graceful Rejection: ERLEDIGT als Vorab-Prüfung. Statt den Spieler wegzuschicken und danach aufzufangen (nach einem nativen Transfer ist die Lobby-Verbindung weg und kann ihn nicht mehr erreichen), wird VOR dem Wechsel gefragt - dann bleibt er einfach in seiner Lobby und liest den Grund im Chat. Eine Wahrheitsquelle (`lobby_service.check_join_allowed`) für BEIDE Lobbys: der Python-Hub ruft sie direkt auf, die Bukkit-Lobby über den lokalen TCP/JSON-Endpoint (`lobby_api_service`, Token, nur 127.0.0.1) - dadurch identische Texte, egal mit welchem Client. Greift bei Kompass/Command/Schild/Portal und auch bei `/lobby`. Geprüft werden: Server offline, Ban (mit Grund), Whitelist, voll, sowie per Status-Ping "läuft zwar, nimmt aber noch keine Verbindungen an". Überall fail-open - eine kaputte Prüfung darf niemanden aussperren.~~

~~Offen dazu: Modpack-Abgleich vor dem Wechsel (der Ping liefert bei Forge/NeoForge eine Mod-Liste - damit wäre "falsches Modpack" vorab erkennbar).~~ Weiterhin offen: Resourcepack-Abgleich (der Client meldet sein Pack nicht), der allererste Join (da gibt es noch keine Lobby zum Zurückkehren) und eine Ablehnung, die erst NACH dem Transfer passiert (z.B. ein Plugin auf dem Ziel) - dafür bräuchte es einen echten Proxy in der Verbindung.

Mobile Verwaltung: MCSM auch als App-Version + mobile Web-App zur Verwaltung. Selbes Backend, immer selber Stand wie die Web-Version. Dünne native App (PWA / Wrapper), die selbst nicht aktualisiert werden muss - alles läuft über die Web-App. Evtl. eigenes, für Mobile optimiertes Frontend. Außerdem die bestehende Website für mobile Browser optimieren. (erweitert die vorhandene "PWA / mobil-optimierte Ansicht")

Server-Vermietung: den Manager als Basis für Server-Vermietung schaffen - z.B. Bezahlmodelle, RAM- und Speicherzuweisung, Selbstverwaltung usw. URL-Schema: nutzername.servername.domain.de/... oder nutzername.domain.de/servername. (verwandt mit "mehrere getrennte Verwaltungsumgebungen" weiter oben)


live übersicht über map, spieler position, gebäude, inventar usw. Dynmap? interaktiv aus der vogelperspektive, pro server zuschaltbar und nach auswahl über link öffentlich einsehbar


rechte verwaltung: nutzern und nutzergruppen je per checkbox berechtigungen zuweisen


push benachrichtigungen auf handy und pc zb. bei crash


ai analysen für crashes usw

ai modpack / datapack / pluginpack / server generator und ersteller / modpack ersteller (modpack und quests und custom rezepte usw.) mit claude API -> curseforge code / link für installation



~~mir ist außerdem grade aufgefallen: es funktionieren nur vanilla tweaks, aber vanilla tweaks crafting rezepte werden nicht erkannt und nicht zur auswahl verfügbar~~

~~/lobby / /server lobby geht nicht~~


manager kann auch server pc restarten

~~zb. spigot oder andere pluginloader configs bearbeitbar machen~~



MOTD editor in Server einstellungen mit Farben, allen optionen usw. mit einer vorschau


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

Nicht Code, sondern Einrichtung: AdvancedBackups 3.6 ist Legacy ohne api-version; bei online-mode=false hinter Velocity muss der Serverport von außen gesperrt sein.


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
