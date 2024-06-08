#!/bin/bash
java \
  -cp $(find ./ldap4j-java/target/*.jar|tr '\n' :):$(find ./ldap4j-slf4j-shim/target/*.jar|tr '\n' :):$(find ./ldap4j-java/target/dependency/*.jar|tr '\n' :) \
  hu.gds.ldap4j.Ldap4jCommand \
  "$@"
