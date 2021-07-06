![Build status](https://github.com/navikt/ispersonoppgave/workflows/main/badge.svg?branch=master)

# ispersonoppgave
Ispersonoppgave is a backend microservice for handling of Person-Oppgaver by SYFO-veiledere in Syfomodiaperson(https://github.com/navikt/syfomodiaperson) in Modia.
Person-Oppgave are created based on events and are available to SYFO-veiledere for manual processing.
Each time a Person-Oppgave is created or processed, an event is produced to notify Syfooversikt of a change in the situation of a person.

The application currently handles one type of Person-Oppgave, which is a received Oppfolgingsplan from LPS that has been shared with NAV and has Bistandsbehov.\
A future goal is to move the Person-Oppgave for received answers to Motebehov from Syfomotebehov to this application.

## Technologies Used
* Docker
* Gradle
* Kotlin
* Kafka
* Ktor
* Postgres
* Vault

##### Test Libraries:
* Kluent
* Mockk
* Spek

## Local Development

### Get Protected Packages
Protected navikt-packages are downloaded from Github Package Registry and requires authentication.
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

`<token>` is a personal access token with scope `read:packages`(and SSO enabled).

Alternatively, the variables can be configured as environment variables or used in the command line:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

### Build
Run `./gradlew clean shadowJar`

### Lint (Ktlint)
##### Command line
Run checking: `./gradlew --continue ktlintCheck`

Run formatting: `./gradlew ktlintFormat`
##### Git Hooks
Apply checking: `./gradlew addKtlintCheckGitPreCommitHook`

Apply formatting: `./gradlew addKtlintFormatGitPreCommitHook`

### Test
Run `./gradlew test -i`

### Run Application

#### Create Docker Image
Creating a docker image should be as simple as `docker build -t ispersonoppgave .`

#### Run Docker Image
`docker run --rm -it -p 8080:8080 ispersonoppgave`

## Application Description
The application has three main flows:
receive OppfolgingsplanLPS, retrieve PersonOppgave list and process PersonOppgave.

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

#### Retrieve PersonOppgave list
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
