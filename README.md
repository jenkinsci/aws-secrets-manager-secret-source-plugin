# AWS Secrets Manager SecretSource

AWS Secrets Manager backend for the Jenkins SecretSource API.

The plugin allows JCasC to interpolate string secrets from Secrets Manager. It is the low-level counterpart of the [AWS Secrets Manager Credentials Provider](https://github.com/jenkinsci/aws-secrets-manager-credentials-provider-plugin) plugin.

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

Create the relevant secret in Secrets Manager. There are many ways to do this, including using Terraform or the AWS CLI:

```bash
aws secretsmanager create-secret --name 'my-password' --secret-string 'abc123' --description 'Jenkins user password'
```

Then put the secret's name in your JCasC definition:

```yaml
jenkins:
  securityRealm:
    local:
      allowsSignup: false
      users:
      - id: "foo"
        password: "${my-password}"
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
