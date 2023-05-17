import * as cdk from 'aws-cdk-lib';
import {Construct} from 'constructs';
import {Vpc} from "aws-cdk-lib/aws-ec2";
import {Repository} from "aws-cdk-lib/aws-ecr";
import {
    AuroraPostgresEngineVersion,
    Credentials,
    DatabaseCluster,
    DatabaseClusterEngine,
    DatabaseSecret
} from "aws-cdk-lib/aws-rds";
import {Effect, PolicyStatement} from "aws-cdk-lib/aws-iam";
import {ApplicationLoadBalancedFargateService} from "aws-cdk-lib/aws-ecs-patterns";
import {ContainerImage} from "aws-cdk-lib/aws-ecs";
import {AttributeType, BillingMode, Table, TableEncryption} from "aws-cdk-lib/aws-dynamodb";
import {RemovalPolicy} from "aws-cdk-lib";

export class AxonRdsStack extends cdk.Stack {

    constructor(scope: Construct, id: string, props?: cdk.StackProps) {
        super(scope, id, props);

        let vpc = new Vpc(this, `axon-rds-test-vpc`);
        let ecrRepo = Repository.fromRepositoryName(this, `axon-rds-ecr-lookup`, `magnetar/demo-app`)
        let projectionTable = this.projectionTable()

        let databaseSecret = new DatabaseSecret(this, 'axon-rds-db-secret', {
            secretName: 'axon-rds-db-secret',
            username: 'axonservice'
        })
        let cluster = new DatabaseCluster(this, 'axon-rds-cluster', {
            engine: DatabaseClusterEngine.auroraPostgres({version: AuroraPostgresEngineVersion.VER_14_3}),
            clusterIdentifier: 'axon-rds-cluster',
            credentials: Credentials.fromSecret(databaseSecret),
            instanceProps: {vpc: vpc},
            defaultDatabaseName: 'axon'
        })
        const loadBalancedFargateService = new ApplicationLoadBalancedFargateService(this, 'axon-rds-test-service', {
                vpc: vpc,
                memoryLimitMiB: 4096,
                cpu: 2048,
                desiredCount: 1,
                taskImageOptions: {
                    image: ContainerImage.fromEcrRepository(ecrRepo, "axon-rds-v4"),
                    environment: {
                        "SERVICE_DATABASE_PROXY_URL": cluster.clusterEndpoint.hostname,
                        "SERVICE_DATABASE_SECRET": databaseSecret.secretArn
                    },
                    containerPort: 8080
                }
            }
        );
        loadBalancedFargateService.taskDefinition.addToTaskRolePolicy(new PolicyStatement({
            effect: Effect.ALLOW,
            resources: [databaseSecret.secretArn],
            actions: ["secretsmanager:DescribeSecret",
                "secretsmanager:GetResourcePolicy",
                "secretsmanager:GetSecretValue",
                "secretsmanager:ListSecretVersionIds"]
        }))
        loadBalancedFargateService.taskDefinition.addToTaskRolePolicy(new PolicyStatement({
            effect: Effect.ALLOW,
            resources: [projectionTable.tableArn],
            actions: ["dynamodb:*"]
        }))
        loadBalancedFargateService.service.connections.allowToDefaultPort(cluster)
        loadBalancedFargateService.targetGroup.configureHealthCheck({
            path: "/health",
            port: "8080"
        })

    }

    projectionTable() {
        return new Table(this, `axon-dynamodb-projection-storage`, {
            partitionKey: {name: "aid", type: AttributeType.STRING},
            tableName: `balance_projection_rds`,
            encryption: TableEncryption.AWS_MANAGED,
            billingMode: BillingMode.PAY_PER_REQUEST,
            removalPolicy: RemovalPolicy.DESTROY
        })
    }
}
