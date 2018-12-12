#!/usr/bin/env bash
set -Eeuo pipefail

# compile and deploy
pushd "$MEDCO_CELL_SRC_DIR"
sed -i "/jboss.home/c\jboss.home=$JBOSS_HOME" build.properties
sed -i "/medco.unlynx.groupfilepath/c\medco.unlynx.groupfilepath=$MEDCO_CONF_DIR/group.toml" etc/spring/medcoapp/medco.properties
sed -i "/medco.unlynx.binarypath/c\medco.unlynx.binarypath=$MEDCO_CONF_DIR/unlynxMedCo" etc/spring/medcoapp/medco.properties
ant -f build.xml clean all deploy
popd
