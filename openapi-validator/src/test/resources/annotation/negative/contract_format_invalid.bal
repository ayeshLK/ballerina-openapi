import ballerina/openapi;
import ballerina/http;

@openapi:ServiceInfo{
    contract: "abc.txt"
}
service /v4 on new http:Listener(9090) {
    resource function post pet(@http:Payload json payload, int id) returns http:Accepted {
        return <http:Accepted> {};
    }
}