rootProject.name = "freimarken"

dependencyResolutionManagement {
    // Projekte duerfen keine eigenen Quellen anmelden: sonst haengt irgendwann ein Modul
    // an einem Spiegel, den niemand mehr kennt.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

include("core", "daten", "paper")
