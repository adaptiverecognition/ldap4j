#!/bin/bash

set -e

rm -fr tempkeys
mkdir tempkeys
cd tempkeys

openssl req -new -subj "/CN=serverca" -keyout "LdapTest.serverca.key.pem" -passout "pass:servercapass" -out "LdapTest.serverca.csr.pem"
openssl x509 -signkey "LdapTest.serverca.key.pem" -passin "pass:servercapass" -req -days 3650 -in "LdapTest.serverca.csr.pem" -out "LdapTest.serverca.cer.pem" -extensions v3_ca
rm "LdapTest.serverca.csr.pem"
echo 1000 > serial.txt
for ii in 0 1
do
    openssl req -new -config "../LdapTest.${ii}.openssl.conf" -keyout "LdapTest.${ii}.server.key.pem" -passout "pass:serverpass" -out "LdapTest.${ii}.server.csr.pem"
    openssl x509 -CA "LdapTest.serverca.cer.pem" -CAkey "LdapTest.serverca.key.pem" -passin "pass:servercapass" -CAserial serial.txt -req -in "LdapTest.${ii}.server.csr.pem" \
            -out "LdapTest.${ii}.server.cer.pem" -days 3650 -extensions "req_exts" -extfile "../LdapTest.${ii}.openssl.conf"
    rm "LdapTest.${ii}.server.csr.pem"
    openssl pkcs12 -export -out "LdapTest.${ii}.server.p12" -passout "pass:serverpass" -inkey "LdapTest.${ii}.server.key.pem" -passin "pass:serverpass" -in "LdapTest.${ii}.server.cer.pem"
    rm "LdapTest.${ii}.server.key.pem"
done
rm serial.txt
rm "LdapTest.serverca.key.pem"

chmod 644 *

cd ..
