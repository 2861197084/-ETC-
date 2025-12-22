#!/usr/bin/env bash

export HBASE_MANAGES_ZK=false

# Limit JVM heap to avoid the RegionServer being killed under Docker memory pressure.
# Adjust in Docker Desktop if you want more headroom.
export HBASE_HEAPSIZE=${HBASE_HEAPSIZE:-512}
