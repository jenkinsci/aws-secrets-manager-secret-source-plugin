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

Create the relevant secret in Secrets Manager. There are many ways to do this, including using Terraform or the AWS CLI. Then put the secret into your JCasC definition.
`aws:secretsmanager:` is ued as a prefix so that the CasC plugin knows how to decode it. 

### If the secret is plain text:
#### Create Secret:
```bash
aws secretsmanager create-secret --name 'my-secret' --secret-string 'abc123' --description 'Jenkins user password'
```

#### JCasC definition:
```yaml
jenkins:
  securityRealm:
    local:
      allowsSignup: false
      users:
      - id: "some_user"
        password: "${aws-secretsmanager:my-secret}"
```

### If the secret value is key-value pairs (JSON)
#### Create Secret:
```bash
aws secretsmanager create-secret --name 'my-json-secret' --secret-string '{"username": "some_user", "password": "abc123" }' --description 'Jenkins user password'
```

#### JCasC definition:

The `:` character is used as a delimiter between secret name and json key in the object. It is used as it is a forbidden character in a AWS Secretsmanager secret name and does not create trouble for Jenkins secrets management internally.

```yaml
jenkins:
  securityRealm:
    local:
      allowsSignup: false
      users:
      - id: "${aws-secretsmanager:my-json-secret:username}"
        password: "${aws-secretsmanager:my-json-secret:password}"
```

Then start Jenkins.

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
