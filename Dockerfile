FROM eclipse-temurin:17-jre-alpine
MAINTAINER nabarun.mondal@gmail.com
COPY app/build/libs /home/cowj
ENTRYPOINT ["java", "--add-opens", "java.base/jdk.internal.loader=ALL-UNNAMED" , "-jar" ,"/home/cowj/cowj-0.1-SNAPSHOT.jar" ]
