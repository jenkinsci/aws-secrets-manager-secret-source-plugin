# AWS Secrets Manager SecretSource

[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/aws-secrets-manager-secret-source-plugin/main)](https://ci.jenkins.io/blue/organizations/jenkins/Plugins%2Faws-secrets-manager-secret-source-plugin/activity/)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/aws-secrets-manager-secret-source.svg)](https://plugins.jenkins.io/aws-secrets-manager-secret-source)

AWS Secrets Manager backend for the Jenkins SecretSource API.

The plugin allows JCasC to interpolate string secrets from Secrets Manager. It is the low-level counterpart of the [AWS Secrets Manager Credentials Provider](https://github.com/jenkinsci/aws-secrets-manager-credentials-provider-plugin) plugin. It can be used standalone, or together with the Credentials Provider.

## Setup

### IAM

Give Jenkins read access to Secrets Manager with an IAM policy.

Required permissions:

- `secretsmanager:GetSecretValue`

Optional permissions:

- `kms:Decrypt` (if you use a customer-managed KMS key to encrypt the secret)

Example:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "AllowJenkinsToGetSecretValues",
            "Effect": "Allow",
            "Action": "secretsmanager:GetSecretValue",
            "Resource": "*"
        }
    ]
}
```

### Jenkins

The plugin uses the AWS Java SDK to communicate with Secrets Manager. If you are running Jenkins outside EC2, ECS, or EKS you may need to manually configure the SDK to authenticate with AWS. See the official AWS documentation for more information.

## Usage

### Text secrets

Create secret:

```bash
aws secretsmanager create-secret --name 'my-secret' --secret-string 'abc123' --description 'Jenkins user password'
```

Reference it by name:

```yaml
jenkins:
  securityRealm:
    local:
      allowsSignup: false
      users:
      - id: "some_user"
        password: "${my-secret}"
```

### JSON secrets

Create secret:

```bash
aws secretsmanager create-secret --name 'my-secret' --secret-string '{"foo": "some_user", "bar": "abc123" }' --description 'Jenkins user password'
```

Reference it using the CasC [`json` helper](https://github.com/jenkinsci/configuration-as-code-plugin/blob/master/docs/features/secrets.adoc#json):

```yaml
jenkins:
  securityRealm:
    local:
      allowsSignup: false
      users:
      - id: "${json:foo:${my-secret}}"
        password: "${json:bar:${my-secret}}"
```

## Development

### Dependencies

- Docker
- Java
- Maven

### Build

In Maven:

```shell script
mvn clean verify
```

In your IDE:

1. Generate translations: `mvn localizer:generate`. (This is a one-off task. You only need to re-run this if you change the translations, or if you clean the Maven `target` directory.)
2. Compile.
3. Start Moto: `mvn docker:build docker:start`.
4. Run tests.
5. Stop Moto: `mvn docker:stop`.

### Notes

The dependencies marked `<!-- Workaround -->` in the POM have only been added to satisfy Maven dependency version constraints. This plugin does not directly use those dependencies.
