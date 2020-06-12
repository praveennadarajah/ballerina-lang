import ballerina/test;
import ballerina/http;
import ballerina/email;

// Mock object definition
public type MockHttpClient client object {
  public string url = "http://mockUrl";

  public remote function get(@untainted string path, public http:RequestMessage message = ()) returns http:Response|http:ClientError {
      http:Response res = new;
      res.statusCode = 500;
      return res;
  }

};

@test:Config {}
function testUserDefinedMockObject() {

  clientEndpoint  = <http:Client>test:mock(http:Client, new MockHttpClient());
  http:Response res = doGet();
  test:assertEquals(res.statusCode, 500);
  test:assertEquals(getClientUrl(), "http://mockUrl");
}

@test:Config {}
function testProvideAReturnValue() {

  http:Client mockHttpClient = <http:Client>test:mock(http:Client);
  http:Response mockResponse = new;
  mockResponse.statusCode = 500;

  test:prepare(mockHttpClient).when("get").thenReturn(mockResponse);
  clientEndpoint = mockHttpClient;
  http:Response res = doGet();
  test:assertEquals(res.statusCode, 500);
}

@test:Config {}
function testProvideAReturnValueBasedOnInput() {

  http:Client mockHttpClient = <http:Client>test:mock(http:Client);
  test:prepare(mockHttpClient).when("get").withArguments("/get?test=123", test:ANY).thenReturn(new http:Response());
  clientEndpoint = mockHttpClient;
  http:Response res = doGet();
  test:assertEquals(res.statusCode, 200);
}

@test:Config {}
function testProvideErrorAsReturnValue() {

  email:SmtpClient mockSmtpClient = <email:SmtpClient>test:mock(email:SmtpClient);
  smtpClient = mockSmtpClient;

  string[] emailIds = ["user1@test.com", "user2@test.com"];
  error? errMock = error("EMAIL_ERROR", message = "email sending failed");
  test:prepare(mockSmtpClient).when("send").thenReturn(errMock);
  error? err = sendNotification(emailIds);
  test:assertTrue(err is error);
}

@test:Config {}
function testDoNothing() {

  email:SmtpClient mockSmtpClient = <email:SmtpClient>test:mock(email:SmtpClient);
  http:Response mockResponse = new;
  mockResponse.statusCode = 500;

  test:prepare(mockSmtpClient).when("send").doNothing();
  smtpClient = mockSmtpClient;

  string[] emailIds = ["user1@test.com", "user2@test.com"];
  error? err = sendNotification(emailIds);
  test:assertEquals(err, ());
}

@test:Config {}
function testMockMemberVarible() {
  string mockClientUrl = "http://foo";
  http:Client mockHttpClient = <http:Client>test:mock(http:Client);
  test:prepare(mockHttpClient).getMember("url").thenReturn(mockClientUrl);

  clientEndpoint = mockHttpClient;
  test:assertEquals(getClientUrl(), mockClientUrl);
}

@test:Config {}
function testProvideAReturnSequence() {
    http:Client mockHttpClient = <http:Client>test:mock(http:Client);
    http:Response mockResponse = new;
    mockResponse.statusCode = 500;

    test:prepare(mockHttpClient).when("get").thenReturnSequence(new http:Response(), mockResponse);
    clientEndpoint = mockHttpClient;
    http:Response res = doGetRepeat();
    test:assertEquals(res.statusCode, 500);
}

# VALIDATION CASES
# 1 - Validations for user defined mock object

public type MockSmtpClientEmpty client object {};

public type MockSmtpClient client object {
  public remote function send(email:Email email) returns email:Error?  {
     // do nothing
  }
};

public type MockSmtpClientFuncErr client object {
  public remote function sendMail(email:Email email) returns email:Error?  {
      // do nothing
  }
};

public type MockSmtpClientSigErr client object {
  public remote function send(email:Email email) returns string {
    return "";
  }
};

// 1.1) when the user-defined mock object is empty
@test:Config {}
function testEmptyUserDefinedObj() {
  email:SmtpClient mockSmtpClient = <email:SmtpClient>test:mock(email:SmtpClient, new MockSmtpClientEmpty());
  smtpClient = mockSmtpClient;
}


// 1.2) when user-defined object is passed to test:prepare function
@test:Config {}
function testUserDefinedMockRegisterCases() {
  email:SmtpClient mockSmtpClient = <email:SmtpClient>test:mock(email:SmtpClient, new MockSmtpClient()); 
  test:prepare(mockSmtpClient).when("send").doNothing();
}

// 1.3) when the functions in mock is not available in the original
@test:Config {}
function testUserDefinedMockInvalidFunction() {
  email:SmtpClient mockSmtpClient = <email:SmtpClient>test:mock(email:SmtpClient, new MockSmtpClientFuncErr());
  smtpClient = mockSmtpClient;
  error? sendNotificationResult = sendNotification(["user1@test.com"]);
}

// 1.4) when the function signatures do not match
@test:Config {}
function testUserDefinedMockFunctionSignatureMismatch() {
  email:SmtpClient mockSmtpClient = <email:SmtpClient>test:mock(email:SmtpClient, new MockSmtpClientSigErr());
  smtpClient = mockSmtpClient;
  error? sendNotificationResult = sendNotification(["user1@test.com"]);
}

# 2 - Validations for framework provided default mock object

// 2.1  when the function called in mock is not available in the original
@test:Config {}
function testDefaultMockInvalidFunctionName() {
  email:SmtpClient mockSmtpClient = <email:SmtpClient>test:mock(email:SmtpClient); 
  test:prepare(mockSmtpClient).when("get").doNothing();
}

// 2.2) call doNothing() - the function has a return type specified
@test:Config {}
function testDefaultMockWrongAction() {
  http:Client mockHttpClient = <http:Client>test:mock(http:Client);
  test:prepare(mockHttpClient).when("get").doNothing();
}

// 2.3) when the object does not have a member variable of specified name
@test:Config {}
function testDefaultMockInvalidFieldName() {
  string mockClientUrl = "http://foo";
  http:Client mockHttpClient = <http:Client>test:mock(http:Client);
  test:prepare(mockHttpClient).getMember("clientUrl").thenReturn(mockClientUrl);

  clientEndpoint = mockHttpClient;
  test:assertEquals(getClientUrl(), mockClientUrl);
}
