# AWS Emulator and Spring Boot on Windows 11

Run the official AWS Emulator in Docker, create a local Secrets Manager secret with the AWS CLI, and read it from a Spring Boot console app. The emulator generates its own credentials and runs locally for free, so this exercise uses no AWS account or Free Tier allowance. [AWS Emulator overview](https://docs.aws.amazon.com/emulator/latest/userguide/what-is-service.html)

## 1. Check your tools

Run these commands in PowerShell:

```powershell
docker version
aws --version
java -version
```

- Start Docker Desktop and use Linux containers. `docker version` should show both **Client** and **Server**. [Docker Desktop for Windows](https://docs.docker.com/desktop/setup/install/windows-install/)
- Install **AWS CLI v2** using the Windows MSI if `aws` is missing. Reopen PowerShell after installation. [AWS CLI installation](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
- Use **JDK 17** for the new sample below. If you're using the existing `spring-boot-demo` project, its `pom.xml` targets Java 17. The build uses the Maven wrapper included by Spring Initializr.

## 2. Build and start AWS Emulator

Create a new folder and save the following file as `Dockerfile`, without a `.txt` extension. This uses the Linux package for Intel and AMD laptops. For Windows on ARM, change `x86_64` in the download URL to `aarch64`. [AWS Emulator Docker guide](https://docs.aws.amazon.com/emulator/latest/userguide/getting-started-docker.html)

```dockerfile
FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal

ADD https://downloads.aws.com/aws-emulator/packages/aws-emulator/LTS/latest/aws-emulator-x86_64.rpm /tmp/emulator.rpm
RUN rpm -i /tmp/emulator.rpm \
    && rm /tmp/emulator.rpm \
    && mkdir -p /home/emulator \
    && chown 1000 /home/emulator

USER 1000
ENV HOME=/home/emulator EMULATOR_BIND_ADDRESS=0.0.0.0
ENTRYPOINT ["aws-emulator"]
```

From the folder containing `Dockerfile`, build the image and start the emulator:

```powershell
docker build -t aws-emulator .

New-Item -ItemType Directory -Force "$env:USERPROFILE\.aws\emulator" | Out-Null

docker run --rm -p 127.0.0.1:9297:9297 `
  -v "$env:USERPROFILE\.aws\emulator:/home/emulator/.aws/emulator" `
  aws-emulator -p 9297 --telemetry off
```

Leave this PowerShell window open. The emulator is ready when its startup log shows `emulator server information`. Docker publishes the HTTPS endpoint on `127.0.0.1:9297`, and the mount makes the generated credentials and TLS files available to Windows.

## 3. Point the AWS CLI at the emulator

Open a **second PowerShell window**. Run this whole block there, then launch the app from that same window. The `$env:` variables and `$emulatorDir` are set for this session. If you open a new terminal, rerun this block there before launching the app. [AWS Emulator CLI and SDK configuration](https://docs.aws.amazon.com/emulator/latest/userguide/point-sdk-to-emulator.html)

```powershell
$emulatorDir = "$env:USERPROFILE\.aws\emulator\9297"

Remove-Item Env:AWS_ACCESS_KEY_ID, Env:AWS_SECRET_ACCESS_KEY, Env:AWS_SESSION_TOKEN, Env:AWS_PROFILE -ErrorAction SilentlyContinue

$env:AWS_ENDPOINT_URL = 'https://127.0.0.1:9297'
$env:AWS_SHARED_CREDENTIALS_FILE = "$emulatorDir\emulator_credentials"
$env:AWS_CONFIG_FILE = "$emulatorDir\config"
$env:AWS_CA_BUNDLE = "$emulatorDir\ca.pem"
$env:AWS_REGION = 'us-east-1'
$env:AWS_DEFAULT_REGION = 'us-east-1'
$env:AWS_EC2_METADATA_DISABLED = 'true'

aws sts get-caller-identity --endpoint-url $env:AWS_ENDPOINT_URL --query Account --output text --no-cli-pager
```

Continue when the command returns `000000000000`, the emulator's account ID.

## 4. Create and verify the secret

Create `demo/springboot` with a fake token, then retrieve it. `--query ARN` shows the secret's ARN while keeping its value out of the terminal output. [AWS CLI Secrets Manager examples](https://docs.aws.amazon.com/cli/latest/userguide/cli_secrets-manager_code_examples.html)

```powershell
aws secretsmanager create-secret --endpoint-url $env:AWS_ENDPOINT_URL `
  --name demo/springboot --secret-string local-demo-token --no-cli-pager

aws secretsmanager get-secret-value --endpoint-url $env:AWS_ENDPOINT_URL `
  --secret-id demo/springboot --query ARN --output text --no-cli-pager
```

The ARN should contain `:000000000000:secret:demo/springboot-`.

## 5. Create the Spring Boot app

Generate and extract a project with the following settings. Leave the dependency picker empty. [Spring Initializr](https://start.spring.io/)

| Setting | Value |
| --- | --- |
| Project | Maven |
| Language | Java |
| Spring Boot | A stable 4.x release |
| Group | `com.example` |
| Artifact | `demo` |
| Package name | `com.example.demo` |
| Packaging | Jar |
| Java | 21 |

If you already have the `spring-boot-demo` project, use it directly. Its application class is `SpringBootDemoApplication` in `com.example.springbootdemo`. If you've already copied the sample code into it, check that it prints only the success message. Remove any `System.out.println` that prints `token`.

If `pom.xml` is missing the AWS SDK for Java Secrets Manager module, add it inside the existing `<dependencies>` block. [AWS SDK for Java release 2.58.0](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.58.0)

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>secretsmanager</artifactId>
    <version>2.58.0</version>
</dependency>
```

For a new `demo` project, replace `src/main/java/com/example/demo/DemoApplication.java` with this code. For `spring-boot-demo`, keep its existing package and class names when adapting the example.

```java
package com.example.demo;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    @Profile("local")
    ApplicationRunner readSecret(@Value("${AWS_ENDPOINT_URL}") String endpointUrl) {
        return args -> {
            URI endpoint = URI.create(endpointUrl);
            if (!"https".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost())) {
                throw new IllegalArgumentException("Use the local HTTPS emulator endpoint");
            }

            try (var credentials = ProfileCredentialsProvider.create("default");
                 var client = SecretsManagerClient.builder()
                         .endpointOverride(endpoint)
                         .credentialsProvider(credentials)
                         .build()) {
                String token = client.getSecretValue(
                        request -> request.secretId("demo/springboot")).secretString();

                if (token == null || token.isBlank()) {
                    throw new IllegalStateException("The demo secret is empty");
                }

                System.out.println("Read secret demo/springboot successfully.");
            }
        };
    }
}
```

The `local` Spring profile enables the startup task. The AWS SDK uses the region and the generated credentials files from step 3. The task calls `GetSecretValue`, checks that it received a value, and prints a success message without logging the token.

## 6. Build and run the app

In the **PowerShell window where you ran step 3**, change to the app folder containing `pom.xml` and `mvnw.cmd`. If you're starting from the `aws-emulator` folder with an existing project, run `Set-Location .\spring-boot-demo` first. Build the JAR:

```powershell
.\mvnw.cmd clean package
```

Before running the JAR, check the endpoint in this shell. It should print `https://127.0.0.1:9297`:

```powershell
$env:AWS_ENDPOINT_URL
```

If the output is blank or Spring reports `Could not resolve placeholder 'AWS_ENDPOINT_URL'`, rerun **all of step 3 in the same PowerShell window that will launch `java`**. That sets the endpoint, credentials files, region, and `$emulatorDir` for the truststore path. To launch from an IDE, set the same environment variables and JVM truststore options in its run configuration.

Run the JAR with the `local` profile. The AWS CLI trusts the emulator through `ca.pem`, while Java uses `truststore.p12`. Passing the truststore arguments to the app's JVM lets Maven use its normal trust settings when downloading dependencies. `--spring.main.web-application-type=none` makes this run a one-shot console app even if your project includes Spring Web. [AWS Emulator Java TLS setup](https://docs.aws.amazon.com/emulator/latest/userguide/point-sdk-to-emulator.html#trusting-emulator-certificate)

```powershell
# Use this JAR for the existing spring-boot-demo project.
$jar = '.\target\spring-boot-demo-0.0.1-SNAPSHOT.jar'
# For a project generated with artifact demo, use .\target\demo-0.0.1-SNAPSHOT.jar.

java "-Djavax.net.ssl.trustStore=$emulatorDir\truststore.p12" `
  "-Djavax.net.ssl.trustStoreType=PKCS12" `
  -jar $jar --spring.profiles.active=local --spring.main.web-application-type=none
```

The sample reads the secret once, prints `Read secret demo/springboot successfully.`, and exits.

## 7. Stop the emulator

Press **Ctrl+C** in the emulator window. It keeps resources in memory, so repeat step 4 after a restart. The emulator generates fresh credentials and a fresh truststore at the same paths each time it starts. [AWS Emulator Docker guide](https://docs.aws.amazon.com/emulator/latest/userguide/getting-started-docker.html)
