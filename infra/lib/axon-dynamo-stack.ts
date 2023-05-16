import * as cdk from 'aws-cdk-lib';
import {RemovalPolicy} from 'aws-cdk-lib';
import {Construct} from 'constructs';
import {Vpc} from "aws-cdk-lib/aws-ec2";
import {ApplicationLoadBalancedFargateService} from "aws-cdk-lib/aws-ecs-patterns";
import {ContainerImage} from "aws-cdk-lib/aws-ecs";
import {Repository} from "aws-cdk-lib/aws-ecr";
import {AttributeType, BillingMode, Table, TableEncryption} from "aws-cdk-lib/aws-dynamodb";
import {Effect, PolicyStatement} from "aws-cdk-lib/aws-iam";
import {HealthCheck} from "aws-cdk-lib/aws-autoscaling";

export class AxonDynamoStack extends cdk.Stack {

    constructor(scope: Construct, id: string, props?: cdk.StackProps) {
        super(scope, id, props);

        let vpc = new Vpc(this, `axon-dynamo-test-vpc`);
        let ecrRepo = Repository.fromRepositoryName(this, `axon-dynamodb-ecr-lookup`, `magnetar/demo-app`)

        let dynamoTable = this.axonStorageTable()
        let projectionTable = this.projectionTable()

        const loadBalancedFargateService = new ApplicationLoadBalancedFargateService(this, 'axon-dynamo-test-service', {
                vpc: vpc,
                memoryLimitMiB: 1024,
                cpu: 512,
                desiredCount: 5,
                taskImageOptions: {
                    image: ContainerImage.fromEcrRepository(ecrRepo, "axon-dynamodb"),
                    environment: {}
                }
            }
        );

        loadBalancedFargateService.taskDefinition.addToTaskRolePolicy(new PolicyStatement({
            effect: Effect.ALLOW,
            resources: [dynamoTable.tableArn, `${dynamoTable.tableArn}/index/*`, projectionTable.tableArn],
            actions: ["dynamodb:*"]
        }))

        loadBalancedFargateService.targetGroup.configureHealthCheck({
            path: "/health",
            port: "8080"
        })
    }

    axonStorageTable() {
        let axonStorageTable = new Table(this, `axon-dynamodb-storage`, {
            partitionKey: {name: "hk", type: AttributeType.STRING},
            sortKey: {name: "sk", type: AttributeType.NUMBER},
            tableName: `axon-dynamodb-storage`,
            encryption: TableEncryption.AWS_MANAGED,
            billingMode: BillingMode.PAY_PER_REQUEST,
            removalPolicy: RemovalPolicy.DESTROY
        });
        axonStorageTable.addGlobalSecondaryIndex({
            indexName: "events_gsi",
            partitionKey: {name: "gsh", type: AttributeType.NUMBER},
            sortKey: {name: "gsl", type: AttributeType.NUMBER}
        })
        return axonStorageTable
    }

    projectionTable() {
        return new Table(this, `axon-dynamodb-projection-storage`, {
            partitionKey: {name: "aid", type: AttributeType.STRING},
            tableName: `balance_projection`,
            encryption: TableEncryption.AWS_MANAGED,
            billingMode: BillingMode.PAY_PER_REQUEST,
            removalPolicy: RemovalPolicy.DESTROY
        })
    }
}
