# ispersonoppgave
Application for handling of Person-Oppgaver by SYFO-veiledere in Syfomodiaperson(https://github.com/navikt/syfomodiaperson) in Modia.

## Technologies used
* Kotlin
* Ktor
* Gradle
* Spek
* Vault
* Postgres

## Get packages from Github Package Registry
Some packages are downloaded from Github Package Registry and requires authentication.
The packages can be downloaded via build.gradle.kts:
```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfoopservice-schema")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}
```

`githubUser` and `githubPassword` are set in `~/.gradle/gradle.properties`:

```
githubUser=x-access-token
githubPassword=<token>
```

Where `<token>` is a personal access token with scope `read:packages`(and SSO enabled).

Alternatively, the variables can be configured as environment variables or used in the command line:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

#### Build
Run `./gradlew clean shadowJar`

### Lint
Run `./gradlew --continue ktlintCheck`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t ispersonoppgave .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 ispersonoppgave`
