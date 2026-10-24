# Aufgabe 1
Develop a Web Service, providing the REST API to access facebook interface(Friendship Graph).

Registry provide the service address for client to access;(like nacos)


# Terminology
1. group represent a namespace.Like i am the 6th group, then what service(like PATH Web Service in aufgabe 1) i have developed already remains in the namespace. 
2. Service, the web service i developed in this semster.
3. key: in the specific service, the information i store
4. value: the value of the key.

eg:
gruppe6 Group
+ path Service
    + [key]address - [value]http://localhost:8080/path
    + number-of-ids - 5000
    + number-of-calls - 120

if you wanna look up what keys the service(in gruppe6) has:
+ curl http://i4mw.informatik.uni-erlangen.de:18080/registry/gruppe6
return:["address"]%   
if you wanna look up what the value to the "address" key:
+  curl http://i4mw.informatik.uni-erlangen.de:18080/registry/gruppe6/address
return:http://i4mw.informatik.uni-erlangen.de:18081/facebook%    


# Directory explaination
Java + Gradle project
middleware-mycode/                  
│
├── doc/
│   └── aufgabe1.md                
│
├── gradle/                         ← Gradle build tools 
│   ├── wrapper/                    ← Gradle Wrapper
│   └── libs.versions.toml          ← dependency management
│
├── lib/
│   └── mwcc-zab-1.0-all.jar        ← provided java lib
│
├── misc/
│   └── aufgabe1/
│       ├── facebook-api.readme     ← Facebook REST API doc
│       ├── registry-api.readme     ← Registry REST API doc
│       └── registry.address        ← Registry server address
│
├── src/                            ← java code
│   └── aufgabe1/
│       └── mw/
│           ├── client/
│           │   ├── MWRegistryClient.java
│           │   ├── MWShell.java
│           │   └── MWWebServiceException.java
│           │
│           └── path/
│               └── MWDijkstra.java
│
├── build.gradle.kts                ← Gradle building rules
├── gradle.properties               ← Gradle project property
└── gradlew                         ← the gradle script


# Aufgabe 1.1
the existing service:(provided by professor)
+ Registry Service
+ FACEBOOK Service

1.1.1 assignment[Register Client]:by REST API look for Registry to find FB Service address;
1.1.2 assignment[Facebook Service Client]:make use of the address to get the data from FB Service

## Impl example: listGroups()
1. create HTTP Client
2. read the registry.address and request the Registry Address to get the groups
3. response get all the groups(JSON) inside,but we need java string[]


## access third party web - service (FB Service)
1. according to Registry find the address of service name
    just like last assignment, access registry to get the key(address) corresponding value
2. access the address by http client to get the res

```java
/facebook/search?string=<string>

    Response response = this.client.path("search").queryParam("string", string).request().get();
```

# Aufgabe 1.2
create a Path Service as web-service: use the facebook friendship to calculate the shortest path between 2 users;

1.2.1 realize the shortest PATH-SERVICE
1.2.2 extend web-service client[MWWebServiceClient.java] to give shell new command line : path <startID> <endID>
1.2.3 optimize PATH-SERVICE by bunch of Reqs

## 1.2.1 create our own web service - PATH SERVICE
offer:
+ GET /path/{startID}/{endID}
return:
public class MWPath {
    String[] path;
    int numberOfIDs;
    int numberOfCalls;
}