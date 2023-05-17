#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { AxonDynamoStack } from '../lib/axon-dynamo-stack';
import { AxonRdsStack } from '../lib/axon-rds-stack';

const app = new cdk.App();

new AxonDynamoStack(app, 'AxonDynamoStack', {
  env: { account: 'TODO', region: 'eu-west-1' }
});

new AxonRdsStack(app, 'AxonRdsStack', {
  env: { account: 'TODO', region: 'eu-west-1' }
});
