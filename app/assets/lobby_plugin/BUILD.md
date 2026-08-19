# MCSMLobby-Plugin bauen

Die begehbare Transfer-Lobby. Der **fertige** `MCSMLobby.jar` liegt bereits hier und
wird vom Manager in `<lobby>/plugins/` kopiert. Neu bauen nur bei Quelltext-Aenderung
(`src/net/mcsm/lobby/MCSMLobby.java`).

Kompiliert gegen `paper-api` **1.20.6** (`transfer(String,int)` gibt es seit MC 1.20.5),
Bytecode **Java 17** -> laeuft auf jedem Paper ab 1.20.5 (die brauchen ohnehin Java 21).

## Bauen (Windows, JDK 21)

```bash
JDK="/c/Program Files/Java/jdk-21/bin"
BASE="https://repo.papermc.io/repository/maven-public"
JARV="1.20.6-R0.1-20241030.191541-127"   # aus paper-api/1.20.6-R0.1-SNAPSHOT/maven-metadata.xml

# Compile-Classpath (nur zum Kompilieren, nicht mit ins Jar):
curl -sSL "$BASE/io/papermc/paper/paper-api/1.20.6-R0.1-SNAPSHOT/paper-api-$JARV.jar" -o paper-api.jar
curl -sSL "https://repo1.maven.org/maven2/net/kyori/adventure-api/4.17.0/adventure-api-4.17.0.jar" -o adventure-api.jar
curl -sSL "https://repo1.maven.org/maven2/net/kyori/adventure-key/4.17.0/adventure-key-4.17.0.jar" -o adventure-key.jar
curl -sSL "https://repo1.maven.org/maven2/net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar" -o examination-api.jar
curl -sSL "$BASE/net/md-5/bungeecord-chat/1.20-R0.2-deprecated+build.18/bungeecord-chat-1.20-R0.2-deprecated+build.18.jar" -o bungeecord-chat.jar

CP="paper-api.jar;adventure-api.jar;adventure-key.jar;examination-api.jar;bungeecord-chat.jar"
"$JDK/javac.exe" --release 17 -cp "$CP" -d out src/net/mcsm/lobby/MCSMLobby.java
cp src/plugin.yml src/config.yml out/
(cd out && "$JDK/jar.exe" cf ../MCSMLobby.jar .)
```

## Was das Plugin tut

Schickt Spieler per Vanilla-Transfer-Paket an einen Gateway-Server. Trigger:
Kompass (Rechtsklick -> GUI), `/server <name>` / `/hub`, Schilder `[server]`,
und begehbare Portal-Regionen (Quader in der `config.yml`).

Die `config.yml` unter `<lobby>/plugins/MCSMLobby/config.yml` erzeugt der Manager aus
den Gateway-Routen (Abschnitt `servers`). `regions` (Portale) ergaenzt der Nutzer selbst.
