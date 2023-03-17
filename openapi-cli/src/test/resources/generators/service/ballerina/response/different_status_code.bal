import ballerina/http;

listener http:Listener ep0 = new (80, config = {host: "petstore.openapi.io"});

service /v1 on ep0 {
    # Creates a new user.
    #
    # + return - Email successfully sent
    resource function post user() returns http:NoContent {
    }
}
