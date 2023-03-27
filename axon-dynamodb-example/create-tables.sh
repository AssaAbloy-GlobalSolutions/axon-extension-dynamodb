aws --endpoint-url "http://dynamodb:8000" dynamodb create-table \
    --table-name axon_storage \
    --attribute-definitions \
        AttributeName=hk,AttributeType=S \
        AttributeName=sk,AttributeType=N \
    --key-schema \
        AttributeName=hk,KeyType=HASH \
        AttributeName=sk,KeyType=RANGE \
    --provisioned-throughput \
        ReadCapacityUnits=5,WriteCapacityUnits=5 \
    --table-class STANDARD

aws --endpoint-url "http://dynamodb:8000" dynamodb update-table \
    --table-name axon_storage \
    --attribute-definitions \
      AttributeName=gsh,AttributeType=N \
      AttributeName=gsl,AttributeType=N \
    --global-secondary-index-updates \
        "[{\"Create\":{\"IndexName\": \"events_gsi\",\"KeySchema\":[{\"AttributeName\":\"gsh\",\"KeyType\":\"HASH\"}, {\"AttributeName\":\"gsl\",\"KeyType\":\"RANGE\"}], \
         \"ProvisionedThroughput\": {\"ReadCapacityUnits\": 10, \"WriteCapacityUnits\": 5}, \
        \"Projection\":{\"ProjectionType\":\"ALL\"}}}]"
