#!/bin/bash
# Build the compiler without GWT.
mvn -DskipTests -pl externs/pom.xml,pom-main.xml,pom-main-shaded.xml
