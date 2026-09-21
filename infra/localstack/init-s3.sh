#!/bin/sh
# Runs inside the LocalStack container once it is ready.
awslocal s3 mb s3://kissan-voice-media
awslocal s3api put-bucket-cors --bucket kissan-voice-media --cors-configuration '{
  "CORSRules": [{
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["GET", "PUT", "POST"],
    "AllowedOrigins": ["*"],
    "ExposeHeaders": ["ETag"]
  }]
}'
echo "kissan-voice-media bucket ready"
