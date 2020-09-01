# ispersonoppgave
App for håndtering av oppgaver som syfo-veiledere må behandle i syfomodiaperson i Modia

## Hente pakker fra Github Package Registry
Noen pakker hentes fra Github Package Registry som krever autentisering.
Pakkene kan lastes ned via build.gradle slik:
```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/navikt/tjenestespesifikasjoner")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}
```

`githubUser` og `githubPassword` settes i `~/.gradle/gradle.properties`:

```
githubUser=x-access-token
githubPassword=<token>
```

Hvor `<token>` er et personal access token med scope `read:packages`(og SSO enabled).

Evt. kan variablene kan også konfigureres som miljøvariabler eller brukes i kommandolinjen:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

