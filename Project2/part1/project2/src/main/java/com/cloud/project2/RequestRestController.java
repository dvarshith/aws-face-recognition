package com.cloud.project2;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import javax.annotation.PostConstruct;
import java.util.*;

@RestController
public class RequestRestController {
	
//	private Map<String, String> classificationResults = new HashMap<>();
//	
//	public RequestRestController() throws Exception {
//		ClassPathResource resource = new ClassPathResource("data.csv");
//		try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
//            String line;
//            while ((line = br.readLine()) != null) {
//                String[] data = line.split(",");
//                classificationResults.put(data[0], data[1]);
//            }
//        }
//    }

//	@PostMapping("/")
//    public String handleFileUpload(@RequestParam("inputFile") MultipartFile file) {
//        String fileName = file.getOriginalFilename();
//        fileName = fileName.split("\\.")[0];
//        String predictionResult = classificationResults.getOrDefault(fileName, "Unknown");
//        return fileName + ":" + predictionResult;
//    }
	
//	private static final String AWS_ACCESS_KEY_ID = "your_access_key_id";
//	private static final String AWS_SECRET_ACCESS_KEY = "your_secret_access_key";
	private static final String ASU_ID = "asuid";
    private static final String REQUEST_QUEUE_NAME = ASU_ID + "-req-queue";
    private static final String RESPONSE_QUEUE_NAME = ASU_ID + "-resp-queue";

    private SqsClient sqsClient;
    private Ec2Client ec2Client;

    private String requestQueueUrl;
    private String responseQueueUrl;

    @PostConstruct
    public void init() {
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY);

        sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();

        ec2Client = Ec2Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();

        requestQueueUrl = getQueueUrl(REQUEST_QUEUE_NAME);
        responseQueueUrl = getQueueUrl(RESPONSE_QUEUE_NAME);

//        startAutoScalingThread();
    }

    @PostMapping("/")
    public ResponseEntity<String> handleImageUpload(@RequestParam("inputFile") MultipartFile inputFile) {
        try {
            String fileName = inputFile.getOriginalFilename();
            String imageId = fileName;

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(requestQueueUrl)
                    .messageBody(imageId)
                    .build());

            String result = pollResponseQueue(imageId);

            return ResponseEntity.ok(imageId + ":" + result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing request");
        }
    }

    private String pollResponseQueue(String imageId) throws InterruptedException {
        while (true) {
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(responseQueueUrl)
                    .waitTimeSeconds(3)
                    .maxNumberOfMessages(10)
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

            for (Message message : messages) {
                String body = message.body();
                // Assuming the message body is in the format: imageId:result
                if (body.startsWith(imageId + ":")) {
                    // Delete message from queue
                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                            .queueUrl(responseQueueUrl)
                            .receiptHandle(message.receiptHandle())
                            .build()
                    );

                    return body.substring(body.indexOf(":") + 1);
                }
            }
            Thread.sleep(500);
        }
    }

    private String getQueueUrl(String queueName) {
        GetQueueUrlRequest request = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();
        return sqsClient.getQueueUrl(request).queueUrl();
    }

    private void startAutoScalingThread() {
        Thread autoScalingThread = new Thread(() -> {
            while (true) {
                try {
                    GetQueueAttributesRequest queueAttributesRequest = GetQueueAttributesRequest.builder()
                            .queueUrl(requestQueueUrl)
                            .attributeNamesWithStrings("ApproximateNumberOfMessages")
                            .build();

                    Map<String, String> attributes = sqsClient.getQueueAttributes(queueAttributesRequest).attributesAsStrings();

                    int numberOfMessages = Integer.parseInt(attributes.get("ApproximateNumberOfMessages"));

                    int currentAppTierInstances = getCurrentAppTierInstanceCount();

                    int desiredInstances = calculateDesiredInstanceCount(numberOfMessages);

                    if (desiredInstances > currentAppTierInstances) {
                        int instancesToLaunch = desiredInstances - currentAppTierInstances;
                        for (int i = 0; i < instancesToLaunch; i++) {
                            launchAppTierInstance();
                        }
                    } else if (numberOfMessages==0) {
                        int instancesToTerminate = currentAppTierInstances;
                        terminateAppTierInstances(instancesToTerminate);
                    }

                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        autoScalingThread.setDaemon(true);
        autoScalingThread.start();
    }

    private int calculateDesiredInstanceCount(int numberOfMessages) {
        int desiredInstances = Math.min(numberOfMessages, 20);
        return desiredInstances;
    }

    private int getCurrentAppTierInstanceCount() {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(
                        Filter.builder().name("tag:Name").values("app-tier-instance-*").build(),
                        Filter.builder().name("instance-state-name").values("running", "pending").build()
                )
                .build();

        DescribeInstancesResponse response = ec2Client.describeInstances(request);
        int count = 0;
        for (Reservation reservation : response.reservations()) {
            count += reservation.instances().size();
        }
        return count;
    }

    private void launchAppTierInstance() {
        String amiId = "ami-xxxxxxxx"; // Replace with your App Tier AMI ID
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .imageId(amiId)
                .instanceType(InstanceType.T2_MICRO)
                .maxCount(1)
                .minCount(1)
                .iamInstanceProfile(IamInstanceProfileSpecification.builder()
                        .name("AppTierInstanceProfile") // Replace with your IAM role name
                        .build())
                .securityGroupIds("sg-xxxxxxxx") // Replace with your security group ID
                .build();

        RunInstancesResponse response = ec2Client.runInstances(runRequest);

        String instanceId = response.instances().get(0).instanceId();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(Tag.builder().key("Name").value("app-tier-instance-" + instanceId).build())
                .build();

        ec2Client.createTags(tagRequest);
    }

    private void terminateAppTierInstances(int count) {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(
                        Filter.builder().name("tag:Name").values("app-tier-instance-*").build(),
                        Filter.builder().name("instance-state-name").values("running").build()
                )
                .build();

        DescribeInstancesResponse response = ec2Client.describeInstances(request);

        List<String> instanceIds = new ArrayList<>();
        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                instanceIds.add(instance.instanceId());
                if (instanceIds.size() >= count) {
                    break;
                }
            }
            if (instanceIds.size() >= count) {
                break;
            }
        }

        if (!instanceIds.isEmpty()) {
            TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
                    .instanceIds(instanceIds)
                    .build();

            ec2Client.terminateInstances(terminateRequest);
        }
    }
	
}
