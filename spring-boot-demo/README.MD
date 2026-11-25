# Spring Boot Secrets Manager Demo

This app reads `demo/springboot` from AWS Secrets Manager running in the local AWS Emulator. Use a fake token because the demo prints the value to the console.

## 1. Start the emulator

You need JDK 17, AWS CLI v2, and Docker Desktop. Follow step 2 in the [AWS Emulator setup guide](../README.MD) to start the emulator. Leave it running.

## 2. Set up PowerShell

Open a second PowerShell window in this project folder, where `pom.xml` is. Run this block and use the **same window** for the rest of the steps.

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

Continue when you see `000000000000`. That's the emulator's account ID.

## 3. Create the demo secret

Do this once after starting the emulator. If you already created the secret during this run, go to step 4.

```powershell
aws secretsmanager create-secret --endpoint-url $env:AWS_ENDPOINT_URL `
  --name demo/springboot --secret-string local-demo-token --no-cli-pager
```

## 4. Run the app

```powershell
.\mvnw.cmd clean package

java "-Djavax.net.ssl.trustStore=$emulatorDir\truststore.p12" `
  "-Djavax.net.ssl.trustStoreType=PKCS12" `
  -jar .\target\spring-boot-demo-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=local --spring.main.web-application-type=none
```

Look for `Read secret demo/springboot successfully.` The app reads the secret once and exits.

If Spring says it could not resolve `AWS_ENDPOINT_URL`, rerun step 2 in the window where you launch Java. When the emulator stops, it clears the local secret. Create it again the next time you start the emulator.
