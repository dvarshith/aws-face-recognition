# Use Ubuntu 20.04 as the base image
FROM ubuntu:20.04

# Set environment variables
ENV LANG=C.UTF-8 \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
    TORCH_HOME=/tmp

# Install Java, Python, and other dependencies
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
    openjdk-17-jdk \
    python3 \
    python3-pip \
    libgl1-mesa-glx \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    wget \
    unzip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install AWS Lambda Java Runtime Interface Client
RUN mkdir -p /opt
RUN wget -O /opt/aws-lambda-java-runtime-interface-client.jar \
    https://repo1.maven.org/maven2/com/amazonaws/aws-lambda-java-runtime-interface-client/2.6.0/aws-lambda-java-runtime-interface-client-2.6.0.jar

# Copy requirements.txt into the container
COPY requirements.txt /tmp/requirements.txt

# Install Python dependencies from requirements.txt
RUN pip3 install --upgrade pip
RUN pip3 install -r /tmp/requirements.txt

# Set the working directory
WORKDIR /var/task

# Copy the fat JAR and resources into the container
COPY target/face-recognition-1.0-SNAPSHOT.jar /var/task/
COPY src/main/resources/face-recognition-code.py /var/task/

# Set the entry point and command for Lambda
ENTRYPOINT [ "java", \
    "-cp", "/var/task/*:/opt/*", \
    "com.amazonaws.services.lambda.runtime.api.client.AWSLambda" ]

CMD [ "com.cse546.project32.FaceRecognitionHandler::handleRequest" ]