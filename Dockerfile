# 기본 이미지 설정
FROM --platform=linux/amd64 openjdk:21-jdk-slim

# AWS 환경에 크롬을 깔지 않으면, 크롤링 기능을 전혀 수행할 수 없다. -> 따라서, 아래 부분은 반드시 있어야 한다.
# Google Chrome 설치
#RUN apt-get update -y \
#    && apt-get install -y wget gnupg2 \
#    && wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
#    && apt install -y ./google-chrome-stable_current_amd64.deb \
#    && rm -f google-chrome-stable_current_amd64.deb \
#    && apt-get clean \
#    && rm -rf /var/lib/apt/lists/*

# 애플리케이션 JAR 파일 복사
ARG JAR_FILE=build/libs/JiJiJig-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

# 애플리케이션 포트 노출
EXPOSE 8080

# 엔트리 포인트
ENTRYPOINT ["java","-jar","/app.jar"]