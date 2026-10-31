package de.freimarken.kern;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Die Regeln rund um Parzellenbesitz: nehmen, schuetzen, Mitbauer.
 *
 * <p>Hier steht das Spiel, nicht die Technik. Kein Bukkit, keine Datenbank - nur
 * {@link Rasterplan} fuer die Geometrie und {@link BesitzLager} fuer die Ablage. Dadurch
 * laesst sich jede Regel ohne Server pruefen.
 *
 * <p>Die Grundregel des Schutzes ist absichtlich streng: Gebaut wird nur auf einer
 * Parzelle, die einem gehoert oder auf der man Mitbauer ist. Wege und Mauern gehoeren
 * niemandem, freie Parzellen auch nicht. Wer anfangen will, nimmt sich erst eine - und
 * die erste ist gratis.
 */
public final class Besitzbuch {

    /** Ausgang eines Versuchs. */
    public enum Bescheid {
        ERTEILT,
        /** Hier ist gar keine Parzelle - Weg, Mauer oder andere Welt. */
        KEINE_PARZELLE,
        /** Die Parzelle gehoert schon jemandem. */
        SCHON_VERGEBEN,
        /** Die Rangobergrenze ist erreicht. */
        GRENZE_ERREICHT,
        /** Das Guthaben reicht nicht. */
        ZU_WENIG_MARK,
        /** Der Handelnde ist nicht Eigentuemer. */
        NICHT_EIGENTUEMER,
        /** Der Spieler steht schon auf der Liste. */
        SCHON_EINGETRAGEN,
        /** Der Spieler stand gar nicht auf der Liste. */
        NICHT_EINGETRAGEN
    }

    /**
     * Antwort auf einen Versuch.
     *
     * @param bescheid was herausgekommen ist
     * @param kosten   was es gekostet hat bzw. gekostet haette, in Mark
     * @param parzelle die betroffene Parzelle, falls es eine gibt
     */
    public record Entscheid(Bescheid bescheid, long kosten, Optional<Parzelle> parzelle) {

        public boolean erteilt() {
            return bescheid == Bescheid.ERTEILT;
        }

        static Entscheid nein(Bescheid grund) {
            return new Entscheid(grund, 0L, Optional.empty());
        }

        static Entscheid nein(Bescheid grund, long kosten) {
            return new Entscheid(grund, kosten, Optional.empty());
        }
    }

    private final Rasterplan plan;
    private final BesitzLager lager;

    public Besitzbuch(Rasterplan plan, BesitzLager lager) {
        this.plan = plan;
        this.lager = lager;
    }

    public Rasterplan plan() {
        return plan;
    }

    /** Die Parzelle an dieser WELTkoordinate, falls dort ueberhaupt Bauland ist. */
    public Optional<Parzelle> parzelleAn(String welt, int weltX, int weltZ) {
        if (plan.feldBei(weltX, weltZ) != Rasterplan.Feld.PARZELLE) {
            return Optional.empty();
        }
        return lager.finde(welt, plan.parzelleX(weltX), plan.parzelleZ(weltZ));
    }

    /**
     * Darf dieser Spieler an dieser Stelle bauen oder abbauen?
     *
     * <p>Bewusst ohne Sonderfall fuer das Team: Wer mehr darf, bekommt das ueber ein Recht
     * und nicht dadurch, dass diese Regel aufgeweicht wird. Die Regel hier beantwortet
     * genau eine Frage, und sie beantwortet sie fuer alle gleich.
     */
    public boolean darfBauen(String welt, UUID spieler, int weltX, int weltZ) {
        return parzelleAn(welt, weltX, weltZ)
                .map(parzelle -> parzelle.darfBauen(spieler))
                .orElse(false);
    }

    /**
     * Eine Parzelle nehmen.
     *
     * @param welt            Weltschluessel
     * @param spieler         wer sie haben will
     * @param weltX           Weltkoordinate, an der er steht
     * @param weltZ           Weltkoordinate, an der er steht
     * @param hoechstzahl     wie viele Parzellen sein Rang erlaubt
     * @param guthaben        was er an Mark hat
     */
    public Entscheid nehmen(String welt, UUID spieler, int weltX, int weltZ,
                            int hoechstzahl, long guthaben) {
        if (plan.feldBei(weltX, weltZ) != Rasterplan.Feld.PARZELLE) {
            return Entscheid.nein(Bescheid.KEINE_PARZELLE);
        }
        int px = plan.parzelleX(weltX);
        int pz = plan.parzelleZ(weltZ);

        Optional<Parzelle> vorhanden = lager.finde(welt, px, pz);
        if (vorhanden.isPresent()) {
            return new Entscheid(Bescheid.SCHON_VERGEBEN, 0L, vorhanden);
        }

        int bereits = lager.anzahlVon(welt, spieler);
        if (bereits >= hoechstzahl) {
            return Entscheid.nein(Bescheid.GRENZE_ERREICHT);
        }

        long preis = Preisstaffel.preisFuerNaechste(bereits);
        if (guthaben < preis) {
            return Entscheid.nein(Bescheid.ZU_WENIG_MARK, preis);
        }

        Parzelle neu = lager.anlegen(welt, px, pz, spieler, preis);
        return new Entscheid(Bescheid.ERTEILT, preis, Optional.of(neu));
    }

    /** Einen Mitbauer eintragen. Nur der Eigentuemer darf das. */
    public Entscheid mitbauerHinzufuegen(String welt, UUID eigentuemer, int weltX, int weltZ,
                                         UUID neuerMitbauer) {
        Optional<Parzelle> gefunden = parzelleAn(welt, weltX, weltZ);
        if (gefunden.isEmpty()) {
            return Entscheid.nein(Bescheid.KEINE_PARZELLE);
        }
        Parzelle parzelle = gefunden.get();
        if (!parzelle.gehoert(eigentuemer)) {
            return new Entscheid(Bescheid.NICHT_EIGENTUEMER, 0L, gefunden);
        }
        if (parzelle.mitbauer().contains(neuerMitbauer) || parzelle.gehoert(neuerMitbauer)) {
            return new Entscheid(Bescheid.SCHON_EINGETRAGEN, 0L, gefunden);
        }
        Set<UUID> neu = new LinkedHashSet<>(parzelle.mitbauer());
        neu.add(neuerMitbauer);
        return new Entscheid(Bescheid.ERTEILT, 0L,
                Optional.of(lager.mitbauerSetzen(parzelle.id(), neu)));
    }

    /** Einen Mitbauer austragen. Nur der Eigentuemer darf das. */
    public Entscheid mitbauerEntfernen(String welt, UUID eigentuemer, int weltX, int weltZ,
                                       UUID bisherigerMitbauer) {
        Optional<Parzelle> gefunden = parzelleAn(welt, weltX, weltZ);
        if (gefunden.isEmpty()) {
            return Entscheid.nein(Bescheid.KEINE_PARZELLE);
        }
        Parzelle parzelle = gefunden.get();
        if (!parzelle.gehoert(eigentuemer)) {
            return new Entscheid(Bescheid.NICHT_EIGENTUEMER, 0L, gefunden);
        }
        if (!parzelle.mitbauer().contains(bisherigerMitbauer)) {
            return new Entscheid(Bescheid.NICHT_EINGETRAGEN, 0L, gefunden);
        }
        Set<UUID> neu = new LinkedHashSet<>(parzelle.mitbauer());
        neu.remove(bisherigerMitbauer);
        return new Entscheid(Bescheid.ERTEILT, 0L,
                Optional.of(lager.mitbauerSetzen(parzelle.id(), neu)));
    }

    /**
     * Eine Parzelle aufgeben.
     *
     * <p>Ohne Rueckzahlung: Sonst waere Kaufen und Aufgeben ein Weg, die Preisstaffel
     * zurueckzusetzen - man kauft die teure sechste, gibt die billige zweite auf und steht
     * guenstiger da als vorher.
     */
    public Entscheid aufgeben(String welt, UUID eigentuemer, int weltX, int weltZ) {
        Optional<Parzelle> gefunden = parzelleAn(welt, weltX, weltZ);
        if (gefunden.isEmpty()) {
            return Entscheid.nein(Bescheid.KEINE_PARZELLE);
        }
        Parzelle parzelle = gefunden.get();
        if (!parzelle.gehoert(eigentuemer)) {
            return new Entscheid(Bescheid.NICHT_EIGENTUEMER, 0L, gefunden);
        }
        lager.entfernen(parzelle.id());
        return new Entscheid(Bescheid.ERTEILT, 0L, gefunden);
    }
}
