import boto3
import time

def list_ec2_instances():
    response = ec2_client.describe_instances()
    instances = []
    for reservation in response['Reservations']:
        for instance in reservation['Instances']:
            instance_info = {
                'InstanceId': instance.get('InstanceId', 'N/A'),
                'State': instance.get('State', {}).get('Name', 'N/A')
            }
            instances.append(instance_info)
    return instances

def list_s3_buckets():
    response = s3_client.list_buckets()
    buckets = [bucket['Name'] for bucket in response['Buckets']]
    return buckets

def list_sqs_queues():
    response = sqs_client.list_queues()
    queues = response.get('QueueUrls', [])
    return queues


region = '' #region
amiId = '' #amiId
instanceType = 't2.micro'
keyPair = '' #keyPair
#accessKey = '' #accessKey
#secretAccessKey = '' #secretAccessKey
s3Bucket = f's3-{int(time.time())}'
sqsQueue = 'sqs.fifo'
fileName = 'CSE546test.txt'
messageName = 'test-message'
messageBody = 'This is a test message'

ec2_client = boto3.client('ec2', region_name=region, aws_access_key_id = accessKey, aws_secret_access_key = secretAccessKey)
s3_client = boto3.client('s3', region_name=region, aws_access_key_id = accessKey, aws_secret_access_key = secretAccessKey)
sqs_client = boto3.client('sqs', region_name=region, aws_access_key_id = accessKey, aws_secret_access_key = secretAccessKey)

print("\nCreating EC2 instance...")
response = ec2_client.run_instances(
    ImageId=amiId,
    InstanceType=instanceType,
    KeyName=keyPair,
    MinCount=1,
    MaxCount=1
)
instanceId = response['Instances'][0]['InstanceId']
print(f"EC2 instance '{instanceId}' request sent.")

print(f"Creating S3 bucket '{s3Bucket}'...")
s3_client.create_bucket(
    Bucket=s3Bucket
)
print(f"S3 bucket '{s3Bucket}' request sent.")

print(f"Creating SQS FIFO queue '{sqsQueue}'...")
response = sqs_client.create_queue(
    QueueName=sqsQueue,
    Attributes={
        'FifoQueue': 'true',
        'ContentBasedDeduplication': 'true'
    }
)
queue_url = response['QueueUrl']
print(f"SQS FIFO queue '{sqsQueue}' request sent.")

print("All requests sent, waiting for 1 min.")
time.sleep(60)

print("\n" + ("*"*75))

print("\nListing all EC2 instances:")
ec2_instances = list_ec2_instances()
print(ec2_instances)
print("Listing all S3 buckets:")
s3_buckets = list_s3_buckets()
print(s3_buckets)
print("Listing all SQS queues:")
sqs_queues = list_sqs_queues()
print(sqs_queues)

print("\n" + ("*"*75))

print(f"\nUploading empty file '{fileName}' to S3 bucket '{s3Bucket}'...")
s3_client.put_object(Bucket=s3Bucket, Key=fileName, Body='')
print(f"File '{fileName}' uploaded to S3 bucket.")

print("\n" + ("*"*75))

print("\nSending message to SQS queue...")
response = sqs_client.send_message(
    QueueUrl=queue_url,
    MessageGroupId='default',
    MessageAttributes={'Title': {'StringValue': 'test message', 'DataType': 'String'}},
    MessageBody=messageBody
)
print("Message sent.")

response = sqs_client.get_queue_attributes(
    QueueUrl=queue_url,
    AttributeNames=['ApproximateNumberOfMessages']
)
message_count = response['Attributes'].get('ApproximateNumberOfMessages', '0')
print(f"Number of messages in SQS queue: {message_count}")

# print("Waiting for 60 seconds. (For video demo)")
# time.sleep(60) # Added extra wait time to show video demo

print("\n" + ("*"*75))

print("\nReceiving message from SQS queue...")
response = sqs_client.receive_message(
    QueueUrl=queue_url,
    MaxNumberOfMessages=1,
    WaitTimeSeconds=10,
    MessageAttributeNames=['All']
)
messages = response.get('Messages', [])
message = messages[0]
receipt_handle = message['ReceiptHandle']
print(f"Message Name: {message.get('MessageAttributes', {}).get('Title', {}).get('StringValue', 'N/A')}")
print(f"Message Body: {message.get('Body', 'N/A')}")
sqs_client.delete_message(
    QueueUrl=queue_url,
    ReceiptHandle=receipt_handle
)
print("Message deleted successfully.")

print("Waiting for 10 seconds.")
time.sleep(10)

response = sqs_client.get_queue_attributes(
    QueueUrl=queue_url,
    AttributeNames=['ApproximateNumberOfMessages']
)
message_count_after = response['Attributes'].get('ApproximateNumberOfMessages', '0')
print(f"Number of messages in SQS queue after retrieval: {message_count_after}")

print("Waiting for 10 seconds.")
time.sleep(10)

print("\n" + ("*"*75))

print(f"\nTerminating EC2 instance '{instanceId}'...")
ec2_client.terminate_instances(InstanceIds=[instanceId])
print(f"EC2 instance '{instanceId}' terminate request sent.")

print(f"Deleting S3 bucket '{s3Bucket}'...")
objects = s3_client.list_objects_v2(Bucket=s3Bucket)
if 'Contents' in objects:
    for obj in objects['Contents']:
        s3_client.delete_object(Bucket=s3Bucket, Key=obj['Key'])
s3_client.delete_bucket(Bucket=s3Bucket)
print(f"S3 bucket '{s3Bucket}' deleted request sent.")

print(f"Deleting SQS queue '{queue_url}'...")
sqs_client.delete_queue(QueueUrl=queue_url)
print(f"SQS queue '{queue_url}' delete request sent.")

print("Resources deletion initiated, waiting for 60 seconds.")
time.sleep(60)

print("\n" + ("*"*75))

print("\nListing all EC2 instances after deletion:")
ec2_instances_after = list_ec2_instances()
print(ec2_instances_after)
print("Listing all S3 buckets after deletion:")
s3_buckets_after = list_s3_buckets()
print(s3_buckets_after)
print("Listing all SQS queues after deletion:")
sqs_queues_after = list_sqs_queues()
print(sqs_queues_after)