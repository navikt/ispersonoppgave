# ispersonoppgave
Application for handling of Person-Oppgaver by SYFO-veiledere in Syfomodiaperson(https://github.com/navikt/syfomodiaperson) in Modia.

PersonOppgave types:
* OppfolgingsplanLPS (Oppfolgingsplan from LPS that has been shared with NAV and has Bistandsbehov)

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

## Application Description
The application has three main flows:
receive OppfolgingsplanLPS, retreive PersonOppgave list and process PersonOppgave.

#### Receive OppfolgingsplanLPS
##### Flow
1. Read OppfolgingsplanLPS from Kafka-topic.
2. Create PersonOppgave if OppfolgingsplanLPS has Bistandsbehov.
3. Send Oversikthendelse for received PersonOppgave to Kafka-topic.

##### Fault tolerance
In case of a failure when trying to generate a Oversikthendelse based on a asynchronous event,
the application will create a retry-object and publish it to a retry-topic.
The application will attempt to retry sending of Oversikthendelse until the limit for maximum retries is reached.
If the maximum number of retries is reached, the application will stop retry of sending of Oversikthendels and manual handling of sending is needed.

#### Retreive PersonOppgave list
##### Flow:
1. Receive request from NAV-Veileder to the PersonOppgave list of a person.
2. Check if NAV-Veileder has access to the person.
3. Veieder receives the list if access app grants access.
4. Send Oversikthendelse for processed PersonOppgave to Kafka-topic.

#### Process PersonOppgave
##### Flow:
1. Receive request from NAV-Veileder to process PersonOppgave.
2. Check if NAV-Veileder has access to the person of the PersonOppgave.
2. Update PersonOppgave with who processed and when it was processed.
3. Send Oversikthendelse for processed PersonOppgave to Kafka-topic.
