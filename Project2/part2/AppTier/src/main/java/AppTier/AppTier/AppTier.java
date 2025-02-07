package AppTier.AppTier;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;

import java.io.*;
import java.util.List;

public class AppTier {

//	private static final String AWS_ACCESS_KEY_ID = "accesskey";
//	private static final String AWS_SECRET_ACCESS_KEY = "secretaccesskey";
	private static final String ASU_ID = "asuid";
    private static final String INPUT_BUCKET = ASU_ID + "-in-bucket";
    private static final String OUTPUT_BUCKET = ASU_ID + "-out-bucket";
    private static final String REQUEST_QUEUE_NAME = ASU_ID + "-req-queue";
    private static final String RESPONSE_QUEUE_NAME = ASU_ID + "-resp-queue";

    private S3Client s3Client;
    private SqsClient sqsClient;

    private String requestQueueUrl;
    private String responseQueueUrl;

    public AppTier() {
    	AwsBasicCredentials awsCreds = AwsBasicCredentials.create(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY);
        
    	s3Client = S3Client.builder().region(Region.US_EAST_1).credentialsProvider(StaticCredentialsProvider.create(awsCreds)).build();
        sqsClient = SqsClient.builder().region(Region.US_EAST_1).credentialsProvider(StaticCredentialsProvider.create(awsCreds)).build();

        requestQueueUrl = getQueueUrl(REQUEST_QUEUE_NAME);
        responseQueueUrl = getQueueUrl(RESPONSE_QUEUE_NAME);
    }

    public void start() {
    	while (true) {
            try {
            	
            	ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                        .queueUrl(requestQueueUrl)
                        .waitTimeSeconds(1)
                        .maxNumberOfMessages(1)
                        .build();
                
                List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();
                
                if (!messages.isEmpty()) {
                    Message message = messages.get(0);
                    
                    String fileName = message.body();
                    
                    String result = processImage(fileName);
                    
                    s3Client.putObject(PutObjectRequest.builder()
                            .bucket(OUTPUT_BUCKET)
                            .key(fileName.split("\\.")[0])
                            .build(),
                            RequestBody.fromString(result)
                    );
                    
                    String responseMessage = fileName + ":" + result;
                    sqsClient.sendMessage(SendMessageRequest.builder()
                            .queueUrl(responseQueueUrl)
                            .messageBody(responseMessage)
                            .build()
                    );
                    
                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                            .queueUrl(requestQueueUrl)
                            .receiptHandle(message.receiptHandle())
                            .build()
                    );
                } else {
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String processImage(String imageId) {
        try {
            String imagePath = "/home/ubuntu/total/face_images_1000/" + imageId;
            
            uploadImageToInputBucket(imageId, imagePath);
            
            String result = runFaceRecognition(imagePath);
            
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error";
        }
    }
    
    private void uploadImageToInputBucket(String imageId, String imagePath) throws IOException {
        File imageFile = new File(imagePath);
        try (InputStream inputStream = new FileInputStream(imageFile)) {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(INPUT_BUCKET)
                    .key(imageId)
                    .build(),
                    RequestBody.fromInputStream(inputStream, imageFile.length())
            );
        }
    }

    private String runFaceRecognition(String imagePath) {
        try {
            String pythonInterpreter = "/home/ubuntu/venv/bin/python";
            ProcessBuilder pb = new ProcessBuilder(pythonInterpreter, "/home/ubuntu/total/model/face_recognition.py", imagePath);
            
            pb.redirectErrorStream(false);
            Process process = pb.start();

            BufferedReader stdOutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = stdOutReader.readLine()) != null) {
                output.append(line).append("\n");
            }

            BufferedReader stdErrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();
            while ((line = stdErrReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            System.out.println("4");
            if (exitCode == 0) {
                return output.toString();
            } else {
                System.err.println("Python script error output: " + errorOutput.toString());
                return "Error: " + errorOutput.toString();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    private String getQueueUrl(String queueName) {
        GetQueueUrlRequest request = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();
        return sqsClient.getQueueUrl(request).queueUrl();
    }

    public static void main(String[] args) {
        AppTier app = new AppTier();
        app.start();
    }
}
