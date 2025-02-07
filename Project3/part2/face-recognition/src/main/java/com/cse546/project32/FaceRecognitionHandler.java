package com.cse546.project32;

//import com.amazonaws.services.lambda.runtime.ClientContext;
//import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
//import java.util.HashMap;
import java.util.Map;

public class FaceRecognitionHandler implements RequestHandler<Map<String, String>, String> {

//	private static final String AWS_ACCESS_KEY_ID = "accesskey";
//	private static final String AWS_SECRET_ACCESS_KEY = "secretaccesskey";
    private static final String ASU_ID = "asuid";
    private static final String DATA_BUCKET = ASU_ID + "-data";

    private S3Client s3Client;

    @Override
    public String handleRequest(Map<String, String> event, Context context) {
        context.getLogger().log("FaceRecognition function invoked.");

        try {
            AwsBasicCredentials awsCreds = AwsBasicCredentials.create(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY);
            s3Client = S3Client.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                    .build();

            String bucketName = event.get("bucket_name");
            String imageFileName = event.get("image_file_name");

            context.getLogger().log("Bucket Name: " + bucketName);
            context.getLogger().log("Image File Name: " + imageFileName);

            String tmpDir = System.getProperty("java.io.tmpdir");
//            String tmpDir = "/tmp/";
            File localImageFile = new File(tmpDir, imageFileName);

            if(!localImageFile.exists()) {
	            s3Client.getObject(GetObjectRequest.builder()
	                    .bucket(bucketName)
	                    .key(imageFileName)
	                    .build(), ResponseTransformer.toFile(localImageFile));
	
	            context.getLogger().log("Downloaded image to: " + localImageFile.getAbsolutePath());
            }

            String dataFileName = "data.pt";
            File localDataFile = new File("/tmp/", dataFileName);

            if(!localDataFile.exists()) {
	            s3Client.getObject(GetObjectRequest.builder()
	                    .bucket(DATA_BUCKET)
	                    .key(dataFileName)
	                    .build(), ResponseTransformer.toFile(localDataFile));
	
	            context.getLogger().log("Downloaded data.pt to: " + localDataFile.getAbsolutePath());
            }

            File scriptFile = new File("/tmp/face-recognition-code.py");
            if(!scriptFile.exists()) {
	            InputStream scriptStream = getClass().getClassLoader().getResourceAsStream("face-recognition-code.py");
	            try (OutputStream outStream = new FileOutputStream(scriptFile)) {
	                byte[] buffer = new byte[1024];
	                int bytesRead;
	                while ((bytesRead = scriptStream.read(buffer)) != -1) {
	                    outStream.write(buffer, 0, bytesRead);
	                }
	            }
	            scriptFile.setExecutable(true);
            }

            String pythonInterpreter = "/usr/bin/python3";
//            String pythonInterpreter = "/Users/varshith.dupati/anaconda3/bin/python3";

            String[] command = {
                    pythonInterpreter,
                    scriptFile.getAbsolutePath(),
                    localImageFile.getAbsolutePath()
            };

            context.getLogger().log("Executing Python script: " + String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
         // Capture both stdout and stderr
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder scriptOutput = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
            	scriptOutput.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            // Log the outputs
            context.getLogger().log("Python script output:\n" + scriptOutput.toString());

            if (exitCode != 0) {
                context.getLogger().log("Python script failed with exit code: " + exitCode);
                return "Error";
            }

            String key = imageFileName.substring(0, imageFileName.lastIndexOf('.'));
            String resultFilePath = "/tmp/" + key + ".txt";
            File resultFile = new File(resultFilePath);

            if (!resultFile.exists()) {
                context.getLogger().log("Result file not found: " + resultFilePath);
                return "Error";
            }

            String recognizedName;
            try (BufferedReader resultReader = new BufferedReader(new FileReader(resultFile))) {
                recognizedName = resultReader.readLine().trim();
            }

            context.getLogger().log("Recognized Name: " + recognizedName);

            String outputBucket = ASU_ID + "-output";
            String outputFileName = imageFileName.substring(0, imageFileName.lastIndexOf('.')) + ".txt";
            File outputFile = new File(tmpDir, outputFileName);

            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(recognizedName);
            }

            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(outputBucket)
                    .key(outputFileName)
                    .build(), RequestBody.fromFile(outputFile));

            context.getLogger().log("Uploaded result to s3://" + outputBucket + "/" + outputFileName);

            return "Success";

        } catch (Exception e) {
            context.getLogger().log("Exception: " + e.getMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                context.getLogger().log(element.toString());
            }
            return "Error";
        }
    }
    
//    // --------
//    
// // Main method for local testing
//    public static void main(String[] args) {
//        FaceRecognitionHandler handler = new FaceRecognitionHandler();
//        
//        // Create a mock context
//        Context context = new MockContext();
//
//        // Create a sample event
//        Map<String, String> event = new HashMap<>();
//        event.put("bucket_name", "1229365139-stage-1");
//        event.put("image_file_name", "test_00.jpg");
//
//        // Invoke the handler
//        String result = handler.handleRequest(event, context);
//        System.out.println("Handler Result: " + result);
//    }
//
//    // Mock Context class
//    public static class MockContext implements Context {
//        @Override
//        public String getAwsRequestId() {
//            return "test-request-id";
//        }
//
//        @Override
//        public String getLogGroupName() {
//            return "test-log-group";
//        }
//
//        @Override
//        public String getLogStreamName() {
//            return "test-log-stream";
//        }
//
//        @Override
//        public String getFunctionName() {
//            return "FaceRecognitionFunction";
//        }
//
//        @Override
//        public String getFunctionVersion() {
//            return "$LATEST";
//        }
//
//        @Override
//        public String getInvokedFunctionArn() {
//            return "arn:aws:lambda:us-east-1:123456789012:function:FaceRecognitionFunction";
//        }
//
//        @Override
//        public CognitoIdentity getIdentity() {
//            return null;
//        }
//
//        @Override
//        public ClientContext getClientContext() {
//            return null;
//        }
//
//        @Override
//        public int getRemainingTimeInMillis() {
//            return 300000; // 5 minutes
//        }
//
//        @Override
//        public int getMemoryLimitInMB() {
//            return 2048;
//        }
//
//        @Override
//        public LambdaLogger getLogger() {
//            return new LambdaLogger() {
//                @Override
//                public void log(String message) {
//                    System.out.println(message);
//                }
//
//				@Override
//				public void log(byte[] message) {
//					// TODO Auto-generated method stub
//					System.out.println(message);
//				}
//            };
//        }
//    }
}
