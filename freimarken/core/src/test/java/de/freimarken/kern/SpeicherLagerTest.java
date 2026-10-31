package de.freimarken.kern;

import org.junit.jupiter.api.DisplayName;

/** Der Vertrag aus {@link BesitzLagerVertrag}, geprueft am Lager im Arbeitsspeicher. */
@DisplayName("Lager im Arbeitsspeicher")
class SpeicherLagerTest extends BesitzLagerVertrag {

    @Override
    protected BesitzLager lager() {
        return new SpeicherLager();
    }
}
