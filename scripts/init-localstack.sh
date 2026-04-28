#!/bin/bash
# Runs once when LocalStack is ready
set -e

awslocal s3 mb s3://app-uploads
awslocal s3 mb s3://app-archive

awslocal sqs create-queue --queue-name order-events
awslocal sqs create-queue --queue-name order-events-dlq
awslocal sqs create-queue --queue-name notifications

echo "LocalStack initialized: S3 buckets + SQS queues"
