FROM maven:3-openjdk-8 as mvn
COPY . /tmp/redit
WORKDIR /tmp/redit
RUN mvn -P complete clean install

FROM eclipse-temurin:8-alpine

ARG USER=redit
ARG USER_ID=1005
ARG USER_GROUP=redit
ARG USER_GROUP_ID=1005
ARG USER_HOME=/home/${USER}

ENV APP=redit-0.1-SNAPSHOT-complete
ENV FOLDER=/tmp/target
# create a user group and a user
RUN  addgroup -g ${USER_GROUP_ID} ${USER_GROUP}; \
    adduser -u ${USER_ID} -D -g '' -h ${USER_HOME} -G ${USER_GROUP} ${USER} ;

WORKDIR ${USER_HOME}
COPY --chown=redit:redit --from=mvn /tmp/redit/target/${APP}.jar ${USER_HOME}
COPY --chown=redit:redit --from=mvn /tmp/redit/src/main/resources ${USER_HOME}
USER ${USER_ID}
ENTRYPOINT ["sh", "-c", "java -Xmx8G -cp ${APP}.jar eu.fbk.dh.tint.runner.TintServer -c tint.properties -p 8015"]
