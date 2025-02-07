package com.cse546.project31;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.InputStream;
import java.net.URLDecoder;

public class Handler implements RequestHandler<S3Event, String> {

//    private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
//    private static final String AWS_ACCESS_KEY_ID = "accesskey";
//	private static final String AWS_SECRET_ACCESS_KEY = "secretaccesskey";
	private S3Client s3Client;

    @Override
    public String handleRequest(S3Event event, Context context) {
        context.getLogger().log("Lambda function invoked.");

        try {
        	// Get S3 
        	
        	AwsBasicCredentials awsCreds = AwsBasicCredentials.create(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY);
        	s3Client = S3Client.builder().region(Region.US_EAST_1).credentialsProvider(StaticCredentialsProvider.create(awsCreds)).build();
        	
            // Get the S3 event details
            S3EventNotificationRecord record = event.getRecords().get(0);
            String srcBucket = record.getS3().getBucket().getName();
            String srcKey = URLDecoder.decode(record.getS3().getObject().getKey().replace('+', ' '), "UTF-8");

            context.getLogger().log("Source Bucket: " + srcBucket);
            context.getLogger().log("Source Key: " + srcKey);

            // Download the video file from S3
            String tmpDir = System.getProperty("java.io.tmpdir");
            File localVideoFile = new File(tmpDir, new File(srcKey).getName());
            
            // Define the GetObjectRequest
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(srcBucket)
                    .key(srcKey)
                    .build();

            // Download the object and copy to local file
            s3Client.getObject(getObjectRequest, localVideoFile.toPath());

            context.getLogger().log("Downloaded video to: " + localVideoFile.getAbsolutePath());

            // Prepare output directory
            String videoNameWithoutExtension = srcKey.substring(0, srcKey.lastIndexOf('.'));
            File outputDir = new File(tmpDir, videoNameWithoutExtension);
            outputDir.mkdirs();

            context.getLogger().log("Created output directory: " + outputDir.getAbsolutePath());

            // Define ffmpeg command parameters
            String inputVideo = localVideoFile.getAbsolutePath();
            String outputDirPath = outputDir.getAbsolutePath();
            String outputPrefix = "output";
            String interval = "0.1";

            // Build ffmpeg command
            String ffmpegCommand = String.format(
                "ffmpeg -ss 0 -i \"%s\" -vf \"fps=1/%s\" -start_number 0 -vframes 10 \"%s/%s-%%02d.jpg\" -y",
                inputVideo, interval, outputDirPath, outputPrefix
            );

            context.getLogger().log("Executing ffmpeg command: " + ffmpegCommand);

            // Execute ffmpeg command
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", ffmpegCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Capture ffmpeg output
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

            // Upload the frames to the stage-1 bucket
            String destBucket = "1229365139-stage-1";
            File[] frames = outputDir.listFiles();
            if (frames != null && frames.length > 0) {
                for (File frame : frames) {
                    String destKey = videoNameWithoutExtension + "/" + frame.getName();
//                    s3Client.putObject(destBucket, destKey, frame);
                    PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                            .bucket(destBucket)
                            .key(destKey)
                            .build();

                    // Upload the file
                    s3Client.putObject(putObjectRequest, RequestBody.fromFile(frame));
                    context.getLogger().log("Uploaded frame to s3://" + destBucket + "/" + destKey);
                }
            } else {
                context.getLogger().log("No frames found in output directory.");
                return "Error";
            }

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
