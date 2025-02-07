package com.cse546.project32;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class Handler implements RequestHandler<S3Event, String> {

//    private static final String AWS_ACCESS_KEY_ID = "accesskey";
//    private static final String AWS_SECRET_ACCESS_KEY = "secretaccesskey";
    private static final String ASU_ID = "asuid";

    private S3Client s3Client;
    private LambdaClient lambdaClient;

    @Override
    public String handleRequest(S3Event event, Context context) {
        context.getLogger().log("Lambda function invoked.");

        try {
            AwsBasicCredentials awsCreds = AwsBasicCredentials.create(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY);
            s3Client = S3Client.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                    .build();

            lambdaClient = LambdaClient.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                    .build();

            S3EventNotificationRecord record = event.getRecords().get(0);
            String srcBucket = record.getS3().getBucket().getName();
            String srcKey = URLDecoder.decode(record.getS3().getObject().getKey().replace('+', ' '), "UTF-8");

            context.getLogger().log("Source Bucket: " + srcBucket);
            context.getLogger().log("Source Key: " + srcKey);

            String tmpDir = System.getProperty("java.io.tmpdir");
            File localVideoFile = new File(tmpDir, new File(srcKey).getName());

            if(!localVideoFile.exists()) {
	            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
	                    .bucket(srcBucket)
	                    .key(srcKey)
	                    .build();
	
	            s3Client.getObject(getObjectRequest, ResponseTransformer.toFile(localVideoFile));
	
	            context.getLogger().log("Downloaded video to: " + localVideoFile.getAbsolutePath());
            }

            String videoNameWithoutExtension = srcKey.substring(0, srcKey.lastIndexOf('.'));
            String outputImageName = videoNameWithoutExtension + ".jpg";
            String outputImagePath = tmpDir + "/" + outputImageName;

            String ffmpegCommand = String.format(
                "ffmpeg -i \"%s\" -vframes 1 \"%s\" -y",
                localVideoFile.getAbsolutePath(), outputImagePath
            );

            context.getLogger().log("Executing ffmpeg command: " + ffmpegCommand);

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", ffmpegCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            InputStream processInputStream = process.getInputStream();
            byte[] processOutput = processInputStream.readAllBytes();
            int exitCode = process.waitFor();

            String ffmpegOutput = new String(processOutput);
            context.getLogger().log("ffmpeg output:\n" + ffmpegOutput);
            context.getLogger().log("ffmpeg exit code: " + exitCode);

            if (exitCode != 0) {
                context.getLogger().log("ffmpeg command failed with exit code " + exitCode);
                return "Error";
            }

            String destBucket = ASU_ID + "-stage-1";
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(destBucket)
                    .key(outputImageName)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromFile(new File(outputImagePath)));
            context.getLogger().log("Uploaded frame to s3://" + destBucket + "/" + outputImageName);

            String functionName = "face-recognition";
            String payload = String.format("{\"bucket_name\":\"%s\",\"image_file_name\":\"%s\"}", destBucket, outputImageName);

            context.getLogger().log("Invoking face-recognition function with payload: " + payload);

            InvokeRequest invokeRequest = InvokeRequest.builder()
                    .functionName(functionName)
                    .invocationType(InvocationType.EVENT)
                    .payload(SdkBytes.fromString(payload, StandardCharsets.UTF_8))
                    .build();

            lambdaClient.invoke(invokeRequest);

            context.getLogger().log("Video splitting and uploading completed successfully.");
            return "Success";

        } catch (Exception e) {
            context.getLogger().log("Exception: " + e.getMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                context.getLogger().log(element.toString());
            }
            return "Error";
        }
    }
}
