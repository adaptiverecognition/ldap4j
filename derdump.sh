#!/bin/bash
java \
  -cp $(find ./ldap4j-java/target/*.jar|tr '\n' :):$(find ./ldap4j-java/target/dependency/*.jar|tr '\n' :) \
  hu.gds.ldap4j.ldap.DERDump \
  "$@"
