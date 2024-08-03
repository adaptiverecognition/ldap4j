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

Create test keys and certificates.
```console
    ./create-keys.sh
```

Run the tests.
```console
    mvn -Ptest test -pl ldap4j-tests-slf4j 
    mvn -Ptest test -pl ldap4j-tests 
```