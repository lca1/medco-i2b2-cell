FROM jboss/wildfly:10.0.0.Final as build

# build-time variables
ENV MEDCO_CONF_DIR="/medco-configuration" \
    MEDCO_CELL_SRC_DIR="/medco-cell-src"

USER root
COPY deployment/i2b2-medco/install-medco-cell.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/install-medco-cell.sh && \
    mkdir -p "$MEDCO_CELL_SRC_DIR" && \
    chown -R jboss:jboss "$MEDCO_CELL_SRC_DIR"
RUN yum update -y && \
    yum -y install git ant && \
    yum clean all
USER jboss

COPY --chown=jboss:jboss ./ $MEDCO_CELL_SRC_DIR
RUN install-medco-cell.sh

# -------------------------------------------
FROM medco/i2b2:v1.7.10.0001 as release

# run-time variables
ENV NODE_IDX="0" \
    UNLYNX_DEBUG_LEVEL="1"

# build-time variables
ENV MEDCO_CONF_DIR="/medco-configuration"

COPY --from=build --chown=jboss:jboss "$JBOSS_HOME/standalone/configuration" "$JBOSS_HOME/standalone/configuration"
COPY --from=build --chown=jboss:jboss "$JBOSS_HOME/standalone/deployments" "$JBOSS_HOME/standalone/deployments"
COPY --chown=jboss:jboss deployment/i2b2-medco/sql/* "$I2B2_SQL_DIR/"
COPY --chown=jboss:jboss deployment/i2b2-medco/pre-init-scripts/* "$PRE_INIT_SCRIPT_DIR/"

VOLUME $MEDCO_CONF_DIR
