## Build ldap4j

Set up Java and Maven.

Pull the repo.
```console
    git clone https://github.com/adaptiverecognition/ldap4j
    cd ldap4j
```

Choose a version. 
```console
    git checkout master
```

Build and install ldap4j.
```console
    mvn -Pdevelopment clean
    mvn -Pdevelopment install
```


## Test ldap4j

Set up openssl.
Create test keys and certificates.
```console
    ./create-keys.sh
```

Set up docker.

Run the tests.
```console
    mvn -Pdevelopment,test test -pl ldap4j-tests-slf4j 
    mvn -Pdevelopment,test test -pl ldap4j-tests 
    mvn -Pdevelopment,test test -pl ldap4j-tests-docker 
```
