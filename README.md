# AWS Face Recognition (Serverless & Auto-scaling)

[![AWS Services](https://img.shields.io/badge/AWS-EC2%2C%20S3%2C%20Lambda%2C%20SQS-orange)](https://aws.amazon.com/)
[![Java 17](https://img.shields.io/badge/Java-17-blue.svg)](https://www.java.com/)
[![Python 3.8+](https://img.shields.io/badge/Python-3.8+-blue.svg)](https://www.python.org/)

 </br>
A comprehensive AWS-based face recognition system that demonstrates:

- **Video Splitting** with AWS Lambda (using FFmpeg)
- **Face Detection & Recognition** with a pre-trained CNN (ResNet)  
- **Auto-scaling** on EC2 based on SQS message queue depth
- **Serverless Pipelines** (S3 triggers, asynchronous Lambda invocations)


> **Note**: This project contains subfolders for different parts (Project1, Project2, Project3).

---

 </br>
 
## Table of Contents

1. [Overview](#overview)  
2. [Architecture](#architecture)  
3. [Prerequisites](#prerequisites)  
4. [Setup & Installation](#setup--installation)  
5. [Usage](#usage)  
6. [Repository Structure](#repository-structure)  
7. [Security Considerations](#security-considerations)  
8. [Possible Improvements](#possible-improvements)  
9. [License](#license)  
10. [Contact](#contact)

---

 </br>
## Overview

This repository showcases an **end-to-end face recognition pipeline** using AWS:
- **Input Bucket**: Users upload `.mp4` videos to `<ASU_ID>-input`.
- **Video-Splitting Lambda**: Extracts frames from uploaded videos and stores them in `<ASU_ID>-stage-1`.
- **Face-Recognition Lambda**: Detects faces, classifies them, and writes the result to `<ASU_ID>-output`.
- **Auto-scaling App Tier**: Launches or terminates EC2 instances based on SQS queue length.
- **Web Tier**: A Spring Boot REST API that interacts with SQS and S3.

Originally developed for a Cloud Computing course, but it’s a solid reference for real-world AWS patterns.

 </br>
---

 </br>
## Architecture

A high-level flow: </br>
User (video) -> [S3: <ASU_ID>-input] -> [Lambda: video-splitting] -> [S3: <ASU_ID>-stage-1] -> [Lambda: face-recognition] -> [S3: <ASU_ID>-output]

For the IaaS version (Project 2): </br>
User -> Web Tier (Spring Boot) -> SQS -> [Auto-Scaling EC2 App Tier] -> S3

Each part uses AWS components:
- **EC2** for the App Tier.
- **S3** for input/output storage.
- **SQS** for message queue handling.
- **Lambda** for serverless tasks (video splitting & face recognition).
- **IAM** for access control (best done with roles rather than keys in code).


---

 </br>
## Prerequisites

1. **AWS Account**  
   - S3, EC2, Lambda, SQS, IAM permissions.
2. **Java 17** & **Maven**  
   - If you’re running the Spring Boot or other Java-based code.
3. **Python 3.8+**  
   - Required for the face-recognition scripts, ffmpeg steps, etc.
4. **(Optional) Docker**  
   - If building container images for Lambda.


---

 </br>
## Setup & Installation

1. **Clone this Repository**
   ```
   git clone [https://github.com/<YourUsername>/aws-face-recognition.git](https://github.com/dvarshith/aws-face-recognition.git)
   cd aws-face-recognition
   ```

2. **Configure AWS Credentials**
   - **Recommended**: Use IAM Roles for EC2 and Lambda so you don’t store keys in code.
   - Or set environment variables locally (for testing):
   - ```
     export AWS_ACCESS_KEY_ID=<YourAccessKey>
     export AWS_SECRET_ACCESS_KEY=<YourSecretKey>
     export AWS_DEFAULT_REGION=us-east-1
     ```

3. **Build the Java Projects**
   - ```
     cd Project2
     mvn clean package
     ```

4. Deploy the Lambdas
   - For video-splitting and face-recognition (Project 3), either:
     - Upload the JARs to AWS Lambda via console, _or_
     - Build Docker images with their respective Dockerfile and push to ECR, then create Lambdas from those images.


---

 </br>
## Usage
1. Upload a Video (PaaS Example)
   - Upload test_00.mp4 to <ASU_ID>-input.
   - Lambda (video-splitting) extracts one frame, saves as test_00.jpg in <ASU_ID>-stage-1.
   - Lambda (face-recognition) runs face detection, saves the recognized name in test_00.txt in <ASU_ID>-output.
2. Web Tier (IaaS Example)
   - Run:
      ```
      java -jar target/Project2-0.0.1-SNAPSHOT.jar
      ```
   - Upload: POST / with form-data:
      ```
      inputFile=<image_or_video_file>
      ```
   - The API sends a message to SQS, triggers the app tier, and eventually returns the classification result.


---

 </br>
## Repository Structure
```
aws-face-recognition/
├── Project1/            # Basic AWS resource management (EC2, S3, SQS)
├── Project2/            # IaaS-based face recognition with auto-scaling
│   ├── src/
│   ├── pom.xml
│   └── ...
├── Project3/            # PaaS-based (Lambda) video splitting & face recognition
│   ├── video-splitting/
│   ├── face-recognition/
│   └── ...
├── .gitignore
├── README.md            # You're here!
└── Other scripts/data as needed
```


---
 </br>

## Security Considerations
- Remove Hardcoded Credentials: Do not store AWS_ACCESS_KEY_ID or AWS_SECRET_ACCESS_KEY in Java/Python files.
- Use IAM Roles: Prefer instance profiles for EC2, and execution roles for Lambda.
- .gitignore: Make sure you aren’t committing .pem files, .log files with private data, or large training data sets.


---
 </br>

## Possible Improvements
- Add CI/CD (GitHub Actions or Jenkins) for automatic builds and deployments.
- More Granular IAM Policies to adhere to the principle of least privilege.
- Performance Tuning for your Lambda containers (memory, concurrency).
- CloudWatch Alarms & Metrics for deeper monitoring of queue length, CPU, memory usage, etc.


---
 </br>

## License
This project is released under the `MIT License`. That means you’re free to use, modify, and distribute the code, but you do so at your own risk.


---
 </br>

## Contact
Author: Varshith Dupati </br>
GitHub: @dvarshith </br>
Issues: Please open an issue on this repo if you have questions or find bugs. </br>
